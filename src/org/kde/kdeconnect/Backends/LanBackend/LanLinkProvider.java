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
import android.preference.PreferenceManager;
import android.support.v4.util.LongSparseArray;
import android.util.Log;

import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.future.IoFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.LineDelimiter;
import org.apache.mina.filter.codec.textline.TextLineCodecFactory;
import org.apache.mina.transport.socket.nio.NioDatagramAcceptor;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.kde.kdeconnect.Backends.BaseLink;
import org.kde.kdeconnect.Backends.BaseLinkProvider;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Helpers.DeviceHelper;
import org.kde.kdeconnect.NetworkPackage;
import org.kde.kdeconnect.UserInterface.CustomDevicesActivity;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

public class LanLinkProvider extends BaseLinkProvider {

    public static final String KEY_CUSTOM_DEVLIST_PREFERENCE  = "device_list_preference";
    private final static int port = 1714;

    private final Context context;
    private final HashMap<String, LanLink> visibleComputers = new HashMap<>();
    private final LongSparseArray<LanLink> nioSessions = new LongSparseArray<>();
    private final LongSparseArray<NioSocketConnector> nioConnectors = new LongSparseArray<>();

    private NioSocketAcceptor tcpAcceptor = null;
    private NioDatagramAcceptor udpAcceptor = null;

