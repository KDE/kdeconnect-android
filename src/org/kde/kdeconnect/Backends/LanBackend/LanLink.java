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
import android.util.Log;

import org.json.JSONObject;
import org.kde.kdeconnect.Backends.BaseLink;
import org.kde.kdeconnect.Backends.BaseLinkProvider;
import org.kde.kdeconnect.Backends.BasePairingHandler;
import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Helpers.SecurityHelpers.RsaHelper;
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper;
import org.kde.kdeconnect.NetworkPackage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.NotYetConnectedException;
import java.security.PublicKey;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;

public class LanLink extends BaseLink {

    public enum ConnectionStarted {
        Locally, Remotely;
    };

    protected ConnectionStarted connectionSource; // If the other device sent me a broadcast,
                                                  // I should not close the connection with it
                                                  // because it's probably trying to find me and
                                                  // potentially ask for pairing.

    private Channel channel = null;
    private boolean onSsl = false;


    @Override
    public void disconnect() {
        closeSocket();
    }

    //Returns the old channel
    public Channel reset(Channel channel, ConnectionStarted connectionSource, boolean onSsl) {
        Channel oldChannel = this.channel;
        this.channel = channel;
        this.connectionSource = connectionSource;
        return oldChannel;
    }

    public void closeSocket() {
        if (channel == null) {
            Log.e("KDE/LanLink", "Not yet connected");
            return;
        }
        channel.close();
    }

    public LanLink(Context context, String deviceId, BaseLinkProvider linkProvider, Channel channel, ConnectionStarted connectionSource, boolean onSsl) {
        super(context, deviceId, linkProvider);
        reset(channel, connectionSource, onSsl);
    }


    @Override
    public String getName() {
        return "LanLink";
    }

    @Override
    public BasePairingHandler getPairingHandler(Device device, BasePairingHandler.PairingHandlerCallback callback) {
        return new LanPairingHandler(device, callback);
    }

