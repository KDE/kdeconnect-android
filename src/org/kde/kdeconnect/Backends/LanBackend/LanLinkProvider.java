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
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

import org.kde.kdeconnect.Backends.BaseLinkProvider;
import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Helpers.DeviceHelper;
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper;
import org.kde.kdeconnect.Helpers.StringsHelper;
import org.kde.kdeconnect.NetworkPackage;
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
import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLSocket;

public class LanLinkProvider extends BaseLinkProvider implements LanLink.LinkDisconnectedCallback {

    public static final int MIN_VERSION_WITH_SSL_SUPPORT = 6;
    public static final int MIN_VERSION_WITH_NEW_PORT_SUPPORT = 7;

    final static int MIN_PORT_LEGACY = 1714;
    final static int MIN_PORT = 1716;
    final static int MAX_PORT = 1764;
    final static int PAYLOAD_TRANSFER_MIN_PORT = 1739;

    private final Context context;

    private final HashMap<String, LanLink> visibleComputers = new HashMap<>();  //Links by device id

    private ServerSocket tcpServer;
    private DatagramSocket udpServer;
    private DatagramSocket udpServerOldPort;

    private boolean listening = false;

    // To prevent infinte loop between Android < IceCream because both device can only broadcast identity package but cannot connect via TCP
    private ArrayList<InetAddress> reverseConnectionBlackList = new ArrayList<>();

    @Override // SocketClosedCallback
    public void linkDisconnected(LanLink brokenLink) {
        String deviceId = brokenLink.getDeviceId();
        visibleComputers.remove(deviceId);
        connectionLost(brokenLink);
    }

    //They received my UDP broadcast and are connecting to me. The first thing they sned should be their identity.
    public void tcpPackageReceived(Socket socket) throws Exception {

        NetworkPackage networkPackage;
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String message = reader.readLine();
            networkPackage = NetworkPackage.unserialize(message);
            //Log.e("TcpListener","Received TCP package: "+networkPackage.serialize());
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        if (!networkPackage.getType().equals(NetworkPackage.PACKAGE_TYPE_IDENTITY)) {
            Log.e("KDE/LanLinkProvider", "Expecting an identity package instead of " + networkPackage.getType());
            return;
        }

        Log.i("KDE/LanLinkProvider", "Identity package received from a TCP connection from " + networkPackage.getString("deviceName"));
        identityPackageReceived(networkPackage, socket, LanLink.ConnectionStarted.Locally);
    }

    //I've received their broadcast and should connect to their TCP socket and send my identity.
    protected void udpPacketReceived(DatagramPacket packet) throws Exception {

        final InetAddress address = packet.getAddress();

        try {

            String message = new String(packet.getData(), StringsHelper.UTF8);
            final NetworkPackage identityPackage = NetworkPackage.unserialize(message);
            final String deviceId = identityPackage.getString("deviceId");
            if (!identityPackage.getType().equals(NetworkPackage.PACKAGE_TYPE_IDENTITY)) {
                Log.e("KDE/LanLinkProvider", "Expecting an UDP identity package");
                return;
            } else {
                String myId = DeviceHelper.getDeviceId(context);
                if (deviceId.equals(myId)) {
                    //Ignore my own broadcast
                    return;
                }
            }

            if (identityPackage.getInt("protocolVersion") >= MIN_VERSION_WITH_NEW_PORT_SUPPORT && identityPackage.getInt("tcpPort") < MIN_PORT) {
                Log.w("KDE/LanLinkProvider", "Ignoring a udp broadcast from legacy port because it comes from a device which knows about the new port.");
                return;
            }

            Log.i("KDE/LanLinkProvider", "Broadcast identity package received from " + identityPackage.getString("deviceName"));

            int tcpPort = identityPackage.getInt("tcpPort", MIN_PORT);

            SocketFactory socketFactory = SocketFactory.getDefault();
            Socket socket = socketFactory.createSocket(address, tcpPort);
            configureSocket(socket);

            OutputStream out = socket.getOutputStream();
            NetworkPackage myIdentity = NetworkPackage.createIdentityPackage(context);
            out.write(myIdentity.serialize().getBytes());
            out.flush();

            identityPackageReceived(identityPackage, socket, LanLink.ConnectionStarted.Remotely);

        } catch (Exception e) {
            Log.e("KDE/LanLinkProvider", "Cannot connect to " + address);
            e.printStackTrace();
            if (!reverseConnectionBlackList.contains(address)) {
                Log.w("KDE/LanLinkProvider","Blacklisting "+address);
                reverseConnectionBlackList.add(address);
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        reverseConnectionBlackList.remove(address);
                    }
                }, 5*1000);

