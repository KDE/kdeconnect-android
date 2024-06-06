/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Backends.LanBackend;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Network;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.json.JSONException;
import org.kde.kdeconnect.Backends.BaseLink;
import org.kde.kdeconnect.Backends.BaseLinkProvider;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.DeviceInfo;
import org.kde.kdeconnect.Helpers.DeviceHelper;
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper;
import org.kde.kdeconnect.Helpers.ThreadHelper;
import org.kde.kdeconnect.Helpers.TrustedNetworkHelper;
import org.kde.kdeconnect.KdeConnect;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.UserInterface.CustomDevicesActivity;
import org.kde.kdeconnect.UserInterface.SettingsFragment;

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
 * @see #identityPacketReceived(NetworkPacket, Socket, LanLink.ConnectionStarted)
 */
public class LanLinkProvider extends BaseLinkProvider {

    final static int UDP_PORT = 1716;
    final static int MIN_PORT = 1716;
    final static int MAX_PORT = 1764;
    final static int PAYLOAD_TRANSFER_MIN_PORT = 1739;

    final static int MAX_UDP_PACKET_SIZE = 1024 * 512;

    final static long MILLIS_DELAY_BETWEEN_CONNECTIONS_TO_SAME_DEVICE = 500L;

    private final Context context;

    final HashMap<String, LanLink> visibleDevices = new HashMap<>(); // Links by device id

    final ConcurrentHashMap<String, Long> lastConnectionTime = new ConcurrentHashMap<>();

    private ServerSocket tcpServer;
    private DatagramSocket udpServer;

    private MdnsDiscovery mdnsDiscovery;

    private long lastBroadcast = 0;
    private final static long delayBetweenBroadcasts = 200;

    private boolean listening = false;

    public void onConnectionLost(BaseLink link) {
        String deviceId = link.getDeviceId();
        visibleDevices.remove(deviceId);
        super.onConnectionLost(link);
    }

    //They received my UDP broadcast and are connecting to me. The first thing they send should be their identity packet.
    @WorkerThread
    private void tcpPacketReceived(Socket socket) throws IOException {

        NetworkPacket networkPacket;
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String message = reader.readLine();
            networkPacket = NetworkPacket.unserialize(message);
            //Log.e("TcpListener", "Received TCP packet: " + networkPacket.serialize());
        } catch (Exception e) {
            Log.e("KDE/LanLinkProvider", "Exception while receiving TCP packet", e);
            return;
        }

