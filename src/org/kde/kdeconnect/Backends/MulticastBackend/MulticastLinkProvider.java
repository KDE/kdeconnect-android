/*
 * Copyright 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 * Copyright 2018 Teemu Rytilahti
 * Copyright 2019 Simon Redman <simon@ergotech.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License or (at your option) version 3 or any later version
 * accepted by the membership of KDE e.V. (or its successor approved
 * by the membership of KDE e.V.), which shall act as a proxy
 * defined in Section 14 of version 3 of the license.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kde.kdeconnect.Backends.MulticastBackend;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdManager.ResolveListener;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import org.kde.kdeconnect.Backends.DeviceLink;
import org.kde.kdeconnect.Backends.BaseLinkProvider;
import org.kde.kdeconnect.Backends.DeviceOffer;
import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Helpers.DeviceHelper;
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper;
import org.kde.kdeconnect.NetworkPacket;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.util.HashMap;

import javax.net.ssl.SSLSocket;

/**
 * This BaseLinkProvider creates {@link MulticastLink}s to other devices on the same
 * WiFi network. The first packet sent over a socket must be an
 * {@link NetworkPacket#createIdentityPacket(Context)}.
 *
 * @see #identityPacketReceived(NetworkPacket, Socket, MulticastLink.ConnectionStarted)
 */
public class MulticastLinkProvider extends BaseLinkProvider implements MulticastLink.LinkDisconnectedCallback {

    HashMap<InetAddress, DeviceOffer> offers = new HashMap<>();

    static final String LOG_TAG = "MulticastLink";

    static final String SERVICE_TYPE = "_kdeconnect._tcp";

    private NsdManager mNsdManager;

    private final static int MIN_PORT = 1716;
    private final static int MAX_PORT = 1764;
    final static int PAYLOAD_TRANSFER_MIN_PORT = 1739;

    private final Context context;

    private final HashMap<String, MulticastLink> visibleComputers = new HashMap<>();  //Links by device id

    private ServerSocket tcpServer;

    private boolean listening = false;

    @Override // SocketClosedCallback
    public void linkDisconnected(MulticastLink brokenLink) {
        String deviceId = brokenLink.getDeviceId();
        visibleComputers.remove(deviceId);
        onLinkDisconnected(brokenLink);
    }

    private InetAddress getDeviceIpAddress() {
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiManager.MulticastLock lock = wifi.createMulticastLock("jmdns-multicast-lock");
        lock.setReferenceCounted(true);
        lock.acquire();
        InetAddress result = null;
        try {
            // figure out our wifi address, otherwise bail
            WifiInfo wifiinfo = wifi.getConnectionInfo();
            int intaddr = wifiinfo.getIpAddress();
            byte[] byteaddr = new byte[] { (byte) (intaddr & 0xff), (byte) (intaddr >> 8 & 0xff),
                    (byte) (intaddr >> 16 & 0xff), (byte) (intaddr >> 24 & 0xff) };
            return InetAddress.getByAddress(byteaddr);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return null;
        }
    }
    private void configureSocket(Socket socket) {
        try {
            socket.setKeepAlive(true);
        } catch (SocketException e) {
            Log.e(LOG_TAG, "Exception", e);
        }
    }


