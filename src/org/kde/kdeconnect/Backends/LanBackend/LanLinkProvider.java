/*
 * Copyright 2014 Albert Vaca Cintora <albertvaka@gmail.com>
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

package org.kde.kdeconnect.Backends.LanBackend;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

import org.kde.kdeconnect.Backends.BaseLink;
import org.kde.kdeconnect.Backends.BaseLinkProvider;
import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Helpers.DeviceHelper;
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper;
import org.kde.kdeconnect.Helpers.StringsHelper;
import org.kde.kdeconnect.Helpers.TrustedNetworkHelper;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.UserInterface.CustomDevicesActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;

/**
 * This BaseLinkProvider creates {@link LanLink}s to other devices on the same
 * WiFi network. The first packet sent over a socket must be an
 * {@link NetworkPacket#createIdentityPacket(Context)}.
 *
 * @see #identityPacketReceived(NetworkPacket, Socket, LanLink.ConnectionStarted)
 */
public class LanLinkProvider extends BaseLinkProvider implements LanLink.LinkDisconnectedCallback {

    private final static int MIN_PORT = 1716;
    private final static int MAX_PORT = 1764;
    final static int PAYLOAD_TRANSFER_MIN_PORT = 1739;

    private final Context context;

    private final HashMap<String, LanLink> visibleComputers = new HashMap<>();  //Links by device id

    private ServerSocket tcpServer;
    private DatagramSocket udpServer;

    private long lastBroadcast = 0;
    private final static long delayBetweenBroadcasts = 500;

    private boolean listening = false;

    // To prevent infinte loop between Android < IceCream because both device can only broadcast identity package but cannot connect via TCP
    private final ArrayList<InetAddress> reverseConnectionBlackList = new ArrayList<>();

    @Override // SocketClosedCallback
    public void linkDisconnected(LanLink brokenLink) {
        String deviceId = brokenLink.getDeviceId();
        visibleComputers.remove(deviceId);
        connectionLost(brokenLink);
    }

    //They received my UDP broadcast and are connecting to me. The first thing they sned should be their identity.
    private void tcpPacketReceived(Socket socket) {

        NetworkPacket networkPacket;
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String message = reader.readLine();
            networkPacket = NetworkPacket.unserialize(message);
            //Log.e("TcpListener","Received TCP package: "+networkPacket.serialize());
        } catch (Exception e) {
            Log.e("KDE/LanLinkProvider", "Exception while receiving TCP packet", e);
            return;
        }

        if (!networkPacket.getType().equals(NetworkPacket.PACKET_TYPE_IDENTITY)) {
            Log.e("KDE/LanLinkProvider", "Expecting an identity package instead of " + networkPacket.getType());
            return;
        }

