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
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.util.LongSparseArray;
import android.util.Base64;
import android.util.Log;

import org.kde.kdeconnect.Backends.BaseLinkProvider;
import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Helpers.DeviceHelper;
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper;
import org.kde.kdeconnect.NetworkPackage;
import org.kde.kdeconnect.UserInterface.CustomDevicesActivity;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;

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
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

public class LanLinkProvider extends BaseLinkProvider {

    public static final String KEY_CUSTOM_DEVLIST_PREFERENCE  = "device_list_preference";
    private final static int port = 1714;
    private static final int MIN_VERSION_WITH_SSL_SUPPORT = 6;

    private final Context context;

    private final HashMap<String, LanLink> visibleComputers = new HashMap<>();  //Links by device id
    private final LongSparseArray<LanLink> nioLinks = new LongSparseArray<>(); //Links by channel id

    private EventLoopGroup bossGroup, workerGroup, udpGroup, clientGroup;
    private TcpHandler tcpHandler = new TcpHandler();
    private UdpHandler udpHandler = new UdpHandler();

    // To prevent infinte loop if both device can only broadcast identity package but cannot connect via TCO
    private ArrayList<String> reverseConnectionBlackList = new ArrayList<>();
    private Timer reverseConnectionTimer;

    private ChannelInitializer<SocketChannel> tcpInitializer = new ChannelInitializer<SocketChannel>() {
        @Override
        protected void initChannel(SocketChannel ch) throws Exception {
            ChannelPipeline pipeline = ch.pipeline();
            ch.config().setKeepAlive(true);
            pipeline.addLast(new LineBasedFrameDecoder(512 * 1024));
            pipeline.addLast(new StringDecoder());
            pipeline.addLast(new StringEncoder());
            pipeline.addLast(tcpHandler);
        }
    };

