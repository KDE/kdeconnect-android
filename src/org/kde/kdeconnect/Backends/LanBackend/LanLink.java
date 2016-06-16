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
import org.kde.kdeconnect.Backends.BasePairingHandler;
import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Helpers.SecurityHelpers.RsaHelper;
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper;
import org.kde.kdeconnect.NetworkPackage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.channels.NotYetConnectedException;
import java.security.PublicKey;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;

public class LanLink extends BaseLink {

    public enum ConnectionStarted {
        Locally, Remotely;
    };

    protected ConnectionStarted connectionSource; // If the other device sent me a broadcast,
                                                  // I should not close the connection with it
                                                  // because it's probably trying to find me and
                                                  // potentially ask for pairing.

    private Socket channel = null;

    private boolean onSsl = false;

    OutputStream writter;
    Cancellable readThread;

    public abstract class Cancellable implements Runnable {
        protected volatile boolean cancelled;
        public void cancel()
        {
            cancelled = true;
        }
    }

    @Override
    public void disconnect() {
        if (readThread != null) {
            readThread.cancel();
        }
        if (channel == null) {
            Log.e("KDE/LanLink", "Not yet connected");
            return;
        }
        try {
            channel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //Returns the old channel
    public Socket reset(final Socket channel, ConnectionStarted connectionSource, boolean onSsl, final LanLinkProvider linkProvider) throws IOException {

        Socket oldChannel = this.channel;
        try {
            Log.e("reset", "1");
            //writter = channel.getOutputStream();
            Log.e("reset", "2");
            this.channel = channel;
            this.connectionSource = connectionSource;
            this.onSsl = onSsl;
            Log.e("reset", "BBBBB");

            if (oldChannel != null) {
                readThread.cancel();
                oldChannel.close();
            }

            Log.e("LanLink", "Start listening");
            //Start listening
            readThread = new Cancellable() {
                @Override
                public void run() {
                    try {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(channel.getInputStream(), LanLinkProvider.UTF8));
                        while (!cancelled) {
                            if (channel.isClosed()) {
                                Log.e("BufferReader", "Channel closed");
                                break;
                            }
                            String packet;
                            try {
                                packet = reader.readLine();
                                Log.e("packet", "A" + packet);
                            } catch (SocketTimeoutException e) {
                                Log.w("BufferReader", "timeout");
                                continue;
                            }
                            if (packet == null) {
                                Log.w("BufferReader", "null package");
                                break;
                            }
                            if (packet.isEmpty()) {
                                Log.w("BufferReader", "empty package: " + packet);
                                continue;
                            }
                            NetworkPackage np = NetworkPackage.unserialize(packet);
                            injectNetworkPackage(np);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    Log.e("LanLink", "Socket closed");
                    linkProvider.socketClosed(channel);
                }
            };

            new Thread(readThread).start();

        } catch (Exception e) {
            e.printStackTrace();
            Log.e("reset","except");
        }
        return oldChannel;
    }

    public LanLink(Context context, String deviceId, LanLinkProvider linkProvider, Socket channel, ConnectionStarted connectionSource, boolean onSsl) throws IOException {
        super(context, deviceId, linkProvider);
        reset(channel, connectionSource, onSsl, linkProvider);
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

            Log.e("LanLink/sendPackage", np.getType());

            //Send body of the network package
            try {
                //writter.write(np.serialize().getBytes(LanLinkProvider.UTF8));
                //writter.flush();
            } catch (Exception e) {
                callback.sendFailure(e);
                e.printStackTrace();
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
        sendPackageInternal(np, callback, key);
    }

    public void injectNetworkPackage(NetworkPackage np) {

        Log.e("receivedNetworkPackage",np.getType());

        if (np.getType().equals(NetworkPackage.PACKAGE_TYPE_ENCRYPTED)) {
            try {
                np = RsaHelper.decrypt(np, privateKey);
            } catch(Exception e) {
                e.printStackTrace();
                Log.e("KDE/onPackageReceived","Exception decrypting the package");
            }

        }

        if (np.hasPayloadTransferInfo()) {

            Socket payloadSocket = null;
            try {
                // Use ssl if existing link is on ssl
                if (onSsl) {
                    SSLContext sslContext = SslHelper.getSslContext(context, getDeviceId(), true);
                    payloadSocket = sslContext.getSocketFactory().createSocket();
                } else {
                    payloadSocket = new Socket();
                }

                int tcpPort = np.getPayloadTransferInfo().getInt("port");
                InetSocketAddress address = (InetSocketAddress)channel.getRemoteSocketAddress();
                payloadSocket.connect(new InetSocketAddress(address.getAddress(), tcpPort));
                np.setPayload(payloadSocket.getInputStream(), np.getPayloadSize());
            } catch (Exception e) {
                try { payloadSocket.close(); } catch(Exception ignored) { }
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
            return openUnsecureSocketOnFreePort(1739);
        }
    }


    static ServerSocket openUnsecureSocketOnFreePort(int minPort) throws IOException {
        int tcpPort = minPort;
        while(tcpPort < 1764) {
            try {
                ServerSocket candidateServer = new ServerSocket();
                candidateServer.bind(new InetSocketAddress(tcpPort));
                Log.i("KDE/LanLink", "Using port "+tcpPort);
                return candidateServer;
            } catch(IOException e) {
                tcpPort++;
            }
        }
        Log.e("KDE/LanLink", "No more ports available");
        throw new IOException("No more ports available");
    }

    static ServerSocket openSecureServerSocket(Context context, String deviceId) throws IOException{
        SSLContext tlsContext = SslHelper.getSslContext(context, deviceId, true);
        SSLServerSocketFactory sslServerSocketFactory = tlsContext.getServerSocketFactory();
        int tcpPort = 1739;
        while(tcpPort < 1764) {
            try {
                ServerSocket candidateServer = sslServerSocketFactory.createServerSocket();
                candidateServer.bind(new InetSocketAddress(tcpPort));
                Log.i("KDE/LanLink", "Using port "+tcpPort);
                return candidateServer;
            } catch(IOException e) {
                tcpPort++;
            }
        }
        Log.e("KDE/LanLink", "No more ports available");
        throw new IOException("No more ports available");
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
