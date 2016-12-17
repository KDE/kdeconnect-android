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

import javax.net.ssl.SSLSocket;

public class LanLink extends BaseLink {

    public interface LinkDisconnectedCallback {
        void linkDisconnected(LanLink brokenLink);
    }

    public enum ConnectionStarted {
        Locally, Remotely;
    };

    private ConnectionStarted connectionSource; // If the other device sent me a broadcast,
                                                // I should not close the connection with it
                                                  // because it's probably trying to find me and
                                                  // potentially ask for pairing.

    private Socket socket = null;

    private LinkDisconnectedCallback callback;

    @Override
    public void disconnect() {
        Log.i("LanLink/Disconnect","socket:"+ socket.hashCode());
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //Returns the old socket
    public Socket reset(final Socket newSocket, ConnectionStarted connectionSource) throws IOException {

        Socket oldSocket = socket;
        socket = newSocket;

        this.connectionSource = connectionSource;

        if (oldSocket != null) {
            oldSocket.close(); //This should cancel the readThread
        }

        //Log.e("LanLink", "Start listening");
        //Create a thread to take care of incoming data for the new socket
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
                            throw new IOException("End of stream");
                        }
                        if (packet.isEmpty()) {
                            continue;
                        }
                        NetworkPackage np = NetworkPackage.unserialize(packet);
                        receivedNetworkPackage(np);
                    }
                } catch (Exception e) {
                    Log.i("LanLink", "Socket closed: " + newSocket.hashCode() + ". Reason: " + e.getMessage());
                    try { Thread.sleep(300); } catch (InterruptedException ignored) {} // Wait a bit because we might receive a new socket meanwhile
                    boolean thereIsaANewSocket = (newSocket != socket);
                    if (!thereIsaANewSocket) {
                        callback.linkDisconnected(LanLink.this);
                    }
                }
            }
        }).start();

        return oldSocket;
    }

    public LanLink(Context context, String deviceId, LanLinkProvider linkProvider, Socket socket, ConnectionStarted connectionSource) throws IOException {
        super(context, deviceId, linkProvider);
        callback = linkProvider;
        reset(socket, connectionSource);
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
                server = LanLinkProvider.openServerSocketOnFreePort(LanLinkProvider.PAYLOAD_TRANSFER_MIN_PORT);
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
                Socket payloadSocket = null;
                OutputStream outputStream = null;
                InputStream inputStream = null;
                try {
                    //Wait a maximum of 10 seconds for the other end to establish a connection with our socket, close it afterwards
                    server.setSoTimeout(10*1000);

                    payloadSocket = server.accept();

                    //Convert to SSL if needed
                    if (socket instanceof SSLSocket) {
                        payloadSocket = SslHelper.convertToSslSocket(context, payloadSocket, getDeviceId(), true, false);
                    }

                    outputStream = payloadSocket.getOutputStream();
                    inputStream = np.getPayload();

                    Log.i("KDE/LanLink", "Beginning to send payload");
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    long progress = 0;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        //Log.e("ok",""+bytesRead);
                        progress += bytesRead;
                        outputStream.write(buffer, 0, bytesRead);
                        if (np.getPayloadSize() > 0) {
                            callback.sendProgress((int)(progress / np.getPayloadSize()));
                        }
                    }
                    outputStream.flush();
                    outputStream.close();
                    Log.i("KDE/LanLink", "Finished sending payload ("+progress+" bytes written)");
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e("KDE/sendPackage", "Exception: "+e);
                    callback.sendFailure(e);
                    return;
                } finally {
                    try { server.close(); } catch (Exception e) { }
                    try { payloadSocket.close(); } catch (Exception e) { }
                    try { inputStream.close(); } catch (Exception e) { }
                    try { outputStream.close(); } catch (Exception e) { }
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

    private void receivedNetworkPackage(NetworkPackage np) {

        if (np.getType().equals(NetworkPackage.PACKAGE_TYPE_ENCRYPTED)) {
            try {
                np = RsaHelper.decrypt(np, privateKey);
            } catch(Exception e) {
                e.printStackTrace();
                Log.e("KDE/onPackageReceived","Exception decrypting the package");
            }
        }

        if (np.hasPayloadTransferInfo()) {

            Socket payloadSocket = new Socket();
            try {
                int tcpPort = np.getPayloadTransferInfo().getInt("port");
                InetSocketAddress deviceAddress = (InetSocketAddress) socket.getRemoteSocketAddress();
                payloadSocket.connect(new InetSocketAddress(deviceAddress.getAddress(), tcpPort));
                // Use ssl if existing link is on ssl
                if (socket instanceof SSLSocket) {
                    payloadSocket = SslHelper.convertToSslSocket(context, payloadSocket, getDeviceId(), true, true);
                }
                np.setPayload(payloadSocket.getInputStream(), np.getPayloadSize());
            } catch (Exception e) {
                try { payloadSocket.close(); } catch(Exception ignored) { }
                e.printStackTrace();
                Log.e("KDE/LanLink", "Exception connecting to payload remote socket");
            }

        }

        packageReceived(np);
    }

    @Override
    public boolean linkShouldBeKeptAlive() {

        return true;    //FIXME: Current implementation is broken, so for now we will keep links always established

        //We keep the remotely initiated connections, since the remotes require them if they want to request
        //pairing to us, or connections that are already paired.
        //return (connectionSource == ConnectionStarted.Remotely);

    }
}
