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

import org.kde.kdeconnect.Backends.BaseLinkProvider;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.NetworkPackage;
import org.kde.kdeconnect.UserInterface.CustomDevicesActivity;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.CharsetUtil;

public class LanLinkProvider extends BaseLinkProvider {

    public static final String KEY_CUSTOM_DEVLIST_PREFERENCE  = "device_list_preference";
    private final static int port = 1714;

    private final Context context;
    private final HashMap<String, LanLink> visibleComputers = new HashMap<String, LanLink>();
    private final LongSparseArray<LanLink> nioLinks = new LongSparseArray<LanLink>();
    private final LongSparseArray<Channel> nioChannels = new LongSparseArray<Channel>();
    
    private ServerBootstrap tcpBootstrap = null;
    private Bootstrap udpBootstrap = null;

    private class TcpHandler extends ChannelInboundHandlerAdapter{
        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            super.channelInactive(ctx);

            try {
                long id = ctx.channel().hashCode();
                final LanLink brokenLink = nioLinks.get(id);
                Channel channel = nioChannels.get(id);
                if (channel != null) {
                    channel.close();
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
        public void channelRead(ChannelHandlerContext ctx, Object message) throws Exception {
            super.channelRead(ctx, message);

//            Log.e("LanLinkProvider","Incoming package, address: " + ctx.channel().remoteAddress());
//            Log.e("LanLinkProvider","Received:"+message);

            String theMessage = (String) message;
            if (theMessage.isEmpty()) {
                Log.e("KDE/LanLinkProvider","Empty package received");
                return;
            }

            NetworkPackage np = NetworkPackage.unserialize(theMessage);

            if (np.getType().equals(NetworkPackage.PACKAGE_TYPE_IDENTITY)) {

                String myId = NetworkPackage.createIdentityPackage(context).getString("deviceId");
                if (np.getString("deviceId").equals(myId)) {
                    return;
                }

                //Log.i("KDE/LanLinkProvider", "Identity package received from " + np.getString("deviceName"));

                LanLink link = new LanLink(ctx.channel(), np.getString("deviceId"), LanLinkProvider.this);
                nioLinks.put(ctx.channel().hashCode(), link);
                //Log.i("KDE/LanLinkProvider","nioLinks.size(): " + nioLinks.size());
                addLink(np, link);
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

    private class UdpHandler extends SimpleChannelInboundHandler<DatagramPacket> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
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


                EventLoopGroup clientGroup = new NioEventLoopGroup();
                try{
                    Bootstrap b = new Bootstrap();
                    b.group(clientGroup);
                    b.channel(NioSocketChannel.class);
                    b.handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new DelimiterBasedFrameDecoder(8192, Delimiters.lineDelimiter()));
                            pipeline.addLast(new StringDecoder());
                            pipeline.addLast(new StringEncoder());
                            pipeline.addLast(new TcpHandler());
                        }
                    });
                    int tcpPort = identityPackage.getInt("tcpPort", port);
                    final ChannelFuture channelFuture = b.connect(packet.sender().getAddress(), tcpPort).sync();
                    channelFuture.addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            channelFuture.removeListener(this);
                            final Channel channel = channelFuture.channel();

                            Log.i("KDE/LanLinkProvider", "Connection successful: " + channel.isActive());

                            final LanLink link = new LanLink(channel, identityPackage.getString("deviceId"), LanLinkProvider.this);
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    NetworkPackage np2 = NetworkPackage.createIdentityPackage(context);
                                    link.sendPackage(np2,new Device.SendPackageStatusCallback() {
                                        @Override
                                        protected void onSuccess() {
                                            nioLinks.put(channel.hashCode(), link);
                                            nioChannels.put(channel.hashCode(), channel);
                                            Log.i("KDE/LanLinkProvider","nioLinks.size(): " + nioLinks.size());
                                            addLink(identityPackage, link);
                                        }

                                        @Override
                                        protected void onFailure(Throwable e) {
                                            Log.e("KDE/LanLinkProvider", "Connection failed: could not send identity package back");
                                        }
                                    });

                                }
                            }).start();
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
            oldLink.disconnect();
            connectionLost(oldLink);
        }
    }

    public LanLinkProvider(Context context) {

        this.context = context;

        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try{
            tcpBootstrap = new ServerBootstrap();
            tcpBootstrap.group(bossGroup, workerGroup);
            tcpBootstrap.channel(NioServerSocketChannel.class);
            tcpBootstrap.option(ChannelOption.SO_BACKLOG, 100);
            tcpBootstrap.handler(new LoggingHandler(LogLevel.INFO));
            tcpBootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);
            tcpBootstrap.childOption(ChannelOption.SO_REUSEADDR, true);
            tcpBootstrap.childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ChannelPipeline pipeline = ch.pipeline();
                    pipeline.addLast(new DelimiterBasedFrameDecoder(8192, Delimiters.lineDelimiter()));
                    pipeline.addLast(new StringDecoder());
                    pipeline.addLast(new StringEncoder());
                    pipeline.addLast(new TcpHandler());
                }
            });
            tcpBootstrap.bind(new InetSocketAddress(port)).sync();
        }catch (Exception e) {
            e.printStackTrace();
        }

        EventLoopGroup udpEventLoopGroup = new NioEventLoopGroup();
        try {
            udpBootstrap = new Bootstrap();
            udpBootstrap.group(udpEventLoopGroup);
            udpBootstrap.channel(NioDatagramChannel.class);
            udpBootstrap.option(ChannelOption.SO_BROADCAST, true);
            udpBootstrap.handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ChannelPipeline pipeline = ch.pipeline();
                    pipeline.addLast(new DelimiterBasedFrameDecoder(8192, Delimiters.lineDelimiter()));
                    pipeline.addLast(new StringDecoder());
                    pipeline.addLast(new StringEncoder());
                    pipeline.addLast(new UdpHandler());
                }
            });
            udpBootstrap.bind(new InetSocketAddress(port)).sync();
        }catch (Exception e){
            e.printStackTrace();
        }

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
        Log.e("KDE/LanLinkProvider","onNetworkChange");

        //FilesHelper.LogOpenFileCount();

        //Keep existing connections open while unbinding the socket
//        tcpAcceptor.setCloseOnDeactivation(false);
//        onStop();
//        tcpAcceptor.setCloseOnDeactivation(true);

        //FilesHelper.LogOpenFileCount();

        onStart();

        //FilesHelper.LogOpenFileCount();
    }

    @Override
    public void onStop() {
        Log.e("KDE/LanLinkProvider", "onStop");
        try {
            udpBootstrap.group().shutdownGracefully().sync();
            tcpBootstrap.group().shutdownGracefully().sync();
            tcpBootstrap.childGroup().shutdownGracefully().sync();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public String getName() {
        return "LanLinkProvider";
    }



}
