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
import android.net.nsd.NsdManager.RegistrationListener;
import android.net.nsd.NsdManager.ResolveListener;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.RequiresApi;

import org.json.JSONException;
import org.kde.kdeconnect.Backends.BaseLink;
import org.kde.kdeconnect.Backends.BaseLinkProvider;
import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Helpers.DeviceHelper;
import org.kde.kdeconnect.Helpers.NetworkHelper;
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper;
import org.kde.kdeconnect.NetworkPacket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.HashMap;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;

/**
 * This BaseLinkProvider creates {@link MulticastLink}s to other devices on the same
 * WiFi network. The first packet sent over a socket must be an
 * {@link NetworkPacket#createIdentityPacket(Context)}.
 *
 * @see #identityPacketReceived(NetworkPacket, Socket, MulticastLink.ConnectionStarted)
 */
public class MulticastLinkProvider extends BaseLinkProvider implements MulticastLink.LinkDisconnectedCallback {

    static final String LOG_TAG = "MulticastLink";

    static final String SERVICE_TYPE = "_kdeconnect._tcp";

    private NsdManager mNsdManager;
    private RegistrationListener mRegistrationListener;
    private ResolveListener mResolveListener;
    private boolean mServiceRegistered = false;

    private final static int MIN_PORT = 1716;
    private final static int MAX_PORT = 1764;
    final static int PAYLOAD_TRANSFER_MIN_PORT = 1739;

    private final Context context;

    private final HashMap<String, MulticastLink> visibleComputers = new HashMap<>();  //Links by device id

    private ServerSocket tcpServer;

    private boolean listening = false;

    // To prevent infinte loop between Android < IceCream because both device can only broadcast identity package but cannot connect via TCP
    private final ArrayList<InetAddress> reverseConnectionBlackList = new ArrayList<>();

    @Override // SocketClosedCallback
    public void linkDisconnected(MulticastLink brokenLink) {
        String deviceId = brokenLink.getDeviceId();
        visibleComputers.remove(deviceId);
        connectionLost(brokenLink);
    }

    //They received my UDP broadcast and are connecting to me. The first thing they send should be their identity.
    private void tcpPacketReceived(Socket socket) {

        NetworkPacket networkPacket;
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String message = reader.readLine();
            networkPacket = NetworkPacket.unserialize(message);
            //Log.e("TcpListener","Received TCP package: "+networkPacket.serialize());
        } catch (Exception e) {
            Log.e(LOG_TAG, "Exception while receiving TCP packet", e);
            return;
        }

        if (!networkPacket.getType().equals(NetworkPacket.PACKET_TYPE_IDENTITY)) {
            Log.e(LOG_TAG, "Expecting an identity package instead of " + networkPacket.getType());
            return;
        }

