/*
 * Copyright 2016 Saikrishna Arcot <saiarcot895@gmail.com>
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

package org.kde.kdeconnect.Backends.BluetoothBackend;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.kde.kdeconnect.Backends.BaseLink;
import org.kde.kdeconnect.Backends.BasePairingHandler;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Helpers.SecurityHelpers.RsaHelper;
import org.kde.kdeconnect.NetworkPackage;

import java.io.*;
import java.nio.charset.Charset;
import java.security.PublicKey;
import java.util.UUID;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class BluetoothLink extends BaseLink {
    private final BluetoothSocket socket;
    private final BluetoothLinkProvider linkProvider;

    private boolean continueAccepting = true;

    private Thread receivingThread = new Thread(new Runnable() {
        @Override
        public void run() {
            StringBuilder sb = new StringBuilder();
            try {
                Reader reader = new InputStreamReader(socket.getInputStream(), "UTF-8");
                char[] buf = new char[512];
                while (continueAccepting) {
                    while (sb.indexOf("\n") == -1 && continueAccepting) {
                        int charsRead;
                        if ((charsRead = reader.read(buf)) > 0) {
                            sb.append(buf, 0, charsRead);
                        }
                    }

                    int endIndex = sb.indexOf("\n");
                    if (endIndex != -1) {
                        String message = sb.substring(0, endIndex + 1);
                        sb.delete(0, endIndex + 1);
                        processMessage(message);
                    }
                }
            } catch (IOException e) {
                Log.e("BluetoothLink/receiving", "Connection to " + socket.getRemoteDevice().getAddress() + " likely broken.", e);
                disconnect();
            }
        }

        private void processMessage(String message) {
            NetworkPackage np;
            try {
                np = NetworkPackage.unserialize(message);
            } catch (JSONException e) {
                Log.e("BluetoothLink/receiving", "Unable to parse message.", e);
                return;
            }

            if (np.getType().equals(NetworkPackage.PACKAGE_TYPE_ENCRYPTED)) {
                try {
                    np = RsaHelper.decrypt(np, privateKey);
                } catch(Exception e) {
                    Log.e("BluetoothLink/receiving", "Exception decrypting the package", e);
                }
            }

            if (np.hasPayloadTransferInfo()) {
                BluetoothSocket transferSocket = null;
                try {
                    UUID transferUuid = UUID.fromString(np.getPayloadTransferInfo().getString("uuid"));
                    transferSocket = socket.getRemoteDevice().createRfcommSocketToServiceRecord(transferUuid);
                    transferSocket.connect();
                    np.setPayload(transferSocket.getInputStream(), np.getPayloadSize());
                } catch (Exception e) {
                    if (transferSocket != null) {
                        try { transferSocket.close(); } catch(IOException ignored) { }
                    }
                    Log.e("BluetoothLink/receiving", "Unable to get payload", e);
                }
            }

            packageReceived(np);
        }
    });

    public BluetoothLink(Context context, BluetoothSocket socket, String deviceId, BluetoothLinkProvider linkProvider) {
        super(context, deviceId, linkProvider);
        this.socket = socket;
        this.linkProvider = linkProvider;
        receivingThread.start();
    }

    @Override
    public String getName() {
        return "BluetoothLink";
    }

    @Override
    public BasePairingHandler getPairingHandler(Device device, BasePairingHandler.PairingHandlerCallback callback) {
        return new BluetoothPairingHandler(device, callback);
    }

    public void disconnect() {
        if (socket == null) {
            return;
        }
        continueAccepting = false;
        try {
            socket.close();
        } catch (IOException e) {
        }
        linkProvider.disconnectedLink(this, getDeviceId(), socket);
    }

    private void sendMessage(NetworkPackage np) throws JSONException, IOException {
        byte[] message = np.serialize().getBytes(Charset.forName("UTF-8"));
        OutputStream socket = this.socket.getOutputStream();
        Log.i("BluetoothLink","Beginning to send message");
        socket.write(message);
        Log.i("BluetoothLink","Finished sending message");
    }

    @Override
    public boolean sendPackage(NetworkPackage np, Device.SendPackageStatusCallback callback) {
        return sendPackageInternal(np, callback, null);
    }

    @Override
    public boolean sendPackageEncrypted(NetworkPackage np, Device.SendPackageStatusCallback callback, PublicKey key) {
        return sendPackageInternal(np, callback, key);
    }

    private boolean sendPackageInternal(NetworkPackage np, final Device.SendPackageStatusCallback callback, PublicKey key) {

        /*if (!isConnected()) {
            Log.e("BluetoothLink", "sendPackageEncrypted failed: not connected");
            callback.sendFailure(new Exception("Not connected"));
            return;
        }*/

        try {
            BluetoothServerSocket serverSocket = null;
            if (np.hasPayload()) {
                UUID transferUuid = UUID.randomUUID();
                serverSocket = BluetoothAdapter.getDefaultAdapter()
                        .listenUsingRfcommWithServiceRecord("KDE Connect Transfer", transferUuid);
                JSONObject payloadTransferInfo = new JSONObject();
                payloadTransferInfo.put("uuid", transferUuid.toString());
                np.setPayloadTransferInfo(payloadTransferInfo);
            }

            if (key != null) {
                try {
                    np = RsaHelper.encrypt(np, key);
                } catch (Exception e) {
                    callback.onFailure(e);
                    return false;
                }
            }

            sendMessage(np);

            if (serverSocket != null) {
                BluetoothSocket transferSocket = serverSocket.accept();
                try {
                    serverSocket.close();

                    int idealBufferLength = 4096;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        idealBufferLength = transferSocket.getMaxReceivePacketSize();
                    }
                    byte[] buffer = new byte[idealBufferLength];
                    int bytesRead;
                    long progress = 0;
                    InputStream stream = np.getPayload();
                    while ((bytesRead = stream.read(buffer)) != -1) {
                        progress += bytesRead;
                        transferSocket.getOutputStream().write(buffer, 0, bytesRead);
                        if (np.getPayloadSize() > 0) {
                            callback.onProgressChanged((int) (100 * progress / np.getPayloadSize()));
                        }
                    }
                    transferSocket.getOutputStream().flush();
                    stream.close();
                } catch (Exception e) {
                    callback.onFailure(e);
                    return false;
                } finally {
                    try { transferSocket.close(); } catch (IOException ignored) { }
                }
            }

            callback.onSuccess();
            return true;
        } catch (Exception e) {
            callback.onFailure(e);
            return false;
        }
    }

    @Override
    public boolean linkShouldBeKeptAlive() {
        return receivingThread.isAlive();
    }

    /*
    public boolean isConnected() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            return socket.isConnected();
        } else {
            return true;
        }
    }
*/
}
