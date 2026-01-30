/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.backends.lan;

import static main.java.org.kde.kdeconnect.helpers.BoundedLineReaderKt.readLineBounded;

import android.content.Context;
import android.net.Network;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.json.JSONException;
import org.kde.kdeconnect.backends.BaseLink;
import org.kde.kdeconnect.backends.BaseLinkProvider;
import org.kde.kdeconnect.DeviceHost;
import org.kde.kdeconnect.DeviceInfo;
import org.kde.kdeconnect.helpers.DeviceHelper;
import org.kde.kdeconnect.helpers.security.SslHelper;
import org.kde.kdeconnect.helpers.ThreadHelper;
import org.kde.kdeconnect.helpers.TrustedDevices;
import org.kde.kdeconnect.helpers.TrustedNetworkHelper;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.ui.CustomDevicesActivity;

import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;

import kotlin.text.Charsets;

/**
 * This LanLinkProvider creates {@link LanLink}s to other devices on the same
 * WiFi network. The first packet sent over a socket must be an
 * {@link DeviceInfo#toIdentityPacket()}.
 *
 * @see #identityPacketReceived(NetworkPacket, Socket, LanLink.ConnectionStarted, boolean)
 */
public class LanLinkProvider extends BaseLinkProvider {

    final static int UDP_PORT = 1716;
    final static int MIN_PORT = 1716;
    final static int MAX_PORT = 1764;
    final static int PAYLOAD_TRANSFER_MIN_PORT = 1739;

    final static int MAX_IDENTITY_PACKET_SIZE = 1024 * 512;
    final static int MAX_UDP_PACKET_SIZE = 1024 * 512;

    final static long MILLIS_DELAY_BETWEEN_CONNECTIONS_TO_SAME_DEVICE = 1000L;

    private final Context context;

    final HashMap<String, LanLink> visibleDevices = new HashMap<>(); // Links by device id

    final static int MAX_RATE_LIMIT_ENTRIES = 255;
    final ConcurrentHashMap<String, Long> lastConnectionTimeByDeviceId = new ConcurrentHashMap<>();
    final ConcurrentHashMap<InetAddress, Long> lastConnectionTimeByIp = new ConcurrentHashMap<>();

    private ServerSocket tcpServer;
    private DatagramSocket udpServer;

    private final MdnsDiscovery mdnsDiscovery;

    private long lastBroadcast = 0;
    private final static long delayBetweenBroadcasts = 200;

    private boolean listening = false;

    public void onConnectionLost(BaseLink link) {
        String deviceId = link.getDeviceId();
        visibleDevices.remove(deviceId);
        super.onConnectionLost(link);
    }

    Pair<NetworkPacket, Boolean> unserializeReceivedIdentityPacket(String message) {
        NetworkPacket identityPacket;
        try {
            identityPacket = NetworkPacket.unserialize(message);
        } catch (JSONException e) {
            Log.w("KDE/LanLinkProvider", "Invalid identity packet received: " + e.getMessage());
            return null;
        }

        if (!DeviceInfo.isValidIdentityPacket(identityPacket)) {
            Log.w("KDE/LanLinkProvider", "Invalid identity packet received.");
            return null;
        }

        final String deviceId = identityPacket.getString("deviceId");
        String myId = DeviceHelper.getDeviceId(context);
        if (deviceId.equals(myId)) {
            //Ignore my own broadcast
            return null;
        }

        if (rateLimitByDeviceId(deviceId)) {
            Log.i("LanLinkProvider", "Discarding second packet from the same device " + deviceId + " received too quickly");
            return null;
        }

        boolean deviceTrusted = TrustedDevices.isTrustedDevice(context, deviceId);
        if (!deviceTrusted && !TrustedNetworkHelper.isTrustedNetwork(context)) {
            Log.i("KDE/LanLinkProvider", "Ignoring identity packet because the device is not trusted and I'm not on a trusted network.");
            return null;
        }

        return new Pair<>(identityPacket, deviceTrusted);
    }

