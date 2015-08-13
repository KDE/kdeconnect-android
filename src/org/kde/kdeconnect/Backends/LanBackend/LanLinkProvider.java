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
import android.util.Base64;
import android.util.Log;

import org.kde.kdeconnect.Backends.BaseLinkProvider;
import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper;
import org.kde.kdeconnect.NetworkPackage;
import org.kde.kdeconnect.UserInterface.CustomDevicesActivity;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.HashMap;

import javax.net.ssl.SSLEngine;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

public class LanLinkProvider extends BaseLinkProvider {

    public static final String KEY_CUSTOM_DEVLIST_PREFERENCE  = "device_list_preference";
    private final static int port = 1714;

    private final Context context;
    private final HashMap<String, LanLink> visibleComputers = new HashMap<String, LanLink>();
    private final LongSparseArray<LanLink> nioLinks = new LongSparseArray<LanLink>();
    private final LongSparseArray<Channel> nioChannels = new LongSparseArray<Channel>();

    private EventLoopGroup bossGroup, workerGroup, udpGroup, clientGroup;
    private TcpHandler tcpHandler = new TcpHandler();
    private UdpHandler udpHandler = new UdpHandler();

    @ChannelHandler.Sharable
    private class TcpHandler extends SimpleChannelInboundHandler<String>{
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
            // Close channel for any sudden exception
            ctx.channel().close();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            // Called after a long time if remote device closes session unexpectedly, like wifi off
            try {
                long id = ctx.channel().hashCode();
                final LanLink brokenLink = nioLinks.get(id);
                Channel channel = nioChannels.get(id);
                if (channel != null) {
                    nioChannels.remove(id);
                }
                if (brokenLink != null) {
                    nioLinks.remove(id);
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

        @Override
        public void channelRead0(ChannelHandlerContext ctx, String message) throws Exception {
//            Log.e("LanLinkProvider","Incoming package, address: " + ctx.channel().remoteAddress());
//            Log.e("LanLinkProvider","Received:"+message);


            if (message.isEmpty()) {
                Log.e("KDE/LanLinkProvider", "Empty package received");
                return;
            }

            final NetworkPackage np = NetworkPackage.unserialize(message);

            if (np.getType().equals(NetworkPackage.PACKAGE_TYPE_IDENTITY)) {

                NetworkPackage myIdentityPackage = NetworkPackage.createIdentityPackage(context);
                if (np.getString("deviceId").equals(myIdentityPackage.getString("deviceId"))) {
                    return;
                }

                Log.i("KDE/LanLinkProvider", "Identity package received from " + np.getString("deviceName"));

                final LanLink link = new LanLink(context, ctx.channel(), np.getString("deviceId"), LanLinkProvider.this);
                nioLinks.put(ctx.channel().hashCode(), link);
                nioChannels.put(ctx.channel().hashCode(), ctx.channel());
                //Log.i("KDE/LanLinkProvider","nioLinks.size(): " + nioLinks.size());

                // Add ssl handler if device uses new protocol
                try {
                    if (NetworkPackage.ProtocolVersion <= np.getInt("protocolVersion")) {
                        final SSLEngine sslEngine = SslHelper.getSslEngine(context, np.getString("deviceId"), SslHelper.SslMode.Client);

                        SslHandler sslHandler = new SslHandler(sslEngine);
                        ctx.channel().pipeline().addFirst(sslHandler);
                        sslHandler.handshakeFuture().addListener(new GenericFutureListener<Future<? super Channel>>() {
                            @Override
                            public void operationComplete(Future<? super Channel> future) throws Exception {
                                if (future.isSuccess()) {
                                    Log.i("KDE/LanLinkProvider","Handshake successful with " + np.getString("deviceName") + " secured with " + sslEngine.getSession().getCipherSuite());
                                    Certificate certificate = sslEngine.getSession().getPeerCertificates()[0];
                                    np.set("certificate", Base64.encodeToString(certificate.getEncoded(), 0));
                                    link.setOnSsl(true);
                                    addLink(np, link);
                                } else {
                                    // Unpair if handshake failed
                                    Log.e("KDE/LanLinkProvider", "Handshake failed with " + np.getString("deviceName"));
                                    future.cause().printStackTrace();
                                    BackgroundService.RunCommand(context, new BackgroundService.InstanceCallback() {
                                        @Override
                                        public void onServiceStart(BackgroundService service) {
                                            Device device = service.getDevice(np.getString("deviceId"));
                                            if (device == null) return;
                                            device.unpair();
                                        }
                                    });
                                }

                            }
                        });
                    } else {
                        addLink(np, link);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            } else {
                LanLink prevLink = nioLinks.get(ctx.channel().hashCode());
                if (prevLink == null) {
                    Log.e("KDE/LanLinkProvider","Expecting an identity package (A)");
                } else {
                    prevLink.injectNetworkPackage(np);
                }
            }

        }
    }

    @ChannelHandler.Sharable
    private class UdpHandler extends SimpleChannelInboundHandler<DatagramPacket> {

        @Override
        protected void channelRead0(final ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
            try {
                String theMessage = packet.content().toString(CharsetUtil.UTF_8);

                final NetworkPackage identityPackage = NetworkPackage.unserialize(theMessage);

                if (!identityPackage.getType().equals(NetworkPackage.PACKAGE_TYPE_IDENTITY)) {
                    Log.e("KDE/LanLinkProvider", "Expecting an identity package (B)");
                    return;
                } else {
                    String myId = NetworkPackage.createIdentityPackage(context).getString("deviceId");
                    if (identityPackage.getString("deviceId").equals(myId)) {
                        return;
                    }
                }

                Log.i("KDE/LanLinkProvider", "Identity package received, creating link");

                try{
                    Bootstrap b = new Bootstrap();
                    b.group(clientGroup);
                    b.channel(NioSocketChannel.class);
                    b.handler(new TcpInitializer());
                    int tcpPort = identityPackage.getInt("tcpPort", port);
                    final ChannelFuture channelFuture = b.connect(packet.sender().getAddress(), tcpPort);
                    channelFuture.addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {

                            if (!future.isSuccess()) {
                                Log.e("KDE/LanLinkProvider", "Cannot connect to " + identityPackage.getString("deviceId"));
                                return;
                            }

                            final Channel channel = channelFuture.channel();

                            Log.i("KDE/LanLinkProvider", "Connection successful: " + channel.isActive());

                            // Add ssl handler if device supports new protocol
                            if (NetworkPackage.ProtocolVersion <= identityPackage.getInt("protocolVersion")) {
                                // add ssl handler with start tls true
                                SSLEngine sslEngine = SslHelper.getSslEngine(context, identityPackage.getString("deviceId"), SslHelper.SslMode.Server);
                                SslHandler sslHandler = new SslHandler(sslEngine, true);
                                channel.pipeline().addFirst(sslHandler);
                            }

                            final LanLink link = new LanLink(context, channel, identityPackage.getString("deviceId"), LanLinkProvider.this);
                                    NetworkPackage np2 = NetworkPackage.createIdentityPackage(context);
                                    link.sendPackage(np2,new Device.SendPackageStatusCallback() {
                                        @Override
                                        protected void onSuccess() {
                                            nioLinks.put(channel.hashCode(), link);
                                            nioChannels.put(channel.hashCode(), channel);
                                            Log.i("KDE/LanLinkProvider", "nioLinks.size(): " + nioLinks.size());

                                            // If ssl handler is in channel, add link after handshake is completed
                                            final SslHandler sslHandler = channel.pipeline().get(SslHandler.class);
                                            if (sslHandler != null) {
                                                sslHandler.handshakeFuture().addListener(new GenericFutureListener<Future<? super Channel>>() {
                                                    @Override
                                                    public void operationComplete(Future<? super Channel> future) throws Exception {
                                                        if (future.isSuccess()) {
                                                            try {
                                                                Log.i("KDE/LanLinkProvider", "Handshake successfully completed with " + identityPackage.getString("deviceName") + ", session secured with " + sslHandler.engine().getSession().getCipherSuite());
                                                                Certificate certificate = sslHandler.engine().getSession().getPeerCertificates()[0];
                                                                identityPackage.set("certificate", Base64.encodeToString(certificate.getEncoded(), 0));
                                                                link.setOnSsl(true);
                                                                addLink(identityPackage, link);
                                                            } catch (Exception e){
                                                                e.printStackTrace();
                                                            }
                                                        } else {
                                                            // Unpair if handshake failed
                                                            // Any exception or handshake exception ?
                                                            Log.e("KDE/LanLinkProvider", "Handshake failed with " + identityPackage.getString("deviceName"));
                                                            future.cause().printStackTrace();

                                                            BackgroundService.RunCommand(context, new BackgroundService.InstanceCallback() {
                                                                @Override
                                                                public void onServiceStart(BackgroundService service) {
                                                                    Device device = service.getDevice(identityPackage.getString("deviceId"));
                                                                    if (device == null) return;
                                                                    device.unpair();
                                                                }
                                                            });
                                                        }

                                                    }
                                                });
                                            } else {
                                                addLink(identityPackage, link);
                                            }

                                        }

                                        @Override
                                        protected void onFailure(Throwable e) {
                                            Log.e("KDE/LanLinkProvider", "Connection failed: could not send identity package back");
                                        }
                                    });


                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }

            } catch (Exception e) {
                Log.e("KDE/LanLinkProvider","Exception receiving udp package!!");
                e.printStackTrace();
            }
        }

    }

    public class TcpInitializer extends ChannelInitializer<SocketChannel> {
        @Override
        protected void initChannel(SocketChannel ch) throws Exception {
            ChannelPipeline pipeline = ch.pipeline();
            ch.config().setAllowHalfClosure(false); // Not sure how it will work, but we certainly don't want half closure
            ch.config().setKeepAlive(true);
            pipeline.addLast(new DelimiterBasedFrameDecoder(512 * 1024, Delimiters.lineDelimiter()));
            pipeline.addLast(new StringDecoder());
            pipeline.addLast(new StringEncoder());
            pipeline.addLast(tcpHandler);
        }
    }

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
            if (link.getStartTime() < oldLink.getStartTime()) {
                // New link is not so new, it just took more time to be establish successfully and get here
                link.disconnect();
                connectionLost(link);
            } else {
                Log.i("KDE/LanLinkProvider", "Removing old connection to same device");
                oldLink.disconnect();
                connectionLost(oldLink);
            }
        }
    }

    public LanLinkProvider(Context context) {

        this.context = context;

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        try{
            ServerBootstrap tcpBootstrap = new ServerBootstrap();
            tcpBootstrap.group(bossGroup, workerGroup);
            tcpBootstrap.channel(NioServerSocketChannel.class);
            tcpBootstrap.option(ChannelOption.SO_BACKLOG, 100);
            tcpBootstrap.handler(new LoggingHandler(LogLevel.INFO));
            tcpBootstrap.option(ChannelOption.SO_REUSEADDR, true);
            tcpBootstrap.childHandler(new TcpInitializer());
            tcpBootstrap.bind(new InetSocketAddress(port)).sync();
        }catch (Exception e) {
            e.printStackTrace();
        }

        udpGroup = new NioEventLoopGroup();
        try {
            Bootstrap udpBootstrap = new Bootstrap();
            udpBootstrap.group(udpGroup);
            udpBootstrap.channel(NioDatagramChannel.class);
            udpBootstrap.option(ChannelOption.SO_BROADCAST, true);
            udpBootstrap.handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ChannelPipeline pipeline = ch.pipeline();
                    pipeline.addLast(new DelimiterBasedFrameDecoder(512 * 1024, Delimiters.lineDelimiter()));
                    pipeline.addLast(new StringDecoder());
                    pipeline.addLast(new StringEncoder());
                    pipeline.addLast(udpHandler);
                }
            });
            udpBootstrap.bind(new InetSocketAddress(port)).sync();
        }catch (Exception e){
            e.printStackTrace();
        }

        clientGroup = new NioEventLoopGroup();

    }

    @Override
    public void onStart() {

        Log.e("KDE/LanLinkProvider", "onStart");


        new Thread(new Runnable() {
            @Override
            public void run() {

                String deviceListPrefs = PreferenceManager.getDefaultSharedPreferences(context).getString(
                        KEY_CUSTOM_DEVLIST_PREFERENCE, "");
                ArrayList<String> iplist = new ArrayList<String>();
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
                            java.net.DatagramPacket packet = new java.net.DatagramPacket(bytes, bytes.length, client, port);
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
        Log.e("KDE/LanLinkProvider", "onNetworkChange");

        //FilesHelper.LogOpenFileCount();

        onStart();

        //FilesHelper.LogOpenFileCount();
    }

    @Override
    public void onStop() {
        Log.e("KDE/LanLinkProvider", "onStop");
        try {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
            udpGroup.shutdownGracefully();
            clientGroup.shutdownGracefully();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public String getName() {
        return "LanLinkProvider";
    }



}
