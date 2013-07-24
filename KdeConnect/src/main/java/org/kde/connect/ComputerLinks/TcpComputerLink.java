package org.kde.connect.ComputerLinks;

import android.os.AsyncTask;
import android.util.Log;

import org.apache.mina.core.buffer.SimpleBufferAllocator;
import org.apache.mina.core.filterchain.IoFilter;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.LineDelimiter;
import org.apache.mina.filter.codec.textline.TextLineCodecFactory;
import org.apache.mina.filter.keepalive.KeepAliveFilter;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.kde.connect.LinkProviders.BaseLinkProvider;
import org.kde.connect.NetworkPackage;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;

public class TcpComputerLink extends BaseComputerLink {

    private IoSession session = null;

    public TcpComputerLink(BaseLinkProvider linkProvider) {
        super(linkProvider);
    }

    public void connect(final InetAddress ip, final int port) {

        final NioSocketConnector connector = new NioSocketConnector();
        connector.setHandler(new IoHandlerAdapter() {
            @Override
            public void messageReceived(IoSession session, Object message) throws Exception {
                super.messageReceived(session, message);
                Log.e("TcpComputerLink","messageReceived (" + message.getClass() + ") " + message.toString());
                try {
                    String theMessage = (String) message;
                    NetworkPackage np = NetworkPackage.unserialize(theMessage);
                    packageReceived(np);
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e("TcpComputerLink","Could not unserialize package");
                }
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
                Log.e("TcpComputerLink","connected");
                session = future.getSession();
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
