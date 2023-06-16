/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Backends.LanBackend;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.WorkerThread;

import org.json.JSONException;
import org.kde.kdeconnect.Backends.BaseLink;
import org.kde.kdeconnect.Backends.BaseLinkProvider;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Helpers.DeviceHelper;
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper;
import org.kde.kdeconnect.Helpers.ThreadHelper;
import org.kde.kdeconnect.Helpers.TrustedNetworkHelper;
import org.kde.kdeconnect.KdeConnect;
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
import java.net.UnknownHostException;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;

import kotlin.text.Charsets;

/**
 * This LanLinkProvider creates {@link LanLink}s to other devices on the same
 * WiFi network. The first packet sent over a socket must be an
 * {@link NetworkPacket#createIdentityPacket(Context)}.
 *
 * @see #identityPacketReceived(NetworkPacket, Socket, LanLink.ConnectionStarted)
 */
public class LanLinkProvider extends BaseLinkProvider {

    private final static int UDP_PORT = 1716;
    private final static int MIN_PORT = 1716;
    private final static int MAX_PORT = 1764;
    final static int PAYLOAD_TRANSFER_MIN_PORT = 1739;

    final static int MAX_UDP_PACKET_SIZE = 1024 * 512;

    private final Context context;

    final HashMap<String, LanLink> visibleDevices = new HashMap<>();  //Links by device id

    ServerSocket tcpServer;
    DatagramSocket udpServer;

    MdnsDiscovery mdnsDiscovery;

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

        if (!networkPacket.getType().equals(NetworkPacket.PACKET_TYPE_IDENTITY)) {
            Log.e("KDE/LanLinkProvider", "Expecting an identity packet instead of " + networkPacket.getType());
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
        final String deviceId = identityPacket.getString("deviceId");
        if (!identityPacket.getType().equals(NetworkPacket.PACKET_TYPE_IDENTITY)) {
            Log.e("KDE/LanLinkProvider", "Expecting an UDP identity packet");
            return;
        } else {
            String myId = DeviceHelper.getDeviceId(context);
            if (deviceId.equals(myId)) {
                //Ignore my own broadcast
                return;
            }
        }

        Log.i("KDE/LanLinkProvider", "Broadcast identity packet received from " + identityPacket.getString("deviceName"));

        int tcpPort = identityPacket.getInt("tcpPort", MIN_PORT);

        SocketFactory socketFactory = SocketFactory.getDefault();
        Socket socket = socketFactory.createSocket(address, tcpPort);
        configureSocket(socket);

        OutputStream out = socket.getOutputStream();
        NetworkPacket myIdentity = NetworkPacket.createIdentityPacket(context);
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

        Log.i("KDE/LanLinkProvider", "Starting SSL handshake with " + identityPacket.getString("deviceName") + " trusted:" + isDeviceTrusted);

        final SSLSocket sslSocket = SslHelper.convertToSslSocket(context, socket, deviceId, isDeviceTrusted, clientMode);
        sslSocket.addHandshakeCompletedListener(event -> {
            String mode = clientMode ? "client" : "server";
            try {
                Certificate certificate = event.getPeerCertificates()[0];
                Log.i("KDE/LanLinkProvider", "Handshake as " + mode + " successful with " + identityPacket.getString("deviceName") + " secured with " + event.getCipherSuite());
                addLink(deviceId, certificate, identityPacket, sslSocket);
            } catch (Exception e) {
                Log.e("KDE/LanLinkProvider", "Handshake as " + mode + " failed with " + identityPacket.getString("deviceName"), e);
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
     * @param deviceId         remote device id
     * @param certificate      remote device certificate
     * @param identityPacket   identity packet with the remote device's device name, type, protocol version, etc.
     * @param socket           a new Socket, which should be used to send and receive packets from the remote device
     * @throws IOException if an exception is thrown by {@link LanLink#reset(SSLSocket)}
     */
    private void addLink(String deviceId, Certificate certificate, final NetworkPacket identityPacket, SSLSocket socket) throws IOException {
        LanLink currentLink = visibleDevices.get(deviceId);
        if (currentLink != null) {
            //Update old link
            Log.i("KDE/LanLinkProvider", "Reusing same link for device " + deviceId);
            final Socket oldSocket = currentLink.reset(socket);
        } else {
            Log.i("KDE/LanLinkProvider", "Creating a new link for device " + deviceId);
            //Let's create the link
            LanLink link = new LanLink(context, deviceId, this, socket);
            visibleDevices.put(deviceId, link);
            onConnectionReceived(deviceId, certificate, identityPacket, link);
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
                    onNetworkChange(); // Trigger a UDP broadcast to try to get them to connect to us instead
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

    private void broadcastUdpIdentityPacket() {
        if (System.currentTimeMillis() < lastBroadcast + delayBetweenBroadcasts) {
            Log.i("LanLinkProvider", "broadcastUdpPacket: relax cowboy");
            return;
        }
        lastBroadcast = System.currentTimeMillis();

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

            sendUdpIdentityPacket(ipList);
        });
    }

    @WorkerThread
    public void sendUdpIdentityPacket(List<InetAddress> ipList) {
        if (tcpServer == null || !tcpServer.isBound()) {
            Log.i("LanLinkProvider", "Won't broadcast UDP packet if TCP socket is not ready yet");
            return;
        }

        NetworkPacket identity = NetworkPacket.createIdentityPacket(context);
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

            mdnsDiscovery.startListening();
            mdnsDiscovery.startAnnouncing();

            broadcastUdpIdentityPacket();
        }
    }

    @Override
    public void onNetworkChange() {
        broadcastUdpIdentityPacket();
        mdnsDiscovery.stopListening();
        mdnsDiscovery.startListening();
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
        mdnsDiscovery.stopAnnouncing();
        mdnsDiscovery.stopListening();
    }

    @Override
    public String getName() {
        return "LanLinkProvider";
    }

}