    private final IoHandler tcpHandler = new IoHandlerAdapter() {
        @Override
        public void sessionClosed(IoSession session) throws Exception {
            try {
                long id = session.getId();
                final LanLink brokenLink = nioSessions.get(id);
                NioSocketConnector connector = nioConnectors.get(id);
                if (connector != null) {
                    connector.dispose();
                    nioConnectors.remove(id);
                }
                if (brokenLink != null) {
                    nioSessions.remove(id);
                    //Log.i("KDE/LanLinkProvider", "nioSessions.size(): " + nioSessions.size() + " (-)");
                    try {
                        brokenLink.closeSocket();
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
                            //Wait a bit before emiting connectionLost, in case the same device re-appears
                            try {
                                Thread.sleep(200);
                            } catch (InterruptedException e) {
                            }
                            connectionLost(brokenLink);

                        }
                    }).start();

                }
            } catch (Exception e) { //If we don't catch it here, Mina will swallow it :/
                e.printStackTrace();
                Log.e("KDE/LanLinkProvider", "sessionClosed exception");
            }
        }

        @Override
        public void messageReceived(IoSession session, Object message) throws Exception {
            super.messageReceived(session, message);

            //Log.e("LanLinkProvider","Incoming package, address: "+session.getRemoteAddress()).toString());
            //Log.e("LanLinkProvider","Received:"+message);

            String theMessage = (String) message;
            if (theMessage.isEmpty()) {
                Log.w("KDE/LanLinkProvider","Empty package received");
                return;
            }

            NetworkPackage np = NetworkPackage.unserialize(theMessage);

            if (np.getType().equals(NetworkPackage.PACKAGE_TYPE_IDENTITY)) {
                String myId = DeviceHelper.getDeviceId(context);
                if (np.getString("deviceId").equals(myId)) {
                    return;
                }

                //Log.i("KDE/LanLinkProvider", "Identity package received from " + np.getString("deviceName"));

                LanLink link = new LanLink(session, np.getString("deviceId"), LanLinkProvider.this, BaseLink.ConnectionStarted.Locally);
                nioSessions.put(session.getId(),link);
                //Log.e("KDE/LanLinkProvider","nioSessions.size(): " + nioSessions.size());
                addLink(np, link);
            } else {
                LanLink prevLink = nioSessions.get(session.getId());
                if (prevLink == null) {
                    Log.e("KDE/LanLinkProvider","Expecting an identity package (A)");
                } else {
                    prevLink.injectNetworkPackage(np);
                }
            }

        }
    };

    private final IoHandler udpHandler = new IoHandlerAdapter() {
        @Override
        public void messageReceived(IoSession udpSession, Object message) throws Exception {
            super.messageReceived(udpSession, message);

            //Log.e("LanLinkProvider", "Udp message received (" + message.getClass() + ") " + message.toString());

            try {
                //We should receive a string thanks to the TextLineCodecFactory filter
                String theMessage = (String) message;
                final NetworkPackage identityPackage = NetworkPackage.unserialize(theMessage);

                if (!identityPackage.getType().equals(NetworkPackage.PACKAGE_TYPE_IDENTITY)) {
                    Log.e("KDE/LanLinkProvider", "Expecting an identity package (B)");
                    return;
                } else {
                    String myId = DeviceHelper.getDeviceId(context);
                    if (identityPackage.getString("deviceId").equals(myId)) {
                        return;
                    }
                }

                //Log.i("KDE/LanLinkProvider", "Identity package received, creating link");

                final InetSocketAddress address = (InetSocketAddress) udpSession.getRemoteAddress();

                final NioSocketConnector connector = new NioSocketConnector();
                connector.setHandler(tcpHandler);
                connector.getSessionConfig().setKeepAlive(true);
                //TextLineCodecFactory will buffer incoming data and emit a message very time it finds a \n
                TextLineCodecFactory textLineFactory = new TextLineCodecFactory(Charset.defaultCharset(), LineDelimiter.UNIX, LineDelimiter.UNIX);
                textLineFactory.setDecoderMaxLineLength(512*1024); //Allow to receive up to 512kb of data
                connector.getFilterChain().addLast("codec", new ProtocolCodecFilter(textLineFactory));

                int tcpPort = identityPackage.getInt("tcpPort", port);
                final ConnectFuture future = connector.connect(new InetSocketAddress(address.getAddress(), tcpPort));
                future.addListener(new IoFutureListener<IoFuture>() {

                    @Override
                    public void operationComplete(IoFuture ioFuture) {
                        try {
                            future.removeListener(this);
                            final IoSession session = ioFuture.getSession();
                            Log.i("KDE/LanLinkProvider", "Connection successful: " + session.isConnected());

                            final LanLink link = new LanLink(session, identityPackage.getString("deviceId"), LanLinkProvider.this, BaseLink.ConnectionStarted.Remotely);
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    NetworkPackage np2 = NetworkPackage.createIdentityPackage(context);
                                    link.sendPackage(np2,new Device.SendPackageStatusCallback() {
                                        @Override
                                        protected void onSuccess() {
                                            nioSessions.put(session.getId(), link);
                                            nioConnectors.put(session.getId(), connector);
                                            //Log.e("KDE/LanLinkProvider","nioSessions.size(): " + nioSessions.size());
                                            addLink(identityPackage, link);
                                        }

                                        @Override
                                        protected void onFailure(Throwable e) {
                                            Log.e("KDE/LanLinkProvider", "Connection failed: could not send identity package back");
                                        }
                                    });

                                }
                            }).start();
                        } catch (Exception e) { //If we don't catch it here, Mina will swallow it :/
                            e.printStackTrace();
                            Log.e("KDE/LanLinkProvider", "sessionClosed exception");
                        }
                    }
                });

            } catch (Exception e) {
                Log.e("KDE/LanLinkProvider","Exception receiving udp package!!");
                e.printStackTrace();
            }

        }
    };

    private void addLink(NetworkPackage identityPackage, LanLink link) {
        String deviceId = identityPackage.getString("deviceId");
        Log.i("KDE/LanLinkProvider","addLink to "+deviceId);
        LanLink oldLink = visibleComputers.get(deviceId);
        if (oldLink == link) {
            Log.e("KDE/LanLinkProvider", "oldLink == link. This should not happen!");
            return;
        }
        visibleComputers.put(deviceId, link);
        connectionAccepted(identityPackage, link);
        if (oldLink != null) {
            Log.i("KDE/LanLinkProvider","Removing old connection to same device");
            oldLink.closeSocket();
            connectionLost(oldLink);
        }
    }

    public LanLinkProvider(Context context) {

        this.context = context;

        //This handles the case when I'm the new device in the network and somebody answers my introduction package
        tcpAcceptor = new NioSocketAcceptor();
        tcpAcceptor.setHandler(tcpHandler);
        tcpAcceptor.getSessionConfig().setKeepAlive(true);
        tcpAcceptor.getSessionConfig().setReuseAddress(true);
        //TextLineCodecFactory will buffer incoming data and emit a message very time it finds a \n
        TextLineCodecFactory textLineFactory = new TextLineCodecFactory(Charset.defaultCharset(), LineDelimiter.UNIX, LineDelimiter.UNIX);
        textLineFactory.setDecoderMaxLineLength(512*1024); //Allow to receive up to 512kb of data
        tcpAcceptor.getFilterChain().addLast("codec", new ProtocolCodecFilter(textLineFactory));

        udpAcceptor = new NioDatagramAcceptor();
        udpAcceptor.getSessionConfig().setReuseAddress(true); //Share port if existing
        //TextLineCodecFactory will buffer incoming data and emit a message very time it finds a \n
        //This one will have the default MaxLineLength of 1KB
        udpAcceptor.getFilterChain().addLast("codec",
                new ProtocolCodecFilter(
                        new TextLineCodecFactory(Charset.defaultCharset(), LineDelimiter.UNIX, LineDelimiter.UNIX)
                )
        );

    }

    @Override
    public void onStart() {

        //This handles the case when I'm the existing device in the network and receive a "hello" UDP package

        Set<SocketAddress> addresses = udpAcceptor.getLocalAddresses();
        for (SocketAddress address : addresses) {
            Log.i("KDE/LanLinkProvider", "UDP unbind old address");
            udpAcceptor.unbind(address);
        }

        //Log.i("KDE/LanLinkProvider", "UDP Bind.");
        udpAcceptor.setHandler(udpHandler);

        try {
            udpAcceptor.bind(new InetSocketAddress(port));
        } catch(Exception e) {
            Log.e("KDE/LanLinkProvider", "Error: Could not bind udp socket");
            e.printStackTrace();
        }

        boolean success = false;
        int tcpPort = port;
        while(!success) {
            try {
                tcpAcceptor.bind(new InetSocketAddress(tcpPort));
                success = true;
            } catch(Exception e) {
                tcpPort++;
            }
        }

        Log.i("KDE/LanLinkProvider","Using tcpPort "+tcpPort);

        //I'm on a new network, let's be polite and introduce myself
        final int finalTcpPort = tcpPort;
        new Thread(new Runnable() {
            @Override
            public void run() {

                String deviceListPrefs = PreferenceManager.getDefaultSharedPreferences(context).getString(
                        KEY_CUSTOM_DEVLIST_PREFERENCE, "");
                ArrayList<String> iplist = new ArrayList<>();
                if (!deviceListPrefs.isEmpty()) {
                    iplist = CustomDevicesActivity.deserializeIpList(deviceListPrefs);
                }
                iplist.add("255.255.255.255"); //Default: broadcast.

                NetworkPackage identity = NetworkPackage.createIdentityPackage(context);
                identity.set("tcpPort", finalTcpPort);
                DatagramSocket socket = null;
                byte[] bytes = null;
                try {
                    socket = new DatagramSocket();
                    socket.setReuseAddress(true);
                    socket.setBroadcast(true);
                    bytes = identity.serialize().getBytes("UTF-8");
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e("KDE/LanLinkProvider","Failed to create DatagramSocket");
                }

                if (bytes != null) {
                    //Log.e("KDE/LanLinkProvider","Sending packet to "+iplist.size()+" ips");
                    for (String ipstr : iplist) {
                        try {
                            InetAddress client = InetAddress.getByName(ipstr);
                            DatagramPacket packet = new DatagramPacket(bytes, bytes.length, client, port);
                            socket.send(packet);
                            //Log.i("KDE/LanLinkProvider","Udp identity package sent to address "+packet.getAddress());
                        } catch (Exception e) {
                            e.printStackTrace();
                            Log.e("KDE/LanLinkProvider", "Sending udp identity package failed. Invalid address? (" + ipstr + ")");
                        }
                    }
                }

                socket.close();

            }
        }).start();
    }

    @Override
    public void onNetworkChange() {
        //Log.e("KDE/LanLinkProvider","onNetworkChange");

        //FilesHelper.LogOpenFileCount();

        //Keep existing connections open while unbinding the socket
        tcpAcceptor.setCloseOnDeactivation(false);
        onStop();
        tcpAcceptor.setCloseOnDeactivation(true);

        //FilesHelper.LogOpenFileCount();

        onStart();

        //FilesHelper.LogOpenFileCount();
    }

    @Override
    public void onStop() {
        udpAcceptor.unbind();
        tcpAcceptor.unbind();
    }

    @Override
    public String getName() {
        return "LanLinkProvider";
    }



}
