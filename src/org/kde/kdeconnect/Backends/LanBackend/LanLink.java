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
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Helpers.SecurityHelpers.RsaHelper;
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper;
import org.kde.kdeconnect.Helpers.StringsHelper;
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
import javax.net.ssl.SSLSocket;

public class LanLink extends BaseLink {

    public enum ConnectionStarted {
        Locally, Remotely;
    };

    protected ConnectionStarted connectionSource; // If the other device sent me a broadcast,
                                                  // I should not close the connection with it
                                                  // because it's probably trying to find me and
                                                  // potentially ask for pairing.

    private Socket socket = null;

    @Override
    public void disconnect() {

        Log.i("LanLink/Disconnect","socket:"+ socket.hashCode());

        if (socket == null) {
            Log.w("KDE/LanLink", "Not yet connected");
            return;
        }

        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    //Returns the old socket
    public Socket reset(final Socket newSocket, ConnectionStarted connectionSource, final LanLinkProvider linkProvider) throws IOException {

        Socket oldSocket = socket;
        socket = newSocket;

        this.connectionSource = connectionSource;

        if (oldSocket != null) {
            oldSocket.close(); //This should cancel the readThread
        }

        Log.e("LanLink", "Start listening");
        //Start listening
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(newSocket.getInputStream(), StringsHelper.UTF8));
                    while (true) {
                        String packet;
                        try {
                            packet = reader.readLine();
                        } catch (SocketTimeoutException e) {
                            continue;
                        }
                        if (packet == null) {
                            throw new IOException("Read null");
                        }
                        if (packet.isEmpty()) continue;
                        NetworkPackage np = NetworkPackage.unserialize(packet);
                        injectNetworkPackage(np);
                    }
                } catch (Exception e) {
                    Log.e("LanLink", "Socket closed " + newSocket.hashCode() + " reason: " + e.getMessage());
                    boolean thereIsaANewSocket = (newSocket != socket);
                    linkProvider.socketClosed(newSocket, thereIsaANewSocket);
                }
            }
        }).start();

        return oldSocket;
    }

    public LanLink(Context context, String deviceId, LanLinkProvider linkProvider, Socket socket, ConnectionStarted connectionSource) throws IOException {
        super(context, deviceId, linkProvider);
        reset(socket, connectionSource, linkProvider);
    }


    @Override
    public String getName() {
        return "LanLink";
    }

    @Override
    public BasePairingHandler getPairingHandler(Device device, BasePairingHandler.PairingHandlerCallback callback) {
        return new LanPairingHandler(device, callback);
    }

    //Blocking, do not call from main thread
    private void sendPackageInternal(NetworkPackage np, final Device.SendPackageStatusCallback callback, PublicKey key) {
        if (socket == null) {
            Log.e("KDE/sendPackage", "Not yet connected");
            callback.sendFailure(new NotYetConnectedException());
            return;
        }

        try {

            //Prepare socket for the payload
            final ServerSocket server;
            if (np.hasPayload()) {
                server = openTcpSocketOnFreePort(context, getDeviceId());
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

            //Log.e("LanLink/sendPackage", np.getType());

            //Send body of the network package
            try {
                OutputStream writter = socket.getOutputStream();
                writter.write(np.serialize().getBytes(StringsHelper.UTF8));
                writter.flush();
            } catch (Exception e) {
                callback.sendFailure(e);
                e.printStackTrace();
                disconnect();
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
                if (socket instanceof SSLSocket) {
                    SSLContext sslContext = SslHelper.getSslContext(context, getDeviceId(), true);
                    payloadSocket = sslContext.getSocketFactory().createSocket();
                } else {
                    payloadSocket = new Socket();
                }

                int tcpPort = np.getPayloadTransferInfo().getInt("port");
                InetSocketAddress address = (InetSocketAddress) socket.getRemoteSocketAddress();
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

    ServerSocket openTcpSocketOnFreePort(Context context, String deviceId) throws IOException {
        if (socket instanceof SSLSocket) {
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
            //Log.e("LinkShouldBeKeptAlive", "because the other end started the connection");
            return true;
        }

        //Log.e("LinkShouldBeKeptAlive", "false");
        return false;

    }
}
