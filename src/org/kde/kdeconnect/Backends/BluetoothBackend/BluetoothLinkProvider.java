/*
 * SPDX-FileCopyrightText: 2016 Saikrishna Arcot <saiarcot895@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Backends.BluetoothBackend;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Network;
import android.os.Parcelable;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;

import org.apache.commons.io.IOUtils;
import org.kde.kdeconnect.Backends.BaseLinkProvider;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.DeviceInfo;
import org.kde.kdeconnect.Helpers.DeviceHelper;
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper;
import org.kde.kdeconnect.Helpers.ThreadHelper;
import org.kde.kdeconnect.NetworkPacket;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import kotlin.text.Charsets;

public class BluetoothLinkProvider extends BaseLinkProvider {

    private static final UUID SERVICE_UUID = UUID.fromString("185f3df4-3268-4e3f-9fca-d4d5059915bd");
    private static final int REQUEST_ENABLE_BT = 48;

    private final Context context;
    private final Map<String, BluetoothLink> visibleDevices = new HashMap<>();
    private final Map<BluetoothDevice, BluetoothSocket> sockets = new HashMap<>();

    private final BluetoothAdapter bluetoothAdapter;

    private ServerRunnable serverRunnable;
    private ClientRunnable clientRunnable;

    private void addLink(NetworkPacket identityPacket, BluetoothLink link) throws CertificateException {
        String deviceId = identityPacket.getString("deviceId");
        Log.i("BluetoothLinkProvider", "addLink to " + deviceId);
        BluetoothLink oldLink = visibleDevices.get(deviceId);
        if (oldLink == link) {
            Log.e("BluetoothLinkProvider", "oldLink == link. This should not happen!");
            return;
        }
        visibleDevices.put(deviceId, link);
        onConnectionReceived(link);
        link.startListening();
        link.packetReceived(identityPacket);
        if (oldLink != null) {
            Log.i("BluetoothLinkProvider", "Removing old connection to same device");
            oldLink.disconnect();
        }
    }

    public BluetoothLinkProvider(Context context) {
        this.context = context;

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.e("BluetoothLinkProvider", "No bluetooth adapter found.");
        }
    }

    @Override
    public void onStart() {
        if (bluetoothAdapter == null) {
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            Log.e("BluetoothLinkProvider", "Bluetooth adapter not enabled.");
            // TODO: next line needs to be called from an existing activity, so move it?
            // startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            // TODO: Check result of the previous command, whether the user allowed bluetooth or not.
            return;
        }

        //This handles the case when I'm the existing device in the network and receive a hello package
        clientRunnable = new ClientRunnable();
        ThreadHelper.execute(clientRunnable);

        // I'm on a new network, let's be polite and introduce myself
        serverRunnable = new ServerRunnable();
        ThreadHelper.execute(serverRunnable);
    }

    @Override
    public void onNetworkChange(@Nullable Network network) {
        onStop();
        onStart();
    }

    @Override
    public void onStop() {
        if (bluetoothAdapter == null || clientRunnable == null || serverRunnable == null) {
            return;
        }

        clientRunnable.stopProcessing();
        serverRunnable.stopProcessing();
    }

    @Override
    public String getName() {
        return "BluetoothLinkProvider";
    }

    public void disconnectedLink(BluetoothLink link, BluetoothDevice remoteAddress) {
        sockets.remove(remoteAddress);
        visibleDevices.remove(link.getDeviceId());
        onConnectionLost(link);
    }

    private class ServerRunnable implements Runnable {

        private boolean continueProcessing = true;
        private BluetoothServerSocket serverSocket;

        void stopProcessing() {
            continueProcessing = false;
            try {
                IOUtils.close(serverSocket);
            } catch (IOException e) {
                Log.e("KDEConnect", "Exception", e);
            }
        }

        @Override
        public void run() {
            try {
                serverSocket = bluetoothAdapter
                        .listenUsingRfcommWithServiceRecord("KDE Connect", SERVICE_UUID);
            } catch (IOException e) {
                Log.e("KDEConnect", "Exception", e);
                return;
            }

            while (continueProcessing) {
                try {
                    BluetoothSocket socket = serverSocket.accept();
                    connect(socket);
                } catch (Exception e) {
                    Log.e("BTLinkProvider/Server", "Bluetooth error", e);
                }
            }
        }

        private void connect(BluetoothSocket socket) throws Exception {
            synchronized (sockets) {
                if (sockets.containsKey(socket.getRemoteDevice())) {
                    Log.i("BTLinkProvider/Server", "Received duplicate connection from " + socket.getRemoteDevice().getAddress());
                    socket.close();
                    return;
                } else {
                    sockets.put(socket.getRemoteDevice(), socket);
                }
            }

            Log.i("BTLinkProvider/Server", "Received connection from " + socket.getRemoteDevice().getAddress());

            //Delay to let bluetooth initialize stuff correctly
            try {
                Thread.sleep(500);
            } catch (Exception e) {
                synchronized (sockets) {
                    sockets.remove(socket.getRemoteDevice());
                }
                throw e;
            }

            try (ConnectionMultiplexer connection = new ConnectionMultiplexer(socket)) {
                OutputStream outputStream = connection.getDefaultOutputStream();
                InputStream inputStream = connection.getDefaultInputStream();

                DeviceInfo myDeviceInfo = DeviceHelper.getDeviceInfo(context);
                NetworkPacket np = myDeviceInfo.toIdentityPacket();
                np.set("certificate", Base64.encodeToString(SslHelper.certificate.getEncoded(), 0));

                byte[] message = np.serialize().getBytes(Charsets.UTF_8);
                outputStream.write(message);
                outputStream.flush();

                Log.i("BTLinkProvider/Server", "Sent identity packet");

                // Listen for the response
                StringBuilder sb = new StringBuilder();
                Reader reader = new InputStreamReader(inputStream, Charsets.UTF_8);
                int charsRead;
                char[] buf = new char[512];
                while (sb.lastIndexOf("\n") == -1 && (charsRead = reader.read(buf)) != -1) {
                    sb.append(buf, 0, charsRead);
                }

                String response = sb.toString();
                final NetworkPacket identityPacket = NetworkPacket.unserialize(response);

                if (!identityPacket.getType().equals(NetworkPacket.PACKET_TYPE_IDENTITY)) {
                    Log.e("BTLinkProvider/Server", "2 Expecting an identity packet");
                    return;
                }

                Log.i("BTLinkProvider/Server", "Received identity packet");

                String certificateString = identityPacket.getString("certificate");
                byte[] certificateBytes = Base64.decode(certificateString, 0);
                Certificate certificate = SslHelper.parseCertificate(certificateBytes);

                DeviceInfo deviceInfo = DeviceInfo.fromIdentityPacketAndCert(identityPacket, certificate);

                BluetoothLink link = new BluetoothLink(context, connection,
                        inputStream, outputStream, socket.getRemoteDevice(),
                        deviceInfo, BluetoothLinkProvider.this);
                addLink(identityPacket, link);
            } catch (Exception e) {
                synchronized (sockets) {
                    sockets.remove(socket.getRemoteDevice());
                }
                throw e;
            }
        }
    }

    private class ClientRunnable extends BroadcastReceiver implements Runnable {

        private boolean continueProcessing = true;
        private final Map<BluetoothDevice, Thread> connectionThreads = new HashMap<>();

        void stopProcessing() {
            continueProcessing = false;
        }

        @Override
        public void run() {
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_UUID);
            context.registerReceiver(this, filter);

            if (continueProcessing) {
                connectToDevices();
                try {
                    Thread.sleep(15000);
                } catch (InterruptedException ignored) {
                }
            }

            context.unregisterReceiver(this);
        }

        private void connectToDevices() {
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            Log.i("BluetoothLinkProvider", "Bluetooth adapter paired devices: " + pairedDevices.size());

            // Loop through paired devices
            for (BluetoothDevice device : pairedDevices) {
                if (sockets.containsKey(device)) {
                    continue;
                }

                device.fetchUuidsWithSdp();
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_UUID.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Parcelable[] activeUuids = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);

                if (sockets.containsKey(device)) {
                    return;
                }

                if (activeUuids == null) {
                    return;
                }

                for (Parcelable uuid : activeUuids) {
                    if (uuid.toString().equals(SERVICE_UUID.toString())) {
                        connectToDevice(device);
                        return;
                    }
                }
            }
        }

        private void connectToDevice(BluetoothDevice device) {
            if (!connectionThreads.containsKey(device) || !connectionThreads.get(device).isAlive()) {
                Thread connectionThread = new Thread(new ClientConnect(device));
                connectionThread.start();
                connectionThreads.put(device, connectionThread);
            }
        }


    }

    private class ClientConnect implements Runnable {

        private final BluetoothDevice device;

        ClientConnect(BluetoothDevice device) {
            this.device = device;
        }

        @Override
        public void run() {
            connectToDevice();
        }

        private void connectToDevice() {
            BluetoothSocket socket;
            try {
                socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID);
                socket.connect();
                sockets.put(device, socket);
            } catch (IOException e) {
                Log.e("BTLinkProvider/Client", "Could not connect to KDE Connect service on " + device.getAddress(), e);
                return;
            }

            Log.i("BTLinkProvider/Client", "Connected to " + device.getAddress());

            try {
                //Delay to let bluetooth initialize stuff correctly
                Thread.sleep(500);
                ConnectionMultiplexer connection = new ConnectionMultiplexer(socket);
                OutputStream outputStream = connection.getDefaultOutputStream();
                InputStream inputStream = connection.getDefaultInputStream();

                int character;
                StringBuilder sb = new StringBuilder();
                while (sb.lastIndexOf("\n") == -1 && (character = inputStream.read()) != -1) {
                    sb.append((char) character);
                }

                String message = sb.toString();
                final NetworkPacket identityPacket = NetworkPacket.unserialize(message);

                if (!identityPacket.getType().equals(NetworkPacket.PACKET_TYPE_IDENTITY)) {
                    Log.e("BTLinkProvider/Client", "1 Expecting an identity packet");
                    socket.close();
                    return;
                }

                Log.i("BTLinkProvider/Client", "Received identity packet");

                String myId = DeviceHelper.getDeviceId(context);
                if (identityPacket.getString("deviceId").equals(myId)) {
                    // Probably won't happen, but just to be safe
                    connection.close();
                    return;
                }

                if (visibleDevices.containsKey(identityPacket.getString("deviceId"))) {
                    return;
                }

                Log.i("BTLinkProvider/Client", "identity packet received, creating link");

                String certificateString = identityPacket.getString("certificate");
                byte[] certificateBytes = Base64.decode(certificateString, 0);
                Certificate certificate = SslHelper.parseCertificate(certificateBytes);
                DeviceInfo deviceInfo = DeviceInfo.fromIdentityPacketAndCert(identityPacket, certificate);

                final BluetoothLink link = new BluetoothLink(context, connection, inputStream, outputStream,
                        socket.getRemoteDevice(), deviceInfo, BluetoothLinkProvider.this);

                DeviceInfo myDeviceInfo = DeviceHelper.getDeviceInfo(context);
                NetworkPacket np2 = myDeviceInfo.toIdentityPacket();
                np2.set("certificate", Base64.encodeToString(SslHelper.certificate.getEncoded(), 0));

                link.sendPacket(np2, new Device.SendPacketStatusCallback() {
                    @Override
                    public void onSuccess() {
                        try {
                            addLink(identityPacket, link);
                        } catch (CertificateException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onFailure(Throwable e) {

                    }
                }, true);
            } catch (Exception e) {
                Log.e("BTLinkProvider/Client", "Connection lost/disconnected on " + device.getAddress(), e);
            }
        }
    }
}
