package org.kde.connect.ComputerLinks;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.future.IoFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.future.ReadFuture;
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

public class NioSessionComputerLink extends BaseComputerLink {

    private IoSession session = null;

    public void disconnect() {
        Log.e("NioSessionComputerLink","Disconnect: "+session.getRemoteAddress().toString());
        session.close(true);
    }

    public NioSessionComputerLink(IoSession session, String deviceId, BaseLinkProvider linkProvider) {
        super(deviceId, linkProvider);
        this.session = session;
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

    public void injectNetworkPackage(NetworkPackage np) {
        packageReceived(np);
    }
}