        Log.i("KDE/LanLinkProvider", "Identity package received from a TCP connection from " + networkPacket.getString("deviceName"));
        identityPacketReceived(networkPacket, socket, LanLink.ConnectionStarted.Locally);
    }

    //I've received their broadcast and should connect to their TCP socket and send my identity.
    private void udpPacketReceived(DatagramPacket packet) {

        final InetAddress address = packet.getAddress();

        try {

            String message = new String(packet.getData(), StringsHelper.UTF8);
            final NetworkPacket identityPacket = NetworkPacket.unserialize(message);
            final String deviceId = identityPacket.getString("deviceId");
            if (!identityPacket.getType().equals(NetworkPacket.PACKET_TYPE_IDENTITY)) {
                Log.e("KDE/LanLinkProvider", "Expecting an UDP identity package");
                return;
            } else {
                String myId = DeviceHelper.getDeviceId(context);
                if (deviceId.equals(myId)) {
                    //Ignore my own broadcast
                    return;
                }
            }

            Log.i("KDE/LanLinkProvider", "Broadcast identity package received from " + identityPacket.getString("deviceName"));

            int tcpPort = identityPacket.getInt("tcpPort", MIN_PORT);

            SocketFactory socketFactory = SocketFactory.getDefault();
            Socket socket = socketFactory.createSocket(address, tcpPort);
            configureSocket(socket);

            OutputStream out = socket.getOutputStream();
            NetworkPacket myIdentity = NetworkPacket.createIdentityPacket(context);
            out.write(myIdentity.serialize().getBytes());
            out.flush();

            identityPacketReceived(identityPacket, socket, LanLink.ConnectionStarted.Remotely);

        } catch (Exception e) {
            Log.e("KDE/LanLinkProvider", "Cannot connect to " + address, e);
            if (!reverseConnectionBlackList.contains(address)) {
                Log.w("KDE/LanLinkProvider", "Blacklisting " + address);
                reverseConnectionBlackList.add(address);
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        reverseConnectionBlackList.remove(address);
                    }
                }, 5 * 1000);

                // Try to cause a reverse connection
                onNetworkChange();
            }
        }
    }

    private void configureSocket(Socket socket) {
        try {
            socket.setKeepAlive(true);
        } catch (SocketException e) {
            Log.e("LanLink", "Exception", e);
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
    private void identityPacketReceived(final NetworkPacket identityPacket, final Socket socket, final LanLink.ConnectionStarted connectionStarted) {

        String myId = DeviceHelper.getDeviceId(context);
        final String deviceId = identityPacket.getString("deviceId");
        if (deviceId.equals(myId)) {
            Log.e("KDE/LanLinkProvider", "Somehow I'm connected to myself, ignoring. This should not happen.");
            return;
        }

        // If I'm the TCP server I will be the SSL client and viceversa.
        final boolean clientMode = (connectionStarted == LanLink.ConnectionStarted.Locally);

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

            Log.i("KDE/LanLinkProvider", "Starting SSL handshake with " + identityPacket.getString("deviceName") + " trusted:" + isDeviceTrusted);

            final SSLSocket sslsocket = SslHelper.convertToSslSocket(context, socket, deviceId, isDeviceTrusted, clientMode);
            sslsocket.addHandshakeCompletedListener(event -> {
                String mode = clientMode ? "client" : "server";
                try {
                    Certificate certificate = event.getPeerCertificates()[0];
                    identityPacket.set("certificate", Base64.encodeToString(certificate.getEncoded(), 0));
                    Log.i("KDE/LanLinkProvider", "Handshake as " + mode + " successful with " + identityPacket.getString("deviceName") + " secured with " + event.getCipherSuite());
                    addLink(identityPacket, sslsocket, connectionStarted);
                } catch (Exception e) {
                    Log.e("KDE/LanLinkProvider", "Handshake as " + mode + " failed with " + identityPacket.getString("deviceName"), e);
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
                    Log.e("KDE/LanLinkProvider", "Handshake failed with " + identityPacket.getString("deviceName"), e);

                    //String[] ciphers = sslsocket.getSupportedCipherSuites();
                    //for (String cipher : ciphers) {
                    //    Log.i("SupportedCiphers","cipher: " + cipher);
                    //}
                }
            }).start();
        } catch (Exception e) {
            Log.e("LanLink", "Exception", e);
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
     * @throws IOException if an exception is thrown by {@link LanLink#reset(SSLSocket, LanLink.ConnectionStarted)}
     */
    private void addLink(final NetworkPacket identityPacket, SSLSocket socket, LanLink.ConnectionStarted connectionOrigin) throws IOException {

        String deviceId = identityPacket.getString("deviceId");
        LanLink currentLink = visibleComputers.get(deviceId);
        if (currentLink != null) {
            //Update old link
            Log.i("KDE/LanLinkProvider", "Reusing same link for device " + deviceId);
            final Socket oldSocket = currentLink.reset(socket, connectionOrigin);
            //Log.e("KDE/LanLinkProvider", "Replacing socket. old: "+ oldSocket.hashCode() + " - new: "+ socket.hashCode());
        } else {
            Log.i("KDE/LanLinkProvider", "Creating a new link for device " + deviceId);
            //Let's create the link
            LanLink link = new LanLink(context, deviceId, this, socket, connectionOrigin);
            visibleComputers.put(deviceId, link);
            connectionAccepted(identityPacket, link);
        }
    }

    public LanLinkProvider(Context context) {
        this.context = context;
    }

    private void setupUdpListener() {
        try {
            udpServer = new DatagramSocket(MIN_PORT);
            udpServer.setReuseAddress(true);
            udpServer.setBroadcast(true);
        } catch (SocketException e) {
            Log.e("LanLinkProvider", "Error creating udp server", e);
            return;
        }
        new Thread(() -> {
            while (listening) {
                final int bufferSize = 1024 * 512;
                byte[] data = new byte[bufferSize];
                DatagramPacket packet = new DatagramPacket(data, bufferSize);
                try {
                    udpServer.receive(packet);
                    udpPacketReceived(packet);
                } catch (Exception e) {
                    Log.e("LanLinkProvider", "UdpReceive exception", e);
                }
            }
            Log.w("UdpListener", "Stopping UDP listener");
        }).start();
    }

    private void setupTcpListener() {
        try {
            tcpServer = openServerSocketOnFreePort(MIN_PORT);
        } catch (Exception e) {
            Log.e("LanLinkProvider", "Error creating tcp server", e);
            return;
        }
        new Thread(() -> {
            while (listening) {
                try {
                    Socket socket = tcpServer.accept();
                    configureSocket(socket);
                    tcpPacketReceived(socket);
                } catch (Exception e) {
                    Log.e("LanLinkProvider", "TcpReceive exception", e);
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
                Log.i("KDE/LanLink", "Using port " + tcpPort);
                return candidateServer;
            } catch (IOException e) {
                tcpPort++;
                if (tcpPort == MAX_PORT) {
                    Log.e("KDE/LanLink", "No ports available");
                    throw e; //Propagate exception
                }
            }
        }
        throw new RuntimeException("This should not be reachable");
    }

    private void broadcastUdpPacket() {
        if (System.currentTimeMillis() < lastBroadcast + delayBetweenBroadcasts) {
            Log.i("LanLinkProvider", "broadcastUdpPacket: relax cowboy");
            return;
        }
        lastBroadcast = System.currentTimeMillis();

        new Thread(() -> {
            ArrayList<String> iplist = CustomDevicesActivity
                    .getCustomDeviceList(PreferenceManager.getDefaultSharedPreferences(context));

            if (TrustedNetworkHelper.isTrustedNetwork(context)) {
                iplist.add("255.255.255.255"); //Default: broadcast.
            } else {
                Log.i("LanLinkProvider", "Current network isn't trusted, not broadcasting");
            }

            if (iplist.isEmpty()) {
                return;
            }

            NetworkPacket identity = NetworkPacket.createIdentityPacket(context);
            int port = (tcpServer == null || !tcpServer.isBound()) ? MIN_PORT : tcpServer.getLocalPort();
            identity.set("tcpPort", port);
            DatagramSocket socket = null;
            byte[] bytes = null;
            try {
                socket = new DatagramSocket();
                socket.setReuseAddress(true);
                socket.setBroadcast(true);
                bytes = identity.serialize().getBytes(StringsHelper.UTF8);
            } catch (Exception e) {
                Log.e("KDE/LanLinkProvider", "Failed to create DatagramSocket", e);
            }

            if (bytes != null) {
                //Log.e("KDE/LanLinkProvider","Sending packet to "+iplist.size()+" ips");
                for (String ipstr : iplist) {
                    try {
                        InetAddress client = InetAddress.getByName(ipstr);
                        socket.send(new DatagramPacket(bytes, bytes.length, client, MIN_PORT));
                        //Log.i("KDE/LanLinkProvider","Udp identity package sent to address "+client);
                    } catch (Exception e) {
                        Log.e("KDE/LanLinkProvider", "Sending udp identity package failed. Invalid address? (" + ipstr + ")", e);
                    }
                }
            }

            if (socket != null) {
                socket.close();
            }

        }).start();
    }

    @Override
    public void onStart() {
        //Log.i("KDE/LanLinkProvider", "onStart");
        if (!listening) {

            listening = true;

            setupUdpListener();
            setupTcpListener();

            broadcastUdpPacket();
        }
    }

    @Override
    public void onNetworkChange() {
        broadcastUdpPacket();
    }

    @Override
    public void onStop() {
        //Log.i("KDE/LanLinkProvider", "onStop");
        listening = false;
        try {
            tcpServer.close();
        } catch (Exception e) {
            Log.e("LanLink", "Exception", e);
        }
        try {
            udpServer.close();
        } catch (Exception e) {
            Log.e("LanLink", "Exception", e);
        }
    }

    @Override
    public String getName() {
        return "LanLinkProvider";
    }

}