    private ChannelInitializer<Channel> udpInitializer = new ChannelInitializer<Channel>() {
        @Override
        protected void initChannel(Channel ch) throws Exception {
            ChannelPipeline pipeline = ch.pipeline();
            pipeline.addLast(new LineBasedFrameDecoder(512 * 1024));
            pipeline.addLast(new StringDecoder());
            pipeline.addLast(new StringEncoder());
            pipeline.addLast(udpHandler);
        }
    };

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
                Channel channel = ctx.channel();
                final LanLink brokenLink = nioLinks.get(channel.hashCode());
                if (brokenLink != null) {
                    nioLinks.remove(channel.hashCode());
                    //Log.i("KDE/LanLinkProvider", "nioLinks.size(): " + nioLinks.size() + " (-)");
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
            //Log.e("KDE/LanLinkProvider", "Received a TCP packet from " + ctx.channel().remoteAddress() + ":" + message);

            if (message.isEmpty()) {
                Log.e("KDE/LanLinkProvider", "Empty package received");
                return;
            }

            NetworkPackage networkPackage = NetworkPackage.unserialize(message);
            Channel channel =  ctx.channel();

            if (networkPackage.getType().equals(NetworkPackage.PACKAGE_TYPE_IDENTITY)) {

                //Log.i("KDE/LanLinkProvider", "Identity package received from a stablished TCP connection from " + networkPackage.getString("deviceName"));
                identityPackageReceived(networkPackage, channel, LanLink.ConnectionStarted.Locally);

            } else {

                LanLink link = nioLinks.get(channel.hashCode());
                if (link== null) {
                    Log.e("KDE/LanLinkProvider","Expecting an identity package instead of " + networkPackage.getType());
                } else {
                    link.injectNetworkPackage(networkPackage);
                }

            }

        }
    }

    private void identityPackageReceived(final NetworkPackage identityPackage, final Channel channel, final LanLink.ConnectionStarted connectionStarted) {

        String myId = DeviceHelper.getDeviceId(context);
        final String deviceId = identityPackage.getString("deviceId");
        if (deviceId.equals(myId)) {
            Log.e("KDE/LanLinkProvider", "Somehow I'm connected to myself, ignoring. This should not happen.");
            return;
        }

        final SslHelper.SslMode sslMode = (connectionStarted == LanLink.ConnectionStarted.Locally? SslHelper.SslMode.Client : SslHelper.SslMode.Server);

        // Add ssl handler if device uses new protocol
        try {
            if (identityPackage.getInt("protocolVersion") >= MIN_VERSION_WITH_SSL_SUPPORT) {
                final SSLEngine sslEngine = SslHelper.getSslEngine(context, deviceId, sslMode);
                SslHandler sslHandler = new SslHandler(sslEngine, sslMode == SslHelper.SslMode.Server);
                channel.pipeline().addFirst(sslHandler);
                sslHandler.handshakeFuture().addListener(new GenericFutureListener<Future<? super Channel>>() {
                    @Override
                    public void operationComplete(Future<? super Channel> future) throws Exception {
                        String mode = (sslMode == SslHelper.SslMode.Server? "server" : "client");
                        if (future.isSuccess()) {
                            Log.i("KDE/LanLinkProvider","Handshake as " + mode + " successful with " + identityPackage.getString("deviceName") + " secured with " + sslEngine.getSession().getCipherSuite());
                            Certificate certificate = sslEngine.getSession().getPeerCertificates()[0];
                            identityPackage.set("certificate", Base64.encodeToString(certificate.getEncoded(), 0));
                            addLink(identityPackage, channel, connectionStarted, true);
                        } else {
                            // Unpair if handshake failed
                            Log.e("KDE/LanLinkProvider", "Handshake as " + mode + " failed with " + identityPackage.getString("deviceName"));
                            future.cause().printStackTrace();
                            if (future.cause() instanceof SSLHandshakeException) {
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

                    }
                });
            } else {
                addLink(identityPackage, channel, connectionStarted, false);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    @ChannelHandler.Sharable
    private class UdpHandler extends SimpleChannelInboundHandler<DatagramPacket> {

        @Override
        protected void channelRead0(final ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
            try {
                String theMessage = packet.content().toString(CharsetUtil.UTF_8);

                final NetworkPackage identityPackage = NetworkPackage.unserialize(theMessage);
                final String deviceId = identityPackage.getString("deviceId");

                if (!identityPackage.getType().equals(NetworkPackage.PACKAGE_TYPE_IDENTITY)) {
                    Log.e("KDE/LanLinkProvider", "Expecting an UDP identity package");
                    return;
                } else {
                    String myId = DeviceHelper.getDeviceId(context);
                    if (deviceId.equals(myId)) {
                        Log.i("KDE/LanLinkProvider", "Ignoring my own broadcast");
                        return;
                    }
                }

                //Log.i("KDE/LanLinkProvider", "Identity package received, creating link");

                try{
                    Bootstrap bootstrap = new Bootstrap();
                    bootstrap.group(clientGroup);
                    bootstrap.channel(NioSocketChannel.class);
                    bootstrap.handler(tcpInitializer);
                    int tcpPort = identityPackage.getInt("tcpPort", port);
                    final ChannelFuture channelFuture = bootstrap.connect(packet.sender().getAddress(), tcpPort);
                    channelFuture.addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {

                            final Channel channel = channelFuture.channel();

                            if (!future.isSuccess()) {
                                Log.e("KDE/LanLinkProvider", "Cannot connect to " + deviceId);
                                if (!reverseConnectionBlackList.contains(deviceId)) {
                                    Log.w("KDE/LanLinkProvider","Blacklisting "+deviceId);
                                    reverseConnectionBlackList.add(deviceId);
                                    reverseConnectionTimer = new Timer();
                                    reverseConnectionTimer.schedule(new TimerTask() {
                                        @Override
                                        public void run() {
                                            reverseConnectionBlackList.remove(deviceId);
                                        }
                                    }, 5*1000);

                                    // Try to cause a reverse connection
                                    onNetworkChange();
                                }
                                return;
                            }

                            //Log.e("KDE/LanLinkProvider", "Connection successful: " + channel.isActive());


                            NetworkPackage np2 = NetworkPackage.createIdentityPackage(context);
                            ChannelFuture future2 = channel.writeAndFlush(np2.serialize()).sync();
                            if (!future2.isSuccess()) {
                                Log.e("KDE/LanLinkProvider", "Connection failed: could not send identity package back");
                                return;
                            }

                            identityPackageReceived(identityPackage, channel, LanLink.ConnectionStarted.Remotely);

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

    private void addLink(NetworkPackage identityPackage, Channel channel, LanLink.ConnectionStarted connectionOrigin, boolean useSsl) {
        String deviceId = identityPackage.getString("deviceId");
        LanLink currentLink = visibleComputers.get(deviceId);
        if (currentLink != null) {
            //Update old link
            Log.i("KDE/LanLinkProvider", "Reusing same link for device " + deviceId);
            final Channel oldChannel = currentLink.reset(channel, connectionOrigin, useSsl);
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
            //Let's create the link
            LanLink link = new LanLink(context, deviceId, this, channel, connectionOrigin, useSsl);
            nioLinks.put(channel.hashCode(), link);
            visibleComputers.put(deviceId, link);
            connectionAccepted(identityPackage, link);
        }
    }

    private void initUdpListener(int udpPort) {
        Bootstrap udpBootstrap = new Bootstrap();
        udpBootstrap.group(udpGroup);
        udpBootstrap.channel(NioDatagramChannel.class);
        udpBootstrap.option(ChannelOption.SO_BROADCAST, true);
        udpBootstrap.handler(udpInitializer);
        try {
            udpBootstrap.bind(new InetSocketAddress(udpPort)).sync();
        }catch (Exception e){
            Log.e("KDE/LanLinkProvider","Exception setting up UDP server");
            e.printStackTrace();
        }

    }

    public LanLinkProvider(Context context) {

        this.context = context;

        udpGroup = new NioEventLoopGroup();
        initUdpListener(port);

        clientGroup = new NioEventLoopGroup();

        // Due to certificate request from SSL server to client, the certificate request message from device with latest android version to device with
        // old android version causes a FATAL ALERT message stating that incorrect certificate request
        // Server is disabled on these devices and using a reverse connection strategy. This works well for connection of these devices with kde
        // and newer android versions. Although devices with android version less than ICS cannot connect to other devices who also have android version less
        // than ICS because server is disabled on both
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            Log.w("KDE/LanLinkProvider","Not starting a TCP server because it's not supported on Android < 14. Operating only as client.");
            return;
        }

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        try{
            ServerBootstrap tcpBootstrap = new ServerBootstrap();
            tcpBootstrap.group(bossGroup, workerGroup);
            tcpBootstrap.channel(NioServerSocketChannel.class);

            tcpBootstrap.option(ChannelOption.SO_BACKLOG, 100);
            tcpBootstrap.childOption(ChannelOption.SO_BACKLOG, 100);

            tcpBootstrap.option(ChannelOption.SO_KEEPALIVE, true);
            tcpBootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);

            tcpBootstrap.option(ChannelOption.SO_REUSEADDR, true);

            tcpBootstrap.childHandler(tcpInitializer);

            tcpBootstrap.bind(new InetSocketAddress(port)).sync();
        }catch (Exception e) {
            Log.e("KDE/LanLinkProvider","Exception setting up TCP server");
            e.printStackTrace();
        }

    }

    @Override
    public void onStart() {

        Log.i("KDE/LanLinkProvider", "onStart");


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
                            socket.send(new java.net.DatagramPacket(bytes, bytes.length, client, port));
                            //Log.i("KDE/LanLinkProvider","Udp identity package sent to address "+packet.getAddress());
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
    public void onNetworkChange() {
        //FilesHelper.LogOpenFileCount();
        onStart();
        //FilesHelper.LogOpenFileCount();
    }

    @Override
    public void onStop() {
        //Log.i("KDE/LanLinkProvider", "onStop");
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