                // Try to cause a reverse connection
                onNetworkChange();
            }
        }
    }

    private void configureSocket(Socket socket) {
        try {
            socket.setKeepAlive(true);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    private void identityPackageReceived(final NetworkPackage identityPackage, final Socket socket, final LanLink.ConnectionStarted connectionStarted) {

        String myId = DeviceHelper.getDeviceId(context);
        final String deviceId = identityPackage.getString("deviceId");
        if (deviceId.equals(myId)) {
            Log.e("KDE/LanLinkProvider", "Somehow I'm connected to myself, ignoring. This should not happen.");
            return;
        }

        // If I'm the TCP server I will be the SSL client and viceversa.
        final boolean clientMode = (connectionStarted == LanLink.ConnectionStarted.Locally);

        // Add ssl handler if device uses new protocol
        try {
            if (identityPackage.getInt("protocolVersion") >= MIN_VERSION_WITH_SSL_SUPPORT) {

                SharedPreferences preferences = context.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE);
                boolean isDeviceTrusted = preferences.getBoolean(deviceId, false);

                Log.i("KDE/LanLinkProvider","Starting SSL handshake with " + identityPackage.getString("deviceName") + " trusted:"+isDeviceTrusted);

                final SSLSocket sslsocket = SslHelper.convertToSslSocket(context, socket, deviceId, isDeviceTrusted, clientMode);
                sslsocket.addHandshakeCompletedListener(new HandshakeCompletedListener() {
                    @Override
                    public void handshakeCompleted(HandshakeCompletedEvent event) {
                        String mode = clientMode? "client" : "server";
                        try {
                            Certificate certificate = event.getPeerCertificates()[0];
                            identityPackage.set("certificate", Base64.encodeToString(certificate.getEncoded(), 0));
                            Log.i("KDE/LanLinkProvider","Handshake as " + mode + " successful with " + identityPackage.getString("deviceName") + " secured with " + event.getCipherSuite());
                            addLink(identityPackage, sslsocket, connectionStarted);
                        } catch (Exception e) {
                            Log.e("KDE/LanLinkProvider","Handshake as " + mode + " failed with " + identityPackage.getString("deviceName"));
                            e.printStackTrace();
                            BackgroundService.RunCommand(context, new BackgroundService.InstanceCallback() {
                                @Override
                                public void onServiceStart(BackgroundService service) {
                                    Device device = service.getDevice(deviceId);
                                    if (device == null) return;
                                    device.unpair();
                                }
                            });
                        }
                    }
                });
                //Handshake is blocking, so do it on another thread and free this thread to keep receiving new connection
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            sslsocket.startHandshake();
                        } catch (Exception e) {
                            Log.e("KDE/LanLinkProvider","Handshake failed with " + identityPackage.getString("deviceName"));
                            e.printStackTrace();
                            BackgroundService.RunCommand(context, new BackgroundService.InstanceCallback() {
                                @Override
                                public void onServiceStart(BackgroundService service) {
                                    Device device = service.getDevice(deviceId);
                                    if (device == null) return;
                                    device.unpair();
                                }
                            });
                        }
                    }
                }).start();
            } else {
                addLink(identityPackage, socket, connectionStarted);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void addLink(final NetworkPackage identityPackage, Socket socket, LanLink.ConnectionStarted connectionOrigin) throws IOException {

        String deviceId = identityPackage.getString("deviceId");
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
            connectionAccepted(identityPackage, link);
        }
    }

    public LanLinkProvider(Context context) {
        this.context = context;
    }

    private DatagramSocket setupUdpListener(int udpPort) {
        final DatagramSocket server;
        try {
            server = new DatagramSocket(udpPort);
            server.setReuseAddress(true);
            server.setBroadcast(true);
        } catch (SocketException e) {
            Log.e("LanLinkProvider", "Error creating udp server");
            e.printStackTrace();
            return null;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (listening) {
                    final int bufferSize = 1024 * 512;
                    byte[] data = new byte[bufferSize];
                    DatagramPacket packet = new DatagramPacket(data, bufferSize);
                    try {
                        server.receive(packet);
                        udpPacketReceived(packet);
                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.e("LanLinkProvider", "UdpReceive exception");
                    }
                }
                Log.w("UdpListener","Stopping UDP listener");
            }
        }).start();
        return server;
    }

    private void setupTcpListener() {

        try {
            tcpServer = openServerSocketOnFreePort(MIN_PORT);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (listening) {
                        try {
                            Socket socket = tcpServer.accept();
                            configureSocket(socket);
                            tcpPackageReceived(socket);
                        } catch (Exception e) {
                            e.printStackTrace();
                            Log.e("LanLinkProvider", "TcpReceive exception");
                        }
                    }
                    Log.w("TcpListener", "Stopping TCP listener");
                }
            }).start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static ServerSocket openServerSocketOnFreePort(int minPort) throws IOException {
        int tcpPort = minPort;
        while(tcpPort < MAX_PORT) {
            try {
                ServerSocket candidateServer = new ServerSocket();
                candidateServer.bind(new InetSocketAddress(tcpPort));
                Log.i("KDE/LanLink", "Using port "+tcpPort);
                return candidateServer;
            } catch(IOException e) {
                tcpPort++;
            }
        }
        Log.e("KDE/LanLink", "No ports available");
        throw new IOException("No ports available");
    }

    void broadcastUdpPackage() {

        new Thread(new Runnable() {
            @Override
            public void run() {

                String deviceListPrefs = PreferenceManager.getDefaultSharedPreferences(context).getString(CustomDevicesActivity.KEY_CUSTOM_DEVLIST_PREFERENCE, "");
                ArrayList<String> iplist = new ArrayList<>();
                if (!deviceListPrefs.isEmpty()) {
                    iplist = CustomDevicesActivity.deserializeIpList(deviceListPrefs);
                }
                iplist.add("255.255.255.255"); //Default: broadcast.

                NetworkPackage identity = NetworkPackage.createIdentityPackage(context);
                identity.set("tcpPort", MIN_PORT);
                DatagramSocket socket = null;
                byte[] bytes = null;
                try {
                    socket = new DatagramSocket();
                    socket.setReuseAddress(true);
                    socket.setBroadcast(true);
                    bytes = identity.serialize().getBytes(StringsHelper.UTF8);
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e("KDE/LanLinkProvider","Failed to create DatagramSocket");
                }

                if (bytes != null) {
                    //Log.e("KDE/LanLinkProvider","Sending packet to "+iplist.size()+" ips");
                    for (String ipstr : iplist) {
                        try {
                            InetAddress client = InetAddress.getByName(ipstr);
                            socket.send(new DatagramPacket(bytes, bytes.length, client, MIN_PORT));
                            socket.send(new DatagramPacket(bytes, bytes.length, client, MIN_PORT_LEGACY));
                            //Log.i("KDE/LanLinkProvider","Udp identity package sent to address "+client);
                        } catch (Exception e) {
                            e.printStackTrace();
                            Log.e("KDE/LanLinkProvider", "Sending udp identity package failed. Invalid address? (" + ipstr + ")");
                        }
                    }
                }

                if (socket != null) {
                    socket.close();
                }

            }
        }).start();
    }
    @Override
    public void onStart() {
        //Log.i("KDE/LanLinkProvider", "onStart");
        if (!listening) {

            listening = true;

            udpServer = setupUdpListener(MIN_PORT);
            udpServerOldPort = setupUdpListener(MIN_PORT_LEGACY);

            // Due to certificate request from SSL server to client, the certificate request message from device with latest android version to device with
            // old android version causes a FATAL ALERT message stating that incorrect certificate request
            // Server is disabled on these devices and using a reverse connection strategy. This works well for connection of these devices with kde
            // and newer android versions. Although devices with android version less than ICS cannot connect to other devices who also have android version less
            // than ICS because server is disabled on both
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                Log.w("KDE/LanLinkProvider","Not starting a TCP server because it's not supported on Android < 14. Operating only as client.");
            } else {
                setupTcpListener();
            }

            broadcastUdpPackage();
        }
    }

    @Override
    public void onNetworkChange() {
        broadcastUdpPackage();
    }

    @Override
    public void onStop() {
        //Log.i("KDE/LanLinkProvider", "onStop");
        listening = false;
        try {
            tcpServer.close();
        } catch (Exception e){
            e.printStackTrace();
        }
        try {
            udpServer.close();
        } catch (Exception e){
            e.printStackTrace();
        }
        try {
            udpServerOldPort.close();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public String getName() {
        return "LanLinkProvider";
    }

}