        Log.i("KDE/LanLinkProvider", "identity packet received from a TCP connection from " + networkPacket.getString("deviceName"));
        identityPacketReceived(networkPacket, socket, LanLink.ConnectionStarted.Locally);
    }

    //I've received their broadcast and should connect to their TCP socket and send my identity.
    @WorkerThread
    private void udpPacketReceived(DatagramPacket packet) throws JSONException, IOException {

        final InetAddress address = packet.getAddress();

        String message = new String(packet.getData(), Charsets.UTF_8);
        final NetworkPacket identityPacket = NetworkPacket.unserialize(message);

        if (!DeviceInfo.isValidIdentityPacket(identityPacket)) {
            Log.w("KDE/LanLinkProvider", "Invalid identity packet received.");
            return;
        }

        final String deviceId = identityPacket.getString("deviceId");
        String myId = DeviceHelper.getDeviceId(context);
        if (deviceId.equals(myId)) {
            //Ignore my own broadcast
            return;
        }

        long now = System.currentTimeMillis();
        Long last =  lastConnectionTime.get(deviceId);
        if (last != null && (last + MILLIS_DELAY_BETWEEN_CONNECTIONS_TO_SAME_DEVICE > now)) {
            Log.i("LanLinkProvider", "Discarding second UDP packet from the same device " + deviceId + " received too quickly");
            return;
        }
        lastConnectionTime.put(deviceId, now);

        int tcpPort = identityPacket.getInt("tcpPort", MIN_PORT);
        if (tcpPort < MIN_PORT || tcpPort > MAX_PORT) {
            Log.e("LanLinkProvider", "TCP port outside of kdeconnect's range");
            return;
        }

        Log.i("KDE/LanLinkProvider", "Broadcast identity packet received from " + identityPacket.getString("deviceName"));

        SocketFactory socketFactory = SocketFactory.getDefault();
        Socket socket = socketFactory.createSocket(address, tcpPort);
        configureSocket(socket);

        DeviceInfo myDeviceInfo = DeviceHelper.getDeviceInfo(context);
        NetworkPacket myIdentity = myDeviceInfo.toIdentityPacket();

        OutputStream out = socket.getOutputStream();
        out.write(myIdentity.serialize().getBytes());
        out.flush();

        identityPacketReceived(identityPacket, socket, LanLink.ConnectionStarted.Remotely);
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
     * Should be called on a new thread since it blocks until the handshake is completed.
     * </p><p>
     * If the remote device should be connected, this calls {@link #addLink}.
     * Otherwise, if there was an Exception, we unpair from that device.
     * </p>
     *
     * @param identityPacket    identity of a remote device
     * @param socket            a new Socket, which should be used to receive packets from the remote device
     * @param connectionStarted which side started this connection
     */
    @WorkerThread
    private void identityPacketReceived(final NetworkPacket identityPacket, final Socket socket, final LanLink.ConnectionStarted connectionStarted) throws IOException {

        if (!DeviceInfo.isValidIdentityPacket(identityPacket)) {
            Log.w("KDE/LanLinkProvider", "Invalid identity packet received.");
            return;
        }

        String myId = DeviceHelper.getDeviceId(context);
        final String deviceId = identityPacket.getString("deviceId");
        if (deviceId.equals(myId)) {
            Log.e("KDE/LanLinkProvider", "Somehow I'm connected to myself, ignoring. This should not happen.");
            return;
        }

        // If I'm the TCP server I will be the SSL client and viceversa.
        final boolean clientMode = (connectionStarted == LanLink.ConnectionStarted.Locally);

        SharedPreferences preferences = context.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE);
        boolean isDeviceTrusted = preferences.getBoolean(deviceId, false);

        if (isDeviceTrusted && !SslHelper.isCertificateStored(context, deviceId)) {
            //Device paired with and old version, we can't use it as we lack the certificate
            Device device = KdeConnect.getInstance().getDevice(deviceId);
            if (device == null) {
                return;
            }
            device.unpair();
            //Retry as unpaired
            identityPacketReceived(identityPacket, socket, connectionStarted);
        }

        String deviceName = identityPacket.getString("deviceName", "unknown");
        Log.i("KDE/LanLinkProvider", "Starting SSL handshake with " + deviceName + " trusted:" + isDeviceTrusted);

        final SSLSocket sslSocket = SslHelper.convertToSslSocket(context, socket, deviceId, isDeviceTrusted, clientMode);
        sslSocket.addHandshakeCompletedListener(event -> {
            String mode = clientMode ? "client" : "server";
            try {
                Certificate certificate = event.getPeerCertificates()[0];
                DeviceInfo deviceInfo = DeviceInfo.fromIdentityPacketAndCert(identityPacket, certificate);
                Log.i("KDE/LanLinkProvider", "Handshake as " + mode + " successful with " + deviceName + " secured with " + event.getCipherSuite());
                addOrUpdateLink(sslSocket, deviceInfo);
            } catch (IOException e) {
                Log.e("KDE/LanLinkProvider", "Handshake as " + mode + " failed with " + deviceName, e);
                Device device = KdeConnect.getInstance().getDevice(deviceId);
                if (device == null) {
                    return;
                }
                device.unpair();
            }
        });

        //Handshake is blocking, so do it on another thread and free this thread to keep receiving new connection
        Log.d("LanLinkProvider", "Starting handshake");
        sslSocket.startHandshake();
        Log.d("LanLinkProvider", "Handshake done");
    }

    /**
     * Add or update a link in the {@link #visibleDevices} map.
     *
     * @param socket           a new Socket, which should be used to send and receive packets from the remote device
     * @param deviceInfo       remote device info
     * @throws IOException if an exception is thrown by {@link LanLink#reset(SSLSocket, DeviceInfo)}
     */
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

    private void broadcastUdpIdentityPacket(@Nullable Network network) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (!preferences.getBoolean(SettingsFragment.KEY_UDP_BROADCAST_ENABLED, true)) {
            Log.i("LanLinkProvider", "UDP broadcast is disabled in settings. Skipping.");
            return;
        }

        ThreadHelper.execute(() -> {
            List<String> ipStringList = CustomDevicesActivity
                    .getCustomDeviceList(PreferenceManager.getDefaultSharedPreferences(context));

            if (TrustedNetworkHelper.isTrustedNetwork(context)) {
                ipStringList.add("255.255.255.255"); //Default: broadcast.
            } else {
                Log.i("LanLinkProvider", "Current network isn't trusted, not broadcasting");
            }

            ArrayList<InetAddress> ipList = new ArrayList<>();
            for (String ip : ipStringList) {
                try {
                    ipList.add(InetAddress.getByName(ip));
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
            if (network != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
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
            mdnsDiscovery.startAnnouncing();

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

}
