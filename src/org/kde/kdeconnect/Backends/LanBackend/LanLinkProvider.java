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
import android.support.v4.util.LongSparseArray;
import android.util.Base64;
import android.util.Log;

import org.json.JSONException;
import org.kde.kdeconnect.Backends.BaseLinkProvider;
import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Helpers.DeviceHelper;
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper;
import org.kde.kdeconnect.NetworkPackage;
import org.kde.kdeconnect.UserInterface.CustomDevicesActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.SocketFactory;
import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class LanLinkProvider extends BaseLinkProvider {

    public static final int MIN_VERSION_WITH_SSL_SUPPORT = 6;
    public static final int MIN_VERSION_WITH_NEW_PORT_SUPPORT = 7;

    public static final String KEY_CUSTOM_DEVLIST_PREFERENCE  = "device_list_preference";

    static final Charset UTF8 = Charset.forName("UTF-8");

    private final static int oldPort = 1714;
    private final static int port = 1716;

    private final Context context;

    private final HashMap<String, LanLink> visibleComputers = new HashMap<>();  //Links by device id
    private final LongSparseArray<LanLink> nioLinks = new LongSparseArray<>(); //Links by channel id

    private ServerSocket tcpServer;
    private DatagramSocket udpServer;
    private DatagramSocket udpServerOldPort;

    private boolean running = false;

    // To prevent infinte loop between Android < IceCream because both device can only broadcast identity package but cannot connect via TCP
    private ArrayList<InetAddress> reverseConnectionBlackList = new ArrayList<>();

    public void socketClosed(Socket socket) {
        try {
            final LanLink brokenLink = nioLinks.get(socket.hashCode());
            if (brokenLink != null) {
                nioLinks.remove(socket.hashCode());
                //Log.i("KDE/LanLinkProvider", "nioLinks.size(): " + nioLinks.size() + " (-)");
                try {
                    brokenLink.disconnect();
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e("KDE/LanLinkProvider", "Exception. Already disconnected?");
                }
                //Log.i("KDE/LanLinkProvider", "Disconnected!");
                String deviceId = brokenLink.getDeviceId();
                if (visibleComputers.get(deviceId) == brokenLink) {
                    visibleComputers.remove(deviceId);
                }
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        //Wait a bit before emitting connectionLost, in case the same device re-appears
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                        }
                        connectionLost(brokenLink);

                    }
                }).start();

            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("KDE/LanLinkProvider", "channelInactive exception");
        }
    }

    //They received my UDP broadcast and are connecting to me. The first thing they sned should be their identity.
    public void tcpPackageReceived(Socket socket) throws Exception {
        //Log.e("KDE/LanLinkProvider", "Received a TCP packet from " + ctx.channel().remoteAddress() + ":" + message);

        NetworkPackage networkPackage;
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String message = reader.readLine();
            networkPackage = NetworkPackage.unserialize(message);
            Log.e("TcpListener","Received TCP package: "+networkPackage.serialize());
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        if (networkPackage.getType().equals(NetworkPackage.PACKAGE_TYPE_IDENTITY)) {

            //TODO: Don't accept identity packet when already connected (move this if inside the check we do in the else)
            Log.i("KDE/LanLinkProvider", "Identity package received from a TCP connection from " + networkPackage.getString("deviceName"));
            identityPackageReceived(networkPackage, socket, LanLink.ConnectionStarted.Locally);

        } else {

            LanLink link = nioLinks.get(socket.hashCode());
            if (link== null) {
                Log.e("KDE/LanLinkProvider","Expecting an identity package instead of " + networkPackage.getType());
            } else {
                link.injectNetworkPackage(networkPackage);
            }

        }

    }

    protected void udpPacketReceived(DatagramPacket packet) throws Exception {

        final InetAddress address = packet.getAddress();

        try {

            String message = new String(packet.getData(), UTF8);

            final NetworkPackage identityPackage = NetworkPackage.unserialize(message);
            final String deviceId = identityPackage.getString("deviceId");
            if (!identityPackage.getType().equals(NetworkPackage.PACKAGE_TYPE_IDENTITY)) {
                Log.e("KDE/LanLinkProvider", "Expecting an UDP identity package");
                return;
            } else {
                String myId = DeviceHelper.getDeviceId(context);
                if (deviceId.equals(myId)) {
                    //Log.i("KDE/LanLinkProvider", "Ignoring my own broadcast");
                    return;
                }
            }

            Log.e("AAAAAAAAAAAAAAAAAAAAA","OOOOOOOOOOOOOOOOOOOOOOOOO");
            if (identityPackage.getInt("protocolVersion") >= MIN_VERSION_WITH_NEW_PORT_SUPPORT && identityPackage.getInt("tcpPort") < port) {
                Log.w("KDE/LanLinkProvider", "Ignoring a udp broadcast from an old port because it comes from a device which knows about the new port.");
                return;
            }

            Log.i("KDE/LanLinkProvider", "Broadcast identity package received from " + identityPackage.getString("deviceName"));

            int tcpPort = identityPackage.getInt("tcpPort", port);

            SocketFactory socketFactory = SocketFactory.getDefault();
            Socket socket = socketFactory.createSocket(packet.getAddress(), tcpPort);
            configureSocket(socket);

            OutputStream out = socket.getOutputStream();
            out.write(NetworkPackage.createIdentityPackage(context).serialize().getBytes());
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

    private void identityPackageReceived(final NetworkPackage identityPackage, final Socket channel, final LanLink.ConnectionStarted connectionStarted) {

        try {
            Log.e("IDENTITITYYYYY", identityPackage.serialize());
        } catch (JSONException e) {
            e.printStackTrace();
        }


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
                final boolean isDeviceTrusted = preferences.getBoolean(deviceId, false);

                Log.i("KDE/LanLinkProvider","Starting SSL handshake with " + identityPackage.getString("deviceName"));

                SSLSocketFactory sslsocketFactory = SslHelper.getSslContext(context, deviceId, isDeviceTrusted).getSocketFactory();
                final SSLSocket sslsocket = (SSLSocket)sslsocketFactory.createSocket(channel, channel.getInetAddress().getHostAddress(), channel.getPort(), true);
                SslHelper.configureSslSocket(sslsocket, isDeviceTrusted, clientMode);
                configureSocket(sslsocket);
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
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            sslsocket.startHandshake();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    }
                }).start();
            } else {
                addLink(identityPackage, channel, connectionStarted);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void addLink(final NetworkPackage identityPackage, Socket channel, LanLink.ConnectionStarted connectionOrigin) throws IOException {

        try {
            Log.e("addLink", identityPackage.serialize());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        String deviceId = identityPackage.getString("deviceId");
        LanLink currentLink = visibleComputers.get(deviceId);
        if (currentLink != null) {
            //Update old link
            Log.i("KDE/LanLinkProvider", "Reusing same link for device " + deviceId);
            final Socket oldChannel = currentLink.reset(channel, connectionOrigin, this);
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    nioLinks.remove(oldChannel.hashCode());
                    //Log.e("KDE/LanLinkProvider", "Forgetting about channel " + channel.hashCode());
                }
            }, 500); //Stop accepting messages from the old channel after 500ms
            nioLinks.put(channel.hashCode(), currentLink);
            //Log.e("KDE/LanLinkProvider", "Replacing channel. old: "+ oldChannel.hashCode() + " - new: "+ channel.hashCode());
        } else {

            Log.e("addLink", "create link");
            //Let's create the link
            LanLink link = new LanLink(context, deviceId, this, channel, connectionOrigin);
            nioLinks.put(channel.hashCode(), link);
            visibleComputers.put(deviceId, link);
            connectionAccepted(identityPackage, link);
        }
    }



    public LanLinkProvider(Context context) {

        this.context = context;


        // Due to certificate request from SSL server to client, the certificate request message from device with latest android version to device with
        // old android version causes a FATAL ALERT message stating that incorrect certificate request
        // Server is disabled on these devices and using a reverse connection strategy. This works well for connection of these devices with kde
        // and newer android versions. Although devices with android version less than ICS cannot connect to other devices who also have android version less
        // than ICS because server is disabled on both
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            Log.w("KDE/LanLinkProvider","Not starting a TCP server because it's not supported on Android < 14. Operating only as client.");
            return;
        }

    }


    private void setupUdpListener() {

        try {
            udpServer = new DatagramSocket(port);
            udpServer.setReuseAddress(true);
            udpServer.setBroadcast(true);
        } catch (SocketException e) {
            Log.e("LanLinkProvider", "Error creating udp server");
            e.printStackTrace();
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (running) {
                    final int bufferSize = 1024 * 512;
                    byte[] data = new byte[bufferSize];
                    DatagramPacket packet = new DatagramPacket(data, bufferSize);
                    try {
                        udpServer.receive(packet);
                        udpPacketReceived(packet);
                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.e("LanLinkProvider", "UdpReceive exception");
                    }
                }
                Log.e("UdpListener","Stopping UDP listener");
            }
        }).start();
    }

    private void setupTcpListener() {

        try {
            tcpServer = LanLink.openUnsecureSocketOnFreePort(port);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (running) {
                        try {
                            Log.e("ServerSocket","Waiting...");
                            Socket socket = tcpServer.accept();
                            Log.e("ServerSocket","Got a socket!");
                            configureSocket(socket);
                            tcpPackageReceived(socket);
                        } catch (Exception e) {
                            e.printStackTrace();
                            Log.e("LanLinkProvider", "TcpReceive exception");
                        }
                    }
                    Log.e("TcpListener", "Stopping TCP listener");
                }
            }).start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void broadcastUdpPackage() {

        new Thread(new Runnable() {
            @Override
            public void run() {

                String deviceListPrefs = PreferenceManager.getDefaultSharedPreferences(context).getString(KEY_CUSTOM_DEVLIST_PREFERENCE, "");
                ArrayList<String> iplist = new ArrayList<>();
                if (!deviceListPrefs.isEmpty()) {
                    iplist = CustomDevicesActivity.deserializeIpList(deviceListPrefs);
                }
                iplist.add("255.255.255.255"); //Default: broadcast.

                NetworkPackage identity = NetworkPackage.createIdentityPackage(context);
                identity.set("tcpPort", port);
                DatagramSocket socket = null;
                byte[] bytes = null;
                try {
                    socket = new DatagramSocket();
                    socket.setReuseAddress(true);
                    socket.setBroadcast(true);
                    bytes = identity.serialize().getBytes(UTF8);
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e("KDE/LanLinkProvider","Failed to create DatagramSocket");
                }

                if (bytes != null) {
                    //Log.e("KDE/LanLinkProvider","Sending packet to "+iplist.size()+" ips");
                    for (String ipstr : iplist) {
                        try {
                            InetAddress client = InetAddress.getByName(ipstr);
                            socket.send(new DatagramPacket(bytes, bytes.length, client, port));
                            //socket.send(new DatagramPacket(bytes, bytes.length, client, oldPort));
                            Log.i("KDE/LanLinkProvider","Udp identity package sent to address "+client);
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

        Log.i("KDE/LanLinkProvider", "onStart");

        if (!running) {
            running = true;
            setupUdpListener();
            setupTcpListener();
            broadcastUdpPackage();
        }

    }

    @Override
    public void onNetworkChange() {
        broadcastUdpPackage();
    }

    @Override
    public void onStop() {
        Log.i("KDE/LanLinkProvider", "onStop");

        running = false;
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

    }

    @Override
    public String getName() {
        return "LanLinkProvider";
    }




}
