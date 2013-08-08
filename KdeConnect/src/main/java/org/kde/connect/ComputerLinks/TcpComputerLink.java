package org.kde.connect.ComputerLinks;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.LineDelimiter;
import org.apache.mina.filter.codec.textline.TextLineCodecFactory;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.kde.connect.LinkProviders.BaseLinkProvider;
import org.kde.connect.NetworkPackage;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;

public class TcpComputerLink extends BaseComputerLink {

    private IoSession session = null;

    public TcpComputerLink(String deviceId, BaseLinkProvider linkProvider) {
        super(deviceId, linkProvider);
    }

    public void connect(final InetAddress ip, final int port) {
        connect(ip,port,null,null);
    }

    public void connect(final InetAddress ip, final int port, final Handler callback, final Handler brokenHandler) {

        Log.e("TcpComputerLink","connect: "+ip.toString()+":"+port);

        final NioSocketConnector connector = new NioSocketConnector();
        connector.setHandler(new IoHandlerAdapter() {
            @Override
            public void messageReceived(IoSession session, Object message) throws Exception {
                super.messageReceived(session, message);
                Log.e("TcpComputerLink","messageReceived (" + message.getClass() + ") " + message.toString());
                NetworkPackage np = null;
                try {
                    //We should receive a string thanks to the TextLineCodecFactory filter
                    String theMessage = (String) message;
                    np = NetworkPackage.unserialize(theMessage);
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e("TcpComputerLink","Could not unserialize package");
                }
                if (np != null) packageReceived(np);
            }

            @Override
            public void sessionClosed(IoSession session) throws Exception {
                super.sessionClosed(session);
                if (brokenHandler != null) brokenHandler.dispatchMessage(new Message());
            }
        });

        //TextLineCodecFactory will split incoming data delimited by the given string
        connector.getFilterChain().addLast("codec",
                new ProtocolCodecFilter(
                        new TextLineCodecFactory(Charset.forName("UTF-8"), LineDelimiter.UNIX, LineDelimiter.UNIX)
                )
        );
        connector.getSessionConfig().setKeepAlive(true);

        new Thread(new Runnable() {
            @Override
            public void run() {
                ConnectFuture future = connector.connect(new InetSocketAddress(ip, port));
                //Wait unit it is connected (this call makes it blocking, but we are on a thread anyway)
                future.awaitUninterruptibly();
                if (!future.isConnected()) Log.e("TcpComputerLink","Could not connect");
                else Log.e("TcpComputerLink","connected");
                session = future.getSession();
                if (callback != null) callback.dispatchMessage(new Message());
            }
        }).run();

    }

    @Override
    public boolean sendPackage(NetworkPackage np) {
        Log.e("TcpComputerLink", "sendPackage");
        if (session == null) {
            Log.e("TcpComputerLink","not yet connected");
            return false;
        } else {
            session.write(np.serialize());
            return true;
        }
    }
}
