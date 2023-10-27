/*
 * SPDX-FileCopyrightText: 2016 Saikrishna Arcot <saiarcot895@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Backends.BluetoothBackend;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.json.JSONException;
import org.json.JSONObject;
import org.kde.kdeconnect.Backends.BaseLink;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.DeviceInfo;
import org.kde.kdeconnect.NetworkPacket;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.util.UUID;

import kotlin.text.Charsets;

public class BluetoothLink extends BaseLink {
    private final ConnectionMultiplexer connection;
    private final InputStream input;
    private final OutputStream output;
    private final BluetoothDevice remoteAddress;
    private final BluetoothLinkProvider linkProvider;
    private final DeviceInfo deviceInfo;

    private boolean continueAccepting = true;

    private final Thread receivingThread = new Thread(new Runnable() {
        @Override
        public void run() {
            StringBuilder sb = new StringBuilder();
            try {
                Reader reader = new InputStreamReader(input, Charsets.UTF_8);
                char[] buf = new char[512];
                while (continueAccepting) {
                    while (sb.indexOf("\n") == -1 && continueAccepting) {
                        int charsRead;
                        if ((charsRead = reader.read(buf)) > 0) {
                            sb.append(buf, 0, charsRead);
                        }
                        if (charsRead < 0) {
                            disconnect();
                            return;
                        }
                    }
                    if (!continueAccepting) break;

                    int endIndex = sb.indexOf("\n");
                    if (endIndex != -1) {
                        String message = sb.substring(0, endIndex + 1);
                        sb.delete(0, endIndex + 1);
                        processMessage(message);
                    }
                }
            } catch (IOException e) {
                Log.e("BluetoothLink/receiving", "Connection to " + remoteAddress.getAddress() + " likely broken.", e);
                disconnect();
            }
        }

        private void processMessage(String message) {
            NetworkPacket np;
            try {
                np = NetworkPacket.unserialize(message);
            } catch (JSONException e) {
                Log.e("BluetoothLink/receiving", "Unable to parse message.", e);
                return;
            }

            if (np.hasPayloadTransferInfo()) {
                try {
                    UUID transferUuid = UUID.fromString(np.getPayloadTransferInfo().getString("uuid"));
                    InputStream payloadInputStream = connection.getChannelInputStream(transferUuid);
                    np.setPayload(new NetworkPacket.Payload(payloadInputStream, np.getPayloadSize()));
                } catch (Exception e) {
                    Log.e("BluetoothLink/receiving", "Unable to get payload", e);
                }
            }

            packetReceived(np);
        }
    });

    public BluetoothLink(Context context, ConnectionMultiplexer connection, InputStream input, OutputStream output, BluetoothDevice remoteAddress, DeviceInfo deviceInfo, BluetoothLinkProvider linkProvider) {
        super(context, linkProvider);
        this.connection = connection;
        this.input = input;
        this.output = output;
        this.deviceInfo = deviceInfo;
        this.remoteAddress = remoteAddress;
        this.linkProvider = linkProvider;
    }

    public void startListening() {
        this.receivingThread.start();
    }

    @Override
    public String getName() {
        return "BluetoothLink";
    }

    @Override
    public DeviceInfo getDeviceInfo() {
        return deviceInfo;
    }

    public void disconnect() {
        if (connection == null) {
            return;
        }
        continueAccepting = false;
        try {
            connection.close();
        } catch (IOException ignored) {
        }
        linkProvider.disconnectedLink(this, remoteAddress);
    }

    private void sendMessage(NetworkPacket np) throws JSONException, IOException {
        byte[] message = np.serialize().getBytes(Charsets.UTF_8);
        output.write(message);
    }

    @WorkerThread
    @Override
    public boolean sendPacket(@NonNull NetworkPacket np, @NonNull Device.SendPacketStatusCallback callback, boolean sendPayloadFromSameThread) throws IOException {
        // sendPayloadFromSameThread is ignored, we always send from the same thread!

        /*if (!isConnected()) {
            Log.e("BluetoothLink", "sendPacketEncrypted failed: not connected");
            callback.sendFailure(new Exception("Not connected"));
            return;
        }*/

        try {
            UUID transferUuid = null;
            if (np.hasPayload()) {
                transferUuid = connection.newChannel();
                JSONObject payloadTransferInfo = new JSONObject();
                payloadTransferInfo.put("uuid", transferUuid.toString());
                np.setPayloadTransferInfo(payloadTransferInfo);
            }

            sendMessage(np);

            if (transferUuid != null) {
                try (OutputStream payloadStream = connection.getChannelOutputStream(transferUuid)) {
                    int BUFFER_LENGTH = 1024;
                    byte[] buffer = new byte[BUFFER_LENGTH];

                    int bytesRead;
                    long progress = 0;
                    InputStream stream = np.getPayload().getInputStream();
                    while ((bytesRead = stream.read(buffer)) != -1) {
                        progress += bytesRead;
                        payloadStream.write(buffer, 0, bytesRead);
                        if (np.getPayloadSize() > 0) {
                            callback.onPayloadProgressChanged((int) (100 * progress / np.getPayloadSize()));
                        }
                    }
                    payloadStream.flush();
                } catch (Exception e) {
                    callback.onFailure(e);
                    return false;
                }
            }

            callback.onSuccess();
            return true;
        } catch (Exception e) {
            callback.onFailure(e);
            return false;
        }
    }
}