    //They received my UDP broadcast and are connecting to me. The first thing they send should be their identity packet.
    @WorkerThread
    private void tcpPacketReceived(Socket socket) throws IOException {

        InetAddress address = socket.getInetAddress();

        if (!address.isSiteLocalAddress() && !address.isLinkLocalAddress()) {
            Log.i("LanLinkProvider", "Discarding UDP packet from a non-local IP");
            return;
        }

        if (rateLimitByIp(address)) {
            Log.i("LanLinkProvider", "Discarding second TCP packet from the same ip " + address + " received too quickly");
            return;
        }

        String message;
        try {
            // We don't use a BufferedInputStream on purpose, since BufferedReader reads ahead and would require
            // us to keep a single BufferedInputStream instance and pass it around to make sure we don't lose data.
            // This means we are readying byte by byte directly from the OS, which is slow, but only for the handshake.
            message = readLineBounded(socket.getInputStream(), MAX_IDENTITY_PACKET_SIZE);
            //Log.e("TcpListener", "Received TCP packet: " + message);
        } catch (Exception e) {
            Log.e("KDE/LanLinkProvider", "Exception while receiving TCP packet", e);
            return;
        }

        final Pair<NetworkPacket, Boolean> pair = unserializeReceivedIdentityPacket(message);
        if (pair == null) {
            return;
        }
        final NetworkPacket identityPacket = pair.first;
        final boolean deviceTrusted = pair.second;

        Log.i("KDE/LanLinkProvider", "identity packet received from a TCP connection from " + identityPacket.getString("deviceName"));

        String targetDeviceId = identityPacket.getStringOrNull("targetDeviceId");
        Integer targetProtocolVersion = identityPacket.getIntOrNull("targetProtocolVersion");
        if (targetDeviceId != null && !targetDeviceId.equals(DeviceHelper.getDeviceId(context))) {
            Log.e("KDE/LanLinkProvider","Received a connection request for a device that isn't me: " + targetDeviceId);
            return;
        }
        if (targetProtocolVersion != null && targetProtocolVersion != DeviceHelper.PROTOCOL_VERSION) {
            Log.e("KDE/LanLinkProvider","Received a connection request for a protocol version that isn't mine: " + targetProtocolVersion);
            return;
        }

        identityPacketReceived(identityPacket, socket, LanLink.ConnectionStarted.Locally, deviceTrusted);
    }

    boolean rateLimitByIp(InetAddress address) {
        long now = System.currentTimeMillis();
        Long last = lastConnectionTimeByIp.get(address);
        if (last != null && (last + MILLIS_DELAY_BETWEEN_CONNECTIONS_TO_SAME_DEVICE > now)) {
            return true;
        }
        lastConnectionTimeByIp.put(address, now);
        if (lastConnectionTimeByIp.size() > MAX_RATE_LIMIT_ENTRIES) {
            lastConnectionTimeByIp.entrySet().removeIf(e -> e.getValue() + MILLIS_DELAY_BETWEEN_CONNECTIONS_TO_SAME_DEVICE < now);
        }
        return false;
    }

    boolean rateLimitByDeviceId(String deviceId) {
        long now = System.currentTimeMillis();
        Long last =  lastConnectionTimeByDeviceId.get(deviceId);
        if (last != null && (last + MILLIS_DELAY_BETWEEN_CONNECTIONS_TO_SAME_DEVICE > now)) {
            return true;
        }
        lastConnectionTimeByDeviceId.put(deviceId, now);
        if (lastConnectionTimeByDeviceId.size() > MAX_RATE_LIMIT_ENTRIES) {
            lastConnectionTimeByDeviceId.entrySet().removeIf(e -> e.getValue() + MILLIS_DELAY_BETWEEN_CONNECTIONS_TO_SAME_DEVICE < now);
        }
        return false;
    }

