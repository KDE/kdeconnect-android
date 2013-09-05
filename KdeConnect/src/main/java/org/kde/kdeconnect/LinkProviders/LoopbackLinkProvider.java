package org.kde.kdeconnect.LinkProviders;

import android.content.Context;
import android.os.AsyncTask;
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
import org.kde.kdeconnect.ComputerLinks.LanComputerLink;
import org.kde.kdeconnect.ComputerLinks.LoopbackComputerLink;
import org.kde.kdeconnect.NetworkPackage;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.HashMap;

public class LoopbackLinkProvider extends BaseLinkProvider {

    private Context context;

    public LoopbackLinkProvider(Context context) {
        this.context = context;
    }

    @Override
    public void onStart() {
        onNetworkChange();
    }

    @Override
    public void onStop() {

    }

    @Override
    public void onNetworkChange() {

        NetworkPackage np = NetworkPackage.createIdentityPackage(context);
        connectionAccepted(np, new LoopbackComputerLink(this));

    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public String getName() {
        return "LoopbackLinkProvider";
    }
}