    @Override
    public void addPackageReceiver(PackageReceiver pr) {
        super.addPackageReceiver(pr);
        BackgroundService.RunCommand(context, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                Device device = service.getDevice(getDeviceId());
                if (device == null) return;
                if (!device.isPaired()) return;
                // If the device is already paired due to other link, just send a pairing request to get required attributes for this link
            }
        });
    }

    //Blocking, do not call from main thread
    private void sendPackageInternal(NetworkPackage np, final Device.SendPackageStatusCallback callback, PublicKey key) {
        if (channel == null) {
            Log.e("KDE/sendPackage", "Not yet connected");
            callback.sendFailure(new NotYetConnectedException());
            return;
        }

        try {

            //Prepare socket for the payload
            final ServerSocket server;
            if (np.hasPayload()) {
                server = openTcpSocketOnFreePort(context, getDeviceId(), onSsl);
                JSONObject payloadTransferInfo = new JSONObject();
                payloadTransferInfo.put("port", server.getLocalPort());
                np.setPayloadTransferInfo(payloadTransferInfo);
            } else {
                server = null;
            }

            //Encrypt if key provided
            if (key != null) {
                np = RsaHelper.encrypt(np, key);
            }

            //Send body of the network package
            ChannelFuture future = channel.writeAndFlush(np.serialize()).sync();
            if (!future.isSuccess()) {
                Log.e("KDE/sendPackage", "!future.isWritten()");
                callback.sendFailure(future.cause());
                return;
            }


            //Send payload
            if (server != null) {
                OutputStream socket = null;
                try {
                    //Wait a maximum of 10 seconds for the other end to establish a connection with our socket, close it afterwards
                    server.setSoTimeout(10*1000);
                    socket = server.accept().getOutputStream();

                    Log.i("KDE/LanLink", "Beginning to send payload");

                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    long progress = 0;
                    InputStream stream = np.getPayload();
                    while ((bytesRead = stream.read(buffer)) != -1) {
                        //Log.e("ok",""+bytesRead);
                        progress += bytesRead;
                        socket.write(buffer, 0, bytesRead);
                        if (np.getPayloadSize() > 0) {
                            callback.sendProgress((int)(progress / np.getPayloadSize()));
                        }
                    }
                    socket.flush();
                    stream.close();
                    Log.i("KDE/LanLink", "Finished sending payload ("+progress+" bytes written)");
                } catch (Exception e) {
                    Log.e("KDE/sendPackage", "Exception: "+e);
                    callback.sendFailure(e);
                    return;
                } finally {
                    if (socket != null) {
                        try { socket.close(); } catch (Exception e) { }
                    }
                    try { server.close(); } catch (Exception e) { }
                }
            }

            callback.sendSuccess();

        } catch (Exception e) {
            if (callback != null) {
                callback.sendFailure(e);
            }
        } finally  {
            //Make sure we close the payload stream, if any
            InputStream stream = np.getPayload();
            try { stream.close(); } catch (Exception e) { }
        }
    }


    //Blocking, do not call from main thread
    @Override
    public void sendPackage(NetworkPackage np,Device.SendPackageStatusCallback callback) {
        sendPackageInternal(np, callback, null);

    }

    //Blocking, do not call from main thread
    @Override
    public void sendPackageEncrypted(NetworkPackage np, Device.SendPackageStatusCallback callback, PublicKey key) {
        if (onSsl) {
            sendPackageInternal(np, callback, null); // No need to encrypt
        }else {
            sendPackageInternal(np, callback, key);
        }
    }

    public void injectNetworkPackage(NetworkPackage np) {

        if (np.getType().equals(NetworkPackage.PACKAGE_TYPE_ENCRYPTED)) {
            try {
                np = RsaHelper.decrypt(np, privateKey);
            } catch(Exception e) {
                e.printStackTrace();
                Log.e("KDE/onPackageReceived","Exception decrypting the package");
            }

        }

        if (np.hasPayloadTransferInfo()) {

            Socket socket = null;
            try {
                // Use ssl if existing link is on ssl
                if (onSsl) {
                    SSLContext sslContext = SslHelper.getSslContext(context, getDeviceId(), true);
                    socket = sslContext.getSocketFactory().createSocket();
                } else {
                    socket = new Socket();
                }

                int tcpPort = np.getPayloadTransferInfo().getInt("port");
                InetSocketAddress address = (InetSocketAddress)channel.remoteAddress();
                socket.connect(new InetSocketAddress(address.getAddress(), tcpPort));
                np.setPayload(socket.getInputStream(), np.getPayloadSize());
            } catch (Exception e) {
                try { socket.close(); } catch(Exception ignored) { }
                e.printStackTrace();
                Log.e("KDE/LanLink", "Exception connecting to payload remote socket");
            }

        }

        packageReceived(np);
    }

    static ServerSocket openTcpSocketOnFreePort(Context context, String deviceId, boolean useSsl) throws IOException {
        if (useSsl) {
            return openSecureServerSocket(context, deviceId);
        } else {
            return openUnsecureSocketOnFreePort();
        }
    }


    static ServerSocket openUnsecureSocketOnFreePort() throws IOException {
        boolean success = false;
        int tcpPort = 1739;
        ServerSocket candidateServer = null;
        while(!success) {
            try {
                candidateServer = new ServerSocket();
                candidateServer.bind(new InetSocketAddress(tcpPort));
                success = true;
                Log.i("KDE/LanLink", "Using port "+tcpPort);
            } catch(IOException e) {
                //Log.e("LanLink", "Exception openning serversocket: "+e);
                tcpPort++;
                if (tcpPort >= 1764) {
                    Log.e("KDE/LanLink", "No more ports available");
                    throw e;
                }
            }
        }
        return candidateServer;
    }

    static ServerSocket openSecureServerSocket(Context context, String deviceId) throws IOException{
        boolean success = false;
        int tcpPort = 1739;

        SSLContext tlsContext = SslHelper.getSslContext(context, deviceId, true);
        SSLServerSocketFactory sslServerSocketFactory = tlsContext.getServerSocketFactory();

        ServerSocket candidateServer = null;
        while(!success) {
            try {
                candidateServer = sslServerSocketFactory.createServerSocket();
                candidateServer.bind(new InetSocketAddress(tcpPort));
                success = true;
                Log.i("LanLink", "Using port "+tcpPort);
            } catch(IOException e) {
                //Log.e("LanLink", "Exception opening serversocket: "+e);
                tcpPort++;
                if (tcpPort >= 1764) {
                    Log.e("LanLink", "No more ports available");
                    throw e;
                }
            }
        }
        return candidateServer;
    }

    @Override
    public boolean linkShouldBeKeptAlive() {

        //We keep the remotely initiated connections, since the remotes require them if they want to request
        //pairing to us, or connections that are already paired. TODO: Keep connections in the process of pairing

        if (connectionSource == ConnectionStarted.Remotely) {
            return true;
        }

        SharedPreferences preferences = context.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE);
        if (preferences.contains(getDeviceId())) {
            return true; //Already paired
        }

        return false;

    }
}