    /**
     * Called when a socket is connected.
     *
     * @param socket            a new Socket, which should be used to receive packets from the remote device
     * @param connectionStarted which side started this connection
     */
    private void doTheSslDance(DeviceOffer deviceOffer, final Socket socket, final MulticastLink.ConnectionStarted connectionStarted) {

        String deviceId = deviceOffer.id;

        // If I'm the TCP server I will be the SSL client and viceversa.
        final boolean clientMode = (connectionStarted == MulticastLink.ConnectionStarted.Locally);

        try {
            SharedPreferences preferences = context.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE);
            boolean isDeviceTrusted = preferences.getBoolean(deviceId, false);

            Log.i(LOG_TAG, "Starting SSL handshake with " + deviceOffer.name + " trusted:" + isDeviceTrusted);

            final SSLSocket sslsocket = SslHelper.convertToSslSocket(context, socket, deviceId, isDeviceTrusted, clientMode);
            sslsocket.addHandshakeCompletedListener(event -> {
                try {
                    Certificate certificate = event.getPeerCertificates()[0];
                    createLink(deviceOffer, sslsocket);
                } catch (Exception e) {
                    String mode = clientMode ? "client" : "server";
                    Log.e(LOG_TAG, "Handshake as " + mode + " failed with " + deviceOffer.name, e);
                    BackgroundService.RunCommand(context, service -> {
                        Device device = service.getDevice(deviceId);
                        if (device == null) return;
                        device.unpair();
                    });
                }
            });
            //Handshake is blocking, so do it on another thread and free this thread to keep receiving new connection
            new Thread(() -> {
                try {
                    synchronized (this) {
                        sslsocket.startHandshake();
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Handshake failed with " + deviceOffer.name, e);

                    //String[] ciphers = sslsocket.getSupportedCipherSuites();
                    //for (String cipher : ciphers) {
                    //    Log.i("SupportedCiphers","cipher: " + cipher);
                    //}
                }
            }).start();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Exception", e);
        }

    }

    /**
     * Add or update a link in the {@link #visibleComputers} map. This method is synchronized, which ensures that only one
     * link is operated on at a time.
     * <p>
     * Without synchronization, the call to {@link SslHelper#parseCertificate(byte[])} in
     * {@link Device#addLink(NetworkPacket, DeviceLink)} crashes on some devices running Oreo 8.1 (SDK level 27).
     * </p>
     *
     * @param identityPacket   representation of remote device
     * @param socket           a new Socket, which should be used to receive packets from the remote device
     * @param connectionOrigin which side started this connection
     * @throws IOException if an exception is thrown by {@link MulticastLink#reset(SSLSocket, MulticastLink.ConnectionStarted)}
     */
    private void createLink(DeviceOffer offer, SSLSocket socket) throws IOException {
        String deviceId = offer.id;
        MulticastLink currentLink = visibleComputers.get(deviceId);
        if (currentLink != null) {
            //Update old link
            Log.i(LOG_TAG, "Reusing same link for device " + deviceId);
            final Socket oldSocket = currentLink.reset(socket);
            //Log.e(LOG_TAG, "Replacing socket. old: "+ oldSocket.hashCode() + " - new: "+ socket.hashCode());
        } else {
            Log.i(LOG_TAG, "Creating a new link for device " + deviceId);
            //Let's create the link
            MulticastLink link = new MulticastLink(context, deviceId, this, socket);
            visibleComputers.put(deviceId, link);
            onLinkConnected(offer, link);
        }
    }

    private NsdServiceInfo createServiceInfoForTcpServer(InetAddress address, int port) {
        NsdServiceInfo serviceInfo = new NsdServiceInfo();

        // The name is subject to change based on conflicts
        // with other services advertised on the same network.
        String name = DeviceHelper.getDeviceName(context);

        serviceInfo.setAttribute("name", name);
        serviceInfo.setAttribute("id", DeviceHelper.getDeviceId(context));
        serviceInfo.setAttribute("type", DeviceHelper.getDeviceType(context).toString());
        serviceInfo.setAttribute("version", Integer.toString(DeviceHelper.ProtocolVersion));
        serviceInfo.setAttribute("ip", address.toString());


        // It might be nice to add the capabilities in the mDNS advertisement too, but without
        // some kind of encoding that is too large for the TXT record

        serviceInfo.setServiceName("KDE Connect on " + name);
        serviceInfo.setServiceType(SERVICE_TYPE);
        serviceInfo.setPort(port);
        serviceInfo.setHost(address);

        return serviceInfo;
    }
    public MulticastLinkProvider(Context context) {
        this.context = context;
        mNsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
    }

