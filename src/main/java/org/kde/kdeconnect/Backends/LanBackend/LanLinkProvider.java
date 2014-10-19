package org.kde.kdeconnect.Backends.LanBackend;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.support.v4.util.LongSparseArray;

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
import org.kde.kdeconnect.Backends.BaseLinkProvider;
import org.kde.kdeconnect.NetworkPackage;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.HashMap;

public class LanLinkProvider extends BaseLinkProvider {

    private final static int port = 1714;

    private final Context context;
    private final HashMap<String, LanLink> visibleComputers = new HashMap<String, LanLink>();
    private final LongSparseArray<LanLink> nioSessions = new LongSparseArray<LanLink>();

    private NioSocketAcceptor tcpAcceptor = null;
    private NioDatagramAcceptor udpAcceptor = null;

    private final IoHandler tcpHandler = new IoHandlerAdapter() {
        @Override
        public void sessionClosed(IoSession session) throws Exception {

            LanLink brokenLink = nioSessions.get(session.getId());
            if (brokenLink != null) {
                nioSessions.remove(session.getId());
                connectionLost(brokenLink);
                brokenLink.disconnect();
                String deviceId = brokenLink.getDeviceId();
                if (visibleComputers.get(deviceId) == brokenLink) {
                    visibleComputers.remove(deviceId);
                }
            }

        }

        @Override
        public void messageReceived(IoSession session, Object message) throws Exception {
            super.messageReceived(session, message);

            //Log.e("LanLinkProvider","Incoming package, address: "+session.getRemoteAddress()).toString());
            //Log.e("LanLinkProvider","Received:"+message);

            String theMessage = (String) message;
            if (theMessage.isEmpty()) {
                Log.e("LanLinkProvider","Empty package received");
                return;
            }

            NetworkPackage np = NetworkPackage.unserialize(theMessage);

            if (np.getType().equals(NetworkPackage.PACKAGE_TYPE_IDENTITY)) {

                String myId = NetworkPackage.createIdentityPackage(context).getString("deviceId");
                if (np.getString("deviceId").equals(myId)) {
                    return;
                }

                //Log.e("LanLinkProvider", "Identity package received from "+np.getString("deviceName"));

                LanLink link = new LanLink(session, np.getString("deviceId"), LanLinkProvider.this);
                nioSessions.put(session.getId(),link);
                addLink(np, link);
            } else {
                LanLink prevLink = nioSessions.get(session.getId());
                if (prevLink == null) {
                    Log.e("LanLinkProvider","2 Expecting an identity package");
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
                    Log.e("LanLinkProvider", "1 Expecting an identity package");
                    return;
                } else {
                    String myId = NetworkPackage.createIdentityPackage(context).getString("deviceId");
                    if (identityPackage.getString("deviceId").equals(myId)) {
                        return;
                    }
                }

                Log.i("LanLinkProvider", "Identity package received, creating link");

                final InetSocketAddress address = (InetSocketAddress) udpSession.getRemoteAddress();

                final NioSocketConnector connector = new NioSocketConnector();
                connector.setHandler(tcpHandler);
                //TextLineCodecFactory will split incoming data delimited by the given string
                connector.getFilterChain().addLast("codec",
                        new ProtocolCodecFilter(
                                new TextLineCodecFactory(Charset.defaultCharset(), LineDelimiter.UNIX, LineDelimiter.UNIX)
                        )
                );
                connector.getSessionConfig().setKeepAlive(true);

                int tcpPort = identityPackage.getInt("tcpPort",port);
                ConnectFuture future = connector.connect(new InetSocketAddress(address.getAddress(), tcpPort));
                future.addListener(new IoFutureListener<IoFuture>() {
                    @Override
                    public void operationComplete(IoFuture ioFuture) {
                        final IoSession session = ioFuture.getSession();

                        final LanLink link = new LanLink(session, identityPackage.getString("deviceId"), LanLinkProvider.this);

                        Log.i("LanLinkProvider", "Connection successful: " + session.isConnected());

                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                NetworkPackage np2 = NetworkPackage.createIdentityPackage(context);
                                link.sendPackage(np2);

                                nioSessions.put(session.getId(), link);
                                addLink(identityPackage, link);
                            }
                        }).start();

                    }
                });

            } catch (Exception e) {
                Log.e("LanLinkProvider","Exception receiving udp package!!");
                e.printStackTrace();
            }

        }
    };

    private void addLink(NetworkPackage identityPackage, LanLink link) {
        String deviceId = identityPackage.getString("deviceId");
        Log.i("LanLinkProvider","addLink to "+deviceId);
        LanLink oldLink = visibleComputers.get(deviceId);
        if (oldLink == link) {
            Log.e("KDEConnect", "LanLinkProvider: oldLink == link. This should not happen!");
            return;
        }
        visibleComputers.put(deviceId, link);
        connectionAccepted(identityPackage, link);
        if (oldLink != null) {
            Log.i("LanLinkProvider","Removing old connection to same device");
            oldLink.disconnect();
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
        tcpAcceptor.setCloseOnDeactivation(false);
        //TextLineCodecFactory will split incoming data delimited by the given string
        tcpAcceptor.getFilterChain().addLast("codec",
                new ProtocolCodecFilter(
                        new TextLineCodecFactory(Charset.defaultCharset(), LineDelimiter.UNIX, LineDelimiter.UNIX)
                )
        );


        udpAcceptor = new NioDatagramAcceptor();
        udpAcceptor.getSessionConfig().setReuseAddress(true);        //Share port if existing
        //TextLineCodecFactory will split incoming data delimited by the given string
        udpAcceptor.getFilterChain().addLast("codec",
                new ProtocolCodecFilter(
                        new TextLineCodecFactory(Charset.defaultCharset(), LineDelimiter.UNIX, LineDelimiter.UNIX)
                )
        );

    }

    @Override
    public void onStart() {

        //This handles the case when I'm the existing device in the network and receive a "hello" UDP package

        udpAcceptor.setHandler(udpHandler);

        try {
            udpAcceptor.bind(new InetSocketAddress(port));
        } catch(Exception e) {
            Log.e("LanLinkProvider", "Error: Could not bind udp socket");
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

        Log.i("LanLinkProvider","Using tcpPort "+tcpPort);

        //I'm on a new network, let's be polite and introduce myself
        final int finalTcpPort = tcpPort;
        new AsyncTask<Void,Void,Void>() {
            @Override
            protected Void doInBackground(Void... voids) {

                try {
                    NetworkPackage identity = NetworkPackage.createIdentityPackage(context);
                    identity.set("tcpPort",finalTcpPort);
                    byte[] b = identity.serialize().getBytes("UTF-8");
                    DatagramPacket packet = new DatagramPacket(b, b.length, InetAddress.getByAddress(new byte[]{-1,-1,-1,-1}), port);
                    DatagramSocket socket = new DatagramSocket();
                    socket.setReuseAddress(true);
                    socket.setBroadcast(true);
                    socket.send(packet);
                    //Log.e("LanLinkProvider","Udp identity package sent");
                } catch(Exception e) {
                    e.printStackTrace();
                    Log.e("LanLinkProvider","Sending udp identity package failed");
                }

                return null;

            }

        }.execute();

    }

    @Override
    public void onNetworkChange() {
        onStop();
        onStart();
    }

    @Override
    public void onStop() {
        udpAcceptor.unbind();
        tcpAcceptor.unbind();
    }
/*
    @Override
    public int getPriority() {
        return 1000;
    }
*/
    @Override
    public String getName() {
        return "LanLinkProvider";
    }
}