    //I've received their broadcast and should connect to their TCP socket and send my identity.
    @WorkerThread
    private void udpPacketReceived(DatagramPacket packet) throws JSONException, IOException {

        final InetAddress address = packet.getAddress();

        if (!address.isSiteLocalAddress() && !address.isLinkLocalAddress()) {
            Log.i("LanLinkProvider", "Discarding UDP packet from a non-local IP");
            return;
        }

        if (rateLimitByIp(address)) {
            Log.i("LanLinkProvider", "Discarding second UDP packet from the same ip " + address + " received too quickly");
            return;
        }

        String message = new String(packet.getData(), Charsets.UTF_8);

        final Pair<NetworkPacket, Boolean> pair = unserializeReceivedIdentityPacket(message);
        if (pair == null) {
            return;
        }
        final NetworkPacket identityPacket = pair.first;
        final boolean deviceTrusted = pair.second;

        Log.i("KDE/LanLinkProvider", "Broadcast identity packet received from " + identityPacket.getString("deviceName"));

        int tcpPort = identityPacket.getInt("tcpPort", MIN_PORT);
        if (tcpPort < MIN_PORT || tcpPort > MAX_PORT) {
            Log.e("LanLinkProvider", "TCP port outside of kdeconnect's range");
            return;
        }

        SocketFactory socketFactory = SocketFactory.getDefault();
        Socket socket = socketFactory.createSocket(address, tcpPort);
        configureSocket(socket);

        DeviceInfo myDeviceInfo = DeviceHelper.getDeviceInfo(context);
        NetworkPacket myIdentity = myDeviceInfo.toIdentityPacket();
        myIdentity.set("targetDeviceId", identityPacket.getString("deviceId"));
        myIdentity.set("targetProtocolVersion", identityPacket.getString("protocolVersion"));
        OutputStream out = socket.getOutputStream();
        out.write(myIdentity.serialize().getBytes());
        out.flush();

        identityPacketReceived(identityPacket, socket, LanLink.ConnectionStarted.Remotely, deviceTrusted);
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
     * Should be called on a new thread since it blocks until the handshake is completed.
     *
     * @param identityPacket    identity of a remote device
     * @param socket            a new Socket, which should be used to receive packets from the remote device
     * @param connectionStarted which side started this connection
     * @param deviceTrusted     whether the packet comes from a trusted device
     */
    @WorkerThread
    private void identityPacketReceived(final NetworkPacket identityPacket, final Socket socket, final LanLink.ConnectionStarted connectionStarted, final boolean deviceTrusted) throws IOException {
        final String deviceId = identityPacket.getString("deviceId");
        final int protocolVersion = identityPacket.getInt("protocolVersion");

        if (deviceTrusted && isProtocolDowngrade(deviceId, protocolVersion)) {
            Log.w("KDE/LanLinkProvider", "Refusing to connect to a device using an older protocol version:" + protocolVersion);
            return;
        }

        if (deviceTrusted && !TrustedDevices.isCertificateStored(context, deviceId)) {
            Log.e("KDE/LanLinkProvider", "Device trusted but no cert stored. This should not happen.");
            return;
        }

        Log.i("KDE/LanLinkProvider", "Starting SSL handshake with " + deviceId + " trusted:" + deviceTrusted);

        // If I'm the TCP server I will be the SSL client and vice-versa.
        final boolean clientMode = (connectionStarted == LanLink.ConnectionStarted.Locally);
        final SSLSocket sslSocket = SslHelper.convertToSslSocket(context, socket, deviceId, deviceTrusted, clientMode);
        sslSocket.addHandshakeCompletedListener(event -> {
            // Start a new thread because some Android versions don't allow calling sslSocket.getOutputStream() from the callback
            ThreadHelper.execute(() -> {
                String mode = clientMode ? "client" : "server";
                try {
                    NetworkPacket secureIdentityPacket;
                    if (protocolVersion >= 8) {
                        DeviceInfo myDeviceInfo = DeviceHelper.getDeviceInfo(context);
                        NetworkPacket myIdentity = myDeviceInfo.toIdentityPacket();

                        OutputStream writer = sslSocket.getOutputStream();
                        writer.write(myIdentity.serialize().getBytes(Charsets.UTF_8));
                        writer.flush();
                        String line = readLineBounded(sslSocket.getInputStream(), MAX_IDENTITY_PACKET_SIZE);
                        // Do not trust the identity packet we received unencrypted
                        secureIdentityPacket = NetworkPacket.unserialize(line);
                        if (!DeviceInfo.isValidIdentityPacket(secureIdentityPacket)) {
                            Log.e("KDE/LanLinkProvider", "Identity packet isn't valid");
                            sslSocket.close();
                            return;
                        }
                        int newProtocolVersion = secureIdentityPacket.getInt("protocolVersion");
                        if (newProtocolVersion != protocolVersion) {
                            Log.e("KDE/LanLinkProvider", "Protocol version changed half-way through the handshake: " + protocolVersion + " -> " + newProtocolVersion);
                            sslSocket.close();
                            return;
                        }
                        String newDeviceId = secureIdentityPacket.getString("deviceId");
                        if (!newDeviceId.equals(deviceId)) {
                            Log.e("KDE/LanLinkProvider", "Device ID changed half-way through the handshake: " + deviceId + " -> " + newDeviceId);
                            sslSocket.close();
                            return;
                        }
                    } else {
                        secureIdentityPacket = identityPacket;
                    }
                    Certificate certificate = event.getPeerCertificates()[0];
                    DeviceInfo deviceInfo = DeviceInfo.fromIdentityPacketAndCert(secureIdentityPacket, certificate);
                    Log.i("KDE/LanLinkProvider", "Handshake as " + mode + " successful with " + deviceInfo.name + " secured with " + event.getCipherSuite());
                    addOrUpdateLink(sslSocket, deviceInfo);
                } catch (JSONException e) {
                    Log.e("KDE/LanLinkProvider", "Remote device doesn't correctly implement protocol version 8", e);
                    try { sslSocket.close(); } catch (IOException ignored) { }
                } catch (IOException e) {
                    Log.e("KDE/LanLinkProvider", "Handshake as " + mode + " failed with " + deviceId, e);
                    try { sslSocket.close(); } catch (IOException ignored) { }
                }
            });
        });

        //Handshake is blocking, so do it on another thread and free this thread to keep receiving new connection
        Log.d("LanLinkProvider", "Starting handshake");
        sslSocket.startHandshake();
        Log.d("LanLinkProvider", "Handshake done");
    }

    private boolean isProtocolDowngrade(String deviceId, int protocolVersion) {
        int lastKnownProtocolVersion = DeviceInfo.loadProtocolVersionFromSettings(context, deviceId);
        return lastKnownProtocolVersion > protocolVersion;
    }

    /**
     * Add or update a link in the {@link #visibleDevices} map.
     *
     * @param socket           a new Socket, which should be used to send and receive packets from the remote device
     * @param deviceInfo       remote device info
     * @throws IOException if an exception is thrown by {@link LanLink#reset(SSLSocket, DeviceInfo)}
     */
    @WorkerThread
    private void addOrUpdateLink(SSLSocket socket, DeviceInfo deviceInfo) throws IOException {
        LanLink link = visibleDevices.get(deviceInfo.id);
        if (link != null) {
            if (!link.getDeviceInfo().certificate.equals(deviceInfo.certificate)) {
                Log.e("LanLinkProvider", "LanLink was asked to replace a socket but the certificate doesn't match, aborting");
                return;
            }
            // Update existing link
            Log.d("KDE/LanLinkProvider", "Reusing same link for device " + deviceInfo.id);
            link.reset(socket, deviceInfo);
            onDeviceInfoUpdated(deviceInfo);
        } else {
            // Create a new link
            Log.d("KDE/LanLinkProvider", "Creating a new link for device " + deviceInfo.id);
            link = new LanLink(context, deviceInfo, this, socket);
            visibleDevices.put(deviceInfo.id, link);
            onConnectionReceived(link);
        }
    }

    public LanLinkProvider(Context context) {
        this.context = context;
        this.mdnsDiscovery = new MdnsDiscovery(context, this);
    }

    private void setupUdpListener() {
        try {
            udpServer = new DatagramSocket(null);
            udpServer.setReuseAddress(true);
            udpServer.setBroadcast(true);
        } catch (SocketException e) {
            Log.e("LanLinkProvider", "Error creating udp server", e);
            throw new RuntimeException(e);
        }
        try {
            udpServer.bind(new InetSocketAddress(UDP_PORT));
        } catch (SocketException e) {
            // We ignore this exception and continue without being able to receive broadcasts instead of crashing the app.
            Log.e("LanLinkProvider", "Error binding udp server. We can send udp broadcasts but not receive them", e);
        }
        ThreadHelper.execute(() -> {
            Log.i("UdpListener", "Starting UDP listener");
            while (listening) {
                try {
                    DatagramPacket packet = new DatagramPacket(new byte[MAX_UDP_PACKET_SIZE], MAX_UDP_PACKET_SIZE);
                    udpServer.receive(packet);
                    ThreadHelper.execute(() -> {
                        try {
                            udpPacketReceived(packet);
                        } catch (JSONException | IOException e) {
                            Log.e("LanLinkProvider", "Exception receiving incoming UDP connection", e);
                        }
                    });
                } catch (IOException e) {
                    Log.e("LanLinkProvider", "UdpReceive exception", e);
                    onNetworkChange(null); // Trigger a UDP broadcast to try to get them to connect to us instead
                }
            }
            Log.w("UdpListener", "Stopping UDP listener");
        });
    }

    private void setupTcpListener() {
        try {
            tcpServer = openServerSocketOnFreePort(MIN_PORT);
        } catch (IOException e) {
            Log.e("LanLinkProvider", "Error creating tcp server", e);
            throw new RuntimeException(e);
        }
        ThreadHelper.execute(() -> {
            while (listening) {
                try {
                    Socket socket = tcpServer.accept();
                    configureSocket(socket);
                    ThreadHelper.execute(() -> {
                        try {
                            tcpPacketReceived(socket);
                        } catch (IOException e) {
                            Log.e("LanLinkProvider", "Exception receiving incoming TCP connection", e);
                        }
                    });
                } catch (Exception e) {
                    Log.e("LanLinkProvider", "TcpReceive exception", e);
                }
            }
            Log.w("TcpListener", "Stopping TCP listener");
        });

    }

    static ServerSocket openServerSocketOnFreePort(int minPort) throws IOException {
        int tcpPort = minPort;
        while (tcpPort <= MAX_PORT) {
            try {
                ServerSocket candidateServer = new ServerSocket(tcpPort);
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

    private void broadcastUdpIdentityPacket(@Nullable Network network) {
        ThreadHelper.execute(() -> {
            List<DeviceHost> hostList = CustomDevicesActivity
                    .getCustomDeviceList(context);

            if (TrustedNetworkHelper.isTrustedNetwork(context)) {
                hostList.add(DeviceHost.BROADCAST); //Default: broadcast.
            } else {
                Log.i("LanLinkProvider", "Current network isn't trusted, not broadcasting");
            }

            ArrayList<InetAddress> ipList = new ArrayList<>();
            for (DeviceHost host : hostList) {
                try {
                    ipList.add(InetAddress.getByName(host.toString()));
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
            }

            if (ipList.isEmpty()) {
                return;
            }

            sendUdpIdentityPacket(ipList, network);
        });
    }

    @WorkerThread
    public void sendUdpIdentityPacket(List<InetAddress> ipList, @Nullable Network network) {
        if (tcpServer == null || !tcpServer.isBound()) {
            Log.i("LanLinkProvider", "Won't broadcast UDP packet if TCP socket is not ready yet");
            return;
        }

        // TODO: In protocol version 8 this packet doesn't need to contain identity info
        //       since it will be exchanged after the socket is encrypted.
        DeviceInfo myDeviceInfo = DeviceHelper.getDeviceInfo(context);
        NetworkPacket identity = myDeviceInfo.toIdentityPacket();
        identity.set("tcpPort", tcpServer.getLocalPort());

        byte[] bytes;
        try {
            bytes = identity.serialize().getBytes(Charsets.UTF_8);
        } catch (JSONException e) {
            Log.e("KDE/LanLinkProvider", "Failed to serialize identity packet", e);
            return;
        }

        DatagramSocket socket;
        try {
            socket = new DatagramSocket();
            if (network != null) {
                try {
                    network.bindSocket(socket);
                } catch (IOException e) {
                    Log.w("LanLinkProvider", "Couldn't bind socket to the network");
                    e.printStackTrace();
                }
            }
            socket.setReuseAddress(true);
            socket.setBroadcast(true);
        } catch (SocketException e) {
            Log.e("KDE/LanLinkProvider", "Failed to create DatagramSocket", e);
            return;
        }

        for (InetAddress ip : ipList) {
            try {
                socket.send(new DatagramPacket(bytes, bytes.length, ip, MIN_PORT));
                //Log.i("KDE/LanLinkProvider","Udp identity packet sent to address "+client);
            } catch (IOException e) {
                Log.e("KDE/LanLinkProvider", "Sending udp identity packet failed. Invalid address? (" + ip.toString() + ")", e);
            }
        }

        socket.close();
    }

    @Override
    public void onStart() {
        //Log.i("KDE/LanLinkProvider", "onStart");
        if (!listening) {

            listening = true;

            setupUdpListener();
            setupTcpListener();

            mdnsDiscovery.startDiscovering();
            if (TrustedNetworkHelper.isTrustedNetwork(context)) {
                mdnsDiscovery.startAnnouncing();
            }

            broadcastUdpIdentityPacket(null);
        }
    }

    @Override
    public void onNetworkChange(@Nullable Network network) {
        if (System.currentTimeMillis() < lastBroadcast + delayBetweenBroadcasts) {
            Log.i("LanLinkProvider", "onNetworkChange: relax cowboy");
            return;
        }
        lastBroadcast = System.currentTimeMillis();

        broadcastUdpIdentityPacket(network);
        mdnsDiscovery.stopDiscovering();
        mdnsDiscovery.startDiscovering();
    }

    @Override
    public void onStop() {
        //Log.i("KDE/LanLinkProvider", "onStop");
        listening = false;
        mdnsDiscovery.stopAnnouncing();
        mdnsDiscovery.stopDiscovering();
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

    @Override
    public int getPriority() { return 20; }

    public int getTcpPort() { return tcpServer.getLocalPort(); }

}