    private void setupTcpListener() {
        try {
            tcpServer = openServerSocketOnFreePort(MIN_PORT);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error creating tcp server", e);
            return;
        }
        new Thread(() -> {
            while (listening) {
                try {
                    Socket socket = tcpServer.accept();
                    configureSocket(socket);
                    InetAddress remoteAddress = socket.getInetAddress();
                    DeviceOffer offer = offers.get(remoteAddress);
                    //if (offer == null) {
                    //    Log.e(LOG_TAG, "Received a connection from an unknown device "+ remoteAddress.toString() + ", ignoring!");
                    //    socket.close();
                    //    return;
                    //    //offer = (DeviceOffer)(offers.values().toArray()[0]);
                    //}
                    doTheSslDance(offer, socket, MulticastLink.ConnectionStarted.Remotely);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "TcpReceive exception", e);
                }
            }
            Log.w("TcpListener", "Stopping TCP listener");
        }).start();
    }

    static ServerSocket openServerSocketOnFreePort(int minPort) throws IOException {
        int tcpPort = minPort;
        while (tcpPort <= MAX_PORT) {
            try {
                ServerSocket candidateServer = new ServerSocket();
                candidateServer.bind(new InetSocketAddress(tcpPort));
                Log.i(LOG_TAG, "Using port " + tcpPort);
                return candidateServer;
            } catch (IOException e) {
                tcpPort++;
                if (tcpPort == MAX_PORT) {
                    Log.e(LOG_TAG, "No ports available");
                    throw e; //Propagate exception
                }
            }
        }
        throw new RuntimeException("This should not be reachable");
    }

    NsdManager.RegistrationListener mRegistrationListener = new NsdManager.RegistrationListener() {

        @Override
        public void onServiceRegistered(NsdServiceInfo NsdServiceInfo) {
            // Save the service name. Android may have changed it in order to
            // resolve a conflict, so update the name you initially requested
            // with the name Android actually used.
            Log.i(LOG_TAG, "Registered " + NsdServiceInfo.getServiceName());
        }

        @Override
        public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            // Registration failed! Put debugging code here to determine why.
            Log.e(LOG_TAG, "Registration failed");
        }

        @Override
        public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
            // Service has been unregistered. This only happens when you call
            // NsdManager.unregisterService() and pass in this listener.

            Log.e(LOG_TAG, "Service unregistered: " + serviceInfo);
            offers.remove(serviceInfo.getHost());
        }

        @Override
        public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            // Unregistration failed. Put debugging code here to determine why.
            Log.e(LOG_TAG, "Unregister of " + serviceInfo + " failed with: " + errorCode);
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    public ResolveListener createResolveListener() {
        return new ResolveListener() {

            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int i) {
                Log.e(LOG_TAG, "Could not resolve service: " + serviceInfo);
            }

            String getString(NsdServiceInfo serviceInfo, String key) {
                byte[] raw = serviceInfo.getAttributes().get(key);
                if (raw == null) {
                    return null;
                }
                return new String(raw, StandardCharsets.UTF_8);
            }
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                Log.e(LOG_TAG, "Successfully resolved " + serviceInfo);

                String id = getString(serviceInfo, "id");
                if (id == null) {
                    Log.e(LOG_TAG, "Id not found");
                    return;
                }
                if (id.equals(DeviceHelper.getDeviceId(context))) {
                    Log.e(LOG_TAG, "Ignoring myself " + serviceInfo);
                    return;
                }

                String name = getString(serviceInfo, "name");
                if (name == null) {
                    Log.e(LOG_TAG, "Name not found");
                    return;
                }

                String type_s = getString(serviceInfo, "type");
                if (type_s == null) {
                    Log.e(LOG_TAG, "Device type not found");
                    return;
                }
                Device.DeviceType type = Device.DeviceType.FromString(type_s);

                String version_s = getString(serviceInfo, "version");
                if (version_s == null) {
                    Log.e(LOG_TAG, "Protocol version not found");
                    return;
                }
                int protocolVersion = Integer.parseInt(version_s);

                InetAddress hostname = serviceInfo.getHost();
                int remotePort = serviceInfo.getPort();

                DeviceOffer offer = new DeviceOffer();
                offer.id = id;
                offer.name = name;
                offer.type = type;
                offer.protocolVersion = protocolVersion;
                offer.host = hostname;
                offer.port = remotePort;
                offer.provider = MulticastLinkProvider.this;

                offers.put(hostname, offer);
                onOfferAdded(offer);
            }
        };
    }


    NsdManager.DiscoveryListener discoveryListener = new NsdManager.DiscoveryListener() {

        // Called as soon as service discovery begins.
        @Override
        public void onDiscoveryStarted(String regType) {
            Log.e(LOG_TAG, "Service discovery started");
        }

        @Override
        public void onServiceFound(NsdServiceInfo service) {
            // A service was found! Do something with it.
            Log.e(LOG_TAG, "Service discovery success " + service);
            mNsdManager.resolveService(service, createResolveListener());
        }

        @Override
        public void onServiceLost(NsdServiceInfo service) {
            // When the network service is no longer available.
            // Internal bookkeeping code goes here.
            Log.e(LOG_TAG, "service lost: " + service);
        }

        @Override
        public void onDiscoveryStopped(String serviceType) {
            Log.e(LOG_TAG, "Discovery stopped: " + serviceType);
            listening = false;
        }

        @Override
        public void onStartDiscoveryFailed(String serviceType, int errorCode) {
            Log.e(LOG_TAG, "Discovery failed: Error code:" + errorCode);
            mNsdManager.stopServiceDiscovery(this);
        }

        @Override
        public void onStopDiscoveryFailed(String serviceType, int errorCode) {
            Log.e(LOG_TAG, "Discovery failed: Error code:" + errorCode);
            mNsdManager.stopServiceDiscovery(this);
        }
    };

    @Override
    public void refresh() {
        onStop();
        onStart();
    }

    public synchronized void onStart() {
        Log.e(LOG_TAG,"ON_START");
        if (!listening) {
            Log.e(LOG_TAG,"ON_START doing things");
            listening = true;
            // We set the tcp server port on the service info, so we need to create it beforehand
            setupTcpListener();
            NsdServiceInfo serviceInfo = createServiceInfoForTcpServer( getDeviceIpAddress(), this.tcpServer.getLocalPort());
            mNsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, mRegistrationListener);
            mNsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
        }
    }

    public synchronized void onStop() {
        /*Log.e(LOG_TAG,"ON_STOP");
        if (listening) {
            Log.e(LOG_TAG,"ON_STOP doing things");
            mNsdManager.stopServiceDiscovery(discoveryListener);
            mNsdManager.unregisterService(mRegistrationListener);
            try { tcpServer.close(); } catch (IOException ignore) { }
            listening = false;
        }*/
    }

    @Override
    public String getName() {
        return "MulticastLinkProvider";
    }

    @Override
    public void connect(DeviceOffer offer) {
        try {
            Socket socket = new Socket(offer.host,offer.port);
            doTheSslDance(offer, socket, MulticastLink.ConnectionStarted.Locally);
        } catch (IOException e) {
            onConnectionFailed(offer, e.getLocalizedMessage());
            e.printStackTrace();
        }
    }

    /*
    void trustDevice() {
        if (identityPacket.has("certificate")) {
        String certificateString = identityPacket.getString("certificate");

        try {
            byte[] certificateBytes = Base64.decode(certificateString, 0);
            certificate = SslHelper.parseCertificate(certificateBytes);
            Log.i("KDE/Device", "Got certificate ");
        } catch (Exception e) {
            Log.e("KDE/Device", "Error getting certificate", e);
        }
    }
    */
}