        Log.i(LOG_TAG, "Identity package received from a TCP connection from " + networkPacket.getString("deviceName"));
        identityPacketReceived(networkPacket, socket, MulticastLink.ConnectionStarted.Locally);
    }

    private void configureSocket(Socket socket) {
        try {
            socket.setKeepAlive(true);
        } catch (SocketException e) {
            Log.e(LOG_TAG, "Exception", e);
        }
    }

    /**
     * Called when a new 'identity' packet is received. Those are passed here by
     * {@link #tcpPacketReceived(Socket)} and {@link #udpPacketReceived(DatagramPacket)}.
     * <p>
     * If the remote device should be connected, this calls {@link #addLink}.
     * Otherwise, if there was an Exception, we unpair from that device.
     * </p>
     *
     * @param identityPacket    identity of a remote device
     * @param socket            a new Socket, which should be used to receive packets from the remote device
     * @param connectionStarted which side started this connection
     */
    private void identityPacketReceived(final NetworkPacket identityPacket, final Socket socket, final MulticastLink.ConnectionStarted connectionStarted) {

        String myId = DeviceHelper.getDeviceId(context);
        final String deviceId = identityPacket.getString("deviceId");
        if (deviceId.equals(myId)) {
            Log.e(LOG_TAG, "Somehow I'm connected to myself, ignoring. This should not happen.");
            return;
        }

        // If I'm the TCP server I will be the SSL client and viceversa.
        final boolean clientMode = (connectionStarted == MulticastLink.ConnectionStarted.Locally);

        // Do the SSL handshake
        try {
            SharedPreferences preferences = context.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE);
            boolean isDeviceTrusted = preferences.getBoolean(deviceId, false);

            if (isDeviceTrusted && !SslHelper.isCertificateStored(context, deviceId)) {
                //Device paired with and old version, we can't use it as we lack the certificate
                BackgroundService.RunCommand(context, service -> {
                    Device device = service.getDevice(deviceId);
                    if (device == null) return;
                    device.unpair();
                    //Retry as unpaired
                    identityPacketReceived(identityPacket, socket, connectionStarted);
                });
            }

            Log.i(LOG_TAG, "Starting SSL handshake with " + identityPacket.getString("deviceName") + " trusted:" + isDeviceTrusted);

            final SSLSocket sslsocket = SslHelper.convertToSslSocket(context, socket, deviceId, isDeviceTrusted, clientMode);
            sslsocket.addHandshakeCompletedListener(event -> {
                String mode = clientMode ? "client" : "server";
                try {
                    Certificate certificate = event.getPeerCertificates()[0];
                    identityPacket.set("certificate", Base64.encodeToString(certificate.getEncoded(), 0));
                    Log.i(LOG_TAG, "Handshake as " + mode + " successful with " + identityPacket.getString("deviceName") + " secured with " + event.getCipherSuite());
                    addLink(identityPacket, sslsocket, connectionStarted);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Handshake as " + mode + " failed with " + identityPacket.getString("deviceName"), e);
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
                    Log.e(LOG_TAG, "Handshake failed with " + identityPacket.getString("deviceName"), e);

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
     * {@link Device#addLink(NetworkPacket, BaseLink)} crashes on some devices running Oreo 8.1 (SDK level 27).
     * </p>
     *
     * @param identityPacket   representation of remote device
     * @param socket           a new Socket, which should be used to receive packets from the remote device
     * @param connectionOrigin which side started this connection
     * @throws IOException if an exception is thrown by {@link MulticastLink#reset(SSLSocket, MulticastLink.ConnectionStarted)}
     */
    private void addLink(final NetworkPacket identityPacket, SSLSocket socket, MulticastLink.ConnectionStarted connectionOrigin) throws IOException {

        String deviceId = identityPacket.getString("deviceId");
        MulticastLink currentLink = visibleComputers.get(deviceId);
        if (currentLink != null) {
            //Update old link
            Log.i(LOG_TAG, "Reusing same link for device " + deviceId);
            final Socket oldSocket = currentLink.reset(socket, connectionOrigin);
            //Log.e(LOG_TAG, "Replacing socket. old: "+ oldSocket.hashCode() + " - new: "+ socket.hashCode());
        } else {
            Log.i(LOG_TAG, "Creating a new link for device " + deviceId);
            //Let's create the link
            MulticastLink link = new MulticastLink(context, deviceId, this, socket, connectionOrigin);
            visibleComputers.put(deviceId, link);
            connectionAccepted(identityPacket, link);
        }
    }

    public MulticastLinkProvider(Context context) {
        this.context = context;
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
                    tcpPacketReceived(socket);
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

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void initializeRegistrationListener() {
        mRegistrationListener = new NsdManager.RegistrationListener() {

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
            public void onServiceUnregistered(NsdServiceInfo arg0) {
                // Service has been unregistered. This only happens when you call
                // NsdManager.unregisterService() and pass in this listener.
                Log.w(LOG_TAG, "Service unregistered: " + arg0);
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                // Unregistration failed. Put debugging code here to determine why.
                Log.e(LOG_TAG, "Unregister of " + serviceInfo + " failed with: " + errorCode);
            }
        };
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    public void initializeNsdManager(RegistrationListener registrationListener) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        mNsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        try {
            mNsdManager.unregisterService(registrationListener);
        } catch (java.lang.IllegalArgumentException e) {
            // not yet registered, but it's fine.
        }
        NsdServiceInfo serviceInfo = new NsdServiceInfo();

        // The name is subject to change based on conflicts
        // with other services advertised on the same network.
        NetworkPacket myIdentity = NetworkPacket.createIdentityPacket(context);
        String did = myIdentity.getString("deviceID");
        String name = myIdentity.getString("deviceName");
        InetAddress addr = this.tcpServer.getInetAddress();
        int port = this.tcpServer.getLocalPort();

        // These cause the requirement for api level 21.
        serviceInfo.setAttribute("name", myIdentity.getString("deviceName"));
        serviceInfo.setAttribute("id", myIdentity.getString("deviceId"));
        serviceInfo.setAttribute("type", myIdentity.getString("deviceType"));
        serviceInfo.setAttribute("version", myIdentity.getString("protocolVersion"));

        // It might be nice to add the capabilities in the mDNS advertisement too, but without
        // some kind of encoding that is too large for the TXT record

        serviceInfo.setServiceName("KDE Connect on " + myIdentity.getString("deviceName"));
        serviceInfo.setServiceType(SERVICE_TYPE);
        serviceInfo.setHost(addr);
        serviceInfo.setPort(port);

        //Log.d("KDE/Lan", "service: " + serviceInfo.toString());

        mNsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    public ResolveListener initializeResolveListener() {
        return new ResolveListener() {

            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int i) {
                Log.e(LOG_TAG, "Could not resolve service: " + serviceInfo);
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                Log.i(LOG_TAG, "Successfully resolved " + serviceInfo);

                InetAddress hostname = serviceInfo.getHost();
                int remotePort = serviceInfo.getPort();

                SocketFactory socketFactory = SocketFactory.getDefault();
                Socket socket;
                try {
                    socket = socketFactory.createSocket(hostname, remotePort);

                    OutputStream out = socket.getOutputStream();
                    NetworkPacket myIdentity = NetworkPacket.createIdentityPacket(context);
                    out.write(myIdentity.serialize().getBytes());
                    out.flush();
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Unable to make the socket connection", e);
                    return;
                } catch (JSONException e) {
                    Log.e(LOG_TAG, "Unable to deserialize myIdentity", e);
                    return;
                }
            }
        };
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    public void initializeDiscoveryListener() {
        // Instantiate a new DiscoveryListener
        NsdManager.DiscoveryListener discoveryListener = new NsdManager.DiscoveryListener() {

            // Called as soon as service discovery begins.
            @Override
            public void onDiscoveryStarted(String regType) {
                Log.d(LOG_TAG, "Service discovery started");
            }

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                // A service was found! Do something with it.
                Log.d(LOG_TAG, "Service discovery success" + service);
                mNsdManager.resolveService(service, mResolveListener);
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
                // When the network service is no longer available.
                // Internal bookkeeping code goes here.
                Log.e(LOG_TAG, "service lost: " + service);
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.i(LOG_TAG, "Discovery stopped: " + serviceType);
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

        mNsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
    }

    @Override
    public void onStart() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Log.i(LOG_TAG, "MulticastBackend not supported on devices older thab Lollipop");
            return;
        }

        if (!listening) {
            listening = true;

            // Need to set up TCP before setting up mDNS because we need to know the TCP listening
            // address and port
            setupTcpListener();

            initializeRegistrationListener();
            mResolveListener = initializeResolveListener();

            initializeNsdManager(mRegistrationListener);

            initializeDiscoveryListener();
        }
    }

    @Override
    public void onNetworkChange() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // This backend does not work on older Android versions
            return;
        }
        if (listening) {
            mNsdManager.unregisterService(mRegistrationListener);
            listening = false;
        }

        if (NetworkHelper.isOnMobileNetwork(context)) {
            Log.i("LanLinkProvider", "On 3G network, disabling mDNS advertisements.");
        } else {
            this.initializeRegistrationListener();
        }
    }

    @Override
    public void onStop() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // This backend does not work on older Android versions
            return;
        }

        listening = false;
        mNsdManager.unregisterService(mRegistrationListener);
    }

    @Override
    public String getName() {
        return "MulticastLinkProvider";
    }

}
