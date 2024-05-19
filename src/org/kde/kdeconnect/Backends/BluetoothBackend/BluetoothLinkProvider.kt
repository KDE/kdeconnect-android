/*
 * SPDX-FileCopyrightText: 2016 Saikrishna Arcot <saiarcot895@gmail.com>
 * SPDX-FileCopyrightText: 2024 Rob Emery <git@mintsoft.net>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.Backends.BluetoothBackend

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Network
import android.preference.PreferenceManager
import android.util.Base64
import android.util.Log
import org.apache.commons.io.IOUtils
import org.kde.kdeconnect.Backends.BaseLinkProvider
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceInfo
import org.kde.kdeconnect.DeviceInfo.Companion.fromIdentityPacketAndCert
import org.kde.kdeconnect.Helpers.DeviceHelper
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper
import org.kde.kdeconnect.Helpers.ThreadHelper.execute
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.UserInterface.SettingsFragment
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader
import java.security.cert.CertificateException
import java.util.UUID
import kotlin.text.Charsets.UTF_8

class BluetoothLinkProvider(private val context: Context) : BaseLinkProvider() {
    private val visibleDevices: MutableMap<String, BluetoothLink> = HashMap()
    private val sockets: MutableMap<BluetoothDevice?, BluetoothSocket> = HashMap()
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var serverRunnable: ServerRunnable? = null
    private var clientRunnable: ClientRunnable? = null

    @Throws(CertificateException::class)
    private fun addLink(identityPacket: NetworkPacket, link: BluetoothLink) {
        val deviceId = identityPacket.getString("deviceId")
        Log.i("BluetoothLinkProvider", "addLink to $deviceId")
        val oldLink = visibleDevices[deviceId]
        if (oldLink == link) {
            Log.e("BluetoothLinkProvider", "oldLink == link. This should not happen!")
            return
        }
        synchronized(visibleDevices) { visibleDevices.put(deviceId, link) }
        onConnectionReceived(link)
        link.startListening()
        link.packetReceived(identityPacket)
        if (oldLink != null) {
            Log.i("BluetoothLinkProvider", "Removing old connection to same device")
            oldLink.disconnect()
        }
    }

    init {
        if (bluetoothAdapter == null) {
            Log.e("BluetoothLinkProvider", "No bluetooth adapter found.")
        }
    }

    override fun onStart() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        if (!preferences.getBoolean(SettingsFragment.KEY_BLUETOOTH_ENABLED, false)) {
            return
        }
        if (bluetoothAdapter == null || bluetoothAdapter.isEnabled == false) {
            return
        }
        Log.i("BluetoothLinkProvider", "onStart called")

        //This handles the case when I'm the existing device in the network and receive a hello package
        clientRunnable = ClientRunnable()
        execute(clientRunnable!!)

        // I'm on a new network, let's be polite and introduce myself
        serverRunnable = ServerRunnable()
        execute(serverRunnable!!)
    }

    override fun onNetworkChange(network: Network?) {
        Log.i("BluetoothLinkProvider", "onNetworkChange called")
        onStop()
        onStart()
    }

    override fun onStop() {
        if (bluetoothAdapter == null || clientRunnable == null || serverRunnable == null) {
            return
        }
        Log.i("BluetoothLinkProvider", "onStop called")
        clientRunnable!!.stopProcessing()
        serverRunnable!!.stopProcessing()
    }

    override fun getName(): String {
        return "BluetoothLinkProvider"
    }

    override fun getPriority(): Int {
        return 10
    }

    fun disconnectedLink(link: BluetoothLink, remoteAddress: BluetoothDevice?) {
        Log.i("BluetoothLinkProvider", "disconnectedLink called")
        synchronized(sockets) { sockets.remove(remoteAddress) }
        synchronized(visibleDevices) { visibleDevices.remove(link.deviceId) }
        onConnectionLost(link)
    }

    private inner class ServerRunnable : Runnable {
        private var continueProcessing = true
        private var serverSocket: BluetoothServerSocket? = null
        fun stopProcessing() {
            continueProcessing = false
            try {
                IOUtils.close(serverSocket)
            } catch (e: IOException) {
                Log.e("KDEConnect", "Exception", e)
            }
        }

        override fun run() {
            serverSocket = try {
                bluetoothAdapter!!.listenUsingRfcommWithServiceRecord("KDE Connect", SERVICE_UUID)
            } catch (e: IOException) {
                Log.e("KDEConnect", "Exception", e)
                return
            } catch (e: SecurityException) {
                Log.e("KDEConnect", "Security Exception for CONNECT", e)

                val prefenceEditor = PreferenceManager.getDefaultSharedPreferences(context).edit()
                prefenceEditor.putBoolean(SettingsFragment.KEY_BLUETOOTH_ENABLED, false)
                prefenceEditor.apply()

                return
            }
            try {
                while (continueProcessing) {
                    val socket = serverSocket!!.accept()
                    connect(socket)
                }
            } catch (e: Exception) {
                Log.d("BTLinkProvider/Server", "Bluetooth Server error", e)
            }
        }

        @Throws(Exception::class)
        private fun connect(socket: BluetoothSocket) {
            synchronized(sockets) {
                if (sockets.containsKey(socket.remoteDevice)) {
                    Log.i("BTLinkProvider/Server", "Received duplicate connection from " + socket.remoteDevice.address)
                    socket.close()
                    return
                } else {
                    sockets.put(socket.remoteDevice, socket)
                }
            }
            Log.i("BTLinkProvider/Server", "Received connection from " + socket.remoteDevice.address)

            //Delay to let bluetooth initialize stuff correctly
            try {
                Thread.sleep(500)
            } catch (e: Exception) {
                synchronized(sockets) { sockets.remove(socket.remoteDevice) }
                throw e
            }
            try {
                ConnectionMultiplexer(socket).use { connection ->
                    val outputStream = connection.defaultOutputStream
                    val inputStream = connection.defaultInputStream
                    val myDeviceInfo = DeviceHelper.getDeviceInfo(context)
                    val np = myDeviceInfo.toIdentityPacket()
                    np["certificate"] = Base64.encodeToString(SslHelper.certificate.encoded, 0)
                    val message = np.serialize().toByteArray(UTF_8)
                    outputStream.write(message)
                    outputStream.flush()
                    Log.i("BTLinkProvider/Server", "Sent identity packet")

                    // Listen for the response
                    val sb = StringBuilder()
                    val reader: Reader = InputStreamReader(inputStream, UTF_8)
                    var charsRead = 0
                    val buf = CharArray(512)
                    while (sb.lastIndexOf("\n") == -1 && reader.read(buf).also { charsRead = it } != -1) {
                        sb.append(buf, 0, charsRead)
                    }
                    val response = sb.toString()
                    val identityPacket = NetworkPacket.unserialize(response)
                    if (!DeviceInfo.isValidIdentityPacket(identityPacket)) {
                        Log.w("BTLinkProvider/Server", "Invalid identity packet received.")
                        return
                    }
                    Log.i("BTLinkProvider/Server", "Received identity packet")
                    val pemEncodedCertificateString = identityPacket.getString("certificate")
                    val base64CertificateString = pemEncodedCertificateString
                            .replace("-----BEGIN CERTIFICATE-----\n", "")
                            .replace("-----END CERTIFICATE-----\n", "")
                    val pemEncodedCertificateBytes = Base64.decode(base64CertificateString, 0)
                    val certificate = SslHelper.parseCertificate(pemEncodedCertificateBytes)
                    val deviceInfo = fromIdentityPacketAndCert(identityPacket, certificate)
                    Log.i("BTLinkProvider/Server", "About to create link")
                    val link = BluetoothLink(context, connection,
                            inputStream, outputStream, socket.remoteDevice,
                            deviceInfo, this@BluetoothLinkProvider)
                    Log.i("BTLinkProvider/Server", "About to addLink")
                    addLink(identityPacket, link)
                    Log.i("BTLinkProvider/Server", "Link Added")
                }
            } catch (e: Exception) {
                synchronized(sockets) {
                    sockets.remove(socket.remoteDevice)
                    Log.i("BTLinkProvider/Server", "Exception thrown, removing socket", e)
                }
                throw e
            }
        }
    }

    object ClientRunnableSingleton {
        val connectionThreads: MutableMap<BluetoothDevice?, Thread> = HashMap()
    }

    private inner class ClientRunnable : BroadcastReceiver(), Runnable {
        private var continueProcessing = true
        fun stopProcessing() {
            continueProcessing = false
        }

        override fun run() {
            try {
                Log.i("ClientRunnable", "run called")
                val filter = IntentFilter(BluetoothDevice.ACTION_UUID)
                context.registerReceiver(this, filter)
                Log.i("ClientRunnable", "receiver registered")
                if (continueProcessing) {
                    Log.i("ClientRunnable", "before connectToDevices")
                    discoverDeviceServices()
                    Log.i("ClientRunnable", "after connectToDevices")
                    try {
                        Thread.sleep(15000)
                    } catch (ignored: InterruptedException) {
                    }
                }
                Log.i("ClientRunnable", "unregisteringReceiver")
                context.unregisterReceiver(this)
            } catch (se: SecurityException) {
                Log.w("BluetoothLinkProvider", se)
            }
        }

        /**
         * Tell Android to use ServiceDiscoveryProtocol to update the
         * list of available UUIDs associated with Bluetooth devices
         * that are bluetooth-paired-but-not-yet-kde-paired
         */
        @SuppressLint("MissingPermission")
        private fun discoverDeviceServices() {
            Log.i("ClientRunnable", "connectToDevices called")
            val pairedDevices = bluetoothAdapter!!.bondedDevices
            if (pairedDevices == null) {
                Log.i("BluetoothLinkProvider", "Paired Devices is NULL")
                return
            }
            Log.i("BluetoothLinkProvider", "Bluetooth adapter paired devices: " + pairedDevices.size)

            // Loop through Bluetooth paired devices
            for (device in pairedDevices) {
                // If a socket exists for this, then it has been paired in KDE
                if (sockets.containsKey(device)) {
                    continue
                }
                Log.i("ClientRunnable", "Calling fetchUuidsWithSdp for device: $device")
                device.fetchUuidsWithSdp()
                val deviceUuids = device.uuids
                if (deviceUuids != null) {
                    for (thisUuid in deviceUuids) {
                        Log.i("ClientRunnable", "device $device uuid: $thisUuid")
                    }
                }
            }
        }

        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (BluetoothDevice.ACTION_UUID == action) {
                Log.i("BluetoothLinkProvider", "Action matches")
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val activeUuids = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID)
                if (sockets.containsKey(device)) {
                    Log.i("BluetoothLinkProvider", "sockets contains device")
                    return
                }
                if (activeUuids == null) {
                    Log.i("BluetoothLinkProvider", "activeUuids is null")
                    return
                }
                for (uuid in activeUuids) {
                    if (uuid.toString() == SERVICE_UUID.toString() || uuid.toString() == BYTE_REVERSED_SERVICE_UUID.toString()) {
                        Log.i("BluetoothLinkProvider", "calling connectToDevice for device: " + device!!.address)
                        connectToDevice(device)
                        return
                    }
                }
            }
        }

        private fun connectToDevice(device: BluetoothDevice?) {
            synchronized(ClientRunnableSingleton.connectionThreads) {
                if (!ClientRunnableSingleton.connectionThreads.containsKey(device) || !ClientRunnableSingleton.connectionThreads[device]!!.isAlive) {
                    val connectionThread = Thread(ClientConnect(device))
                    connectionThread.start()
                    ClientRunnableSingleton.connectionThreads[device] = connectionThread
                }
            }
        }
    }

    private inner class ClientConnect(private val device: BluetoothDevice?) : Runnable {
        override fun run() {
            connectToDevice()
        }

        private fun connectToDevice() {
            val socket: BluetoothSocket
            try {
                Log.i("BTLinkProvider/Client", "Cancelling Discovery")
                bluetoothAdapter!!.cancelDiscovery()
                Log.i("BTLinkProvider/Client", "Creating RFCommSocket to Service Record")
                socket = device!!.createRfcommSocketToServiceRecord(SERVICE_UUID)
                Log.i("BTLinkProvider/Client", "Connecting to ServiceRecord Socket")
                socket.connect()
                synchronized(sockets) { sockets.put(device, socket) }
            } catch (e: IOException) {
                Log.e("BTLinkProvider/Client", "Could not connect to KDE Connect service on " + device!!.address, e)
                return
            } catch (e: SecurityException) {
                Log.e("BTLinkProvider/Client", "Security Exception connecting to " + device!!.address, e)
                return
            }
            Log.i("BTLinkProvider/Client", "Connected to " + device.address)
            try {
                //Delay to let bluetooth initialize stuff correctly
                Thread.sleep(500)
                val connection = ConnectionMultiplexer(socket)
                val outputStream = connection.defaultOutputStream
                val inputStream = connection.defaultInputStream
                Log.i("BTLinkProvider/Client", "Device: " + device.address + " Before inputStream.read()")
                var character = 0
                val sb = StringBuilder()
                while (sb.lastIndexOf("\n") == -1 && inputStream.read().also { character = it } != -1) {
                    sb.append(character.toChar())
                }
                Log.i("BTLinkProvider/Client", "Device: " + device.address + " Before sb.toString()")
                val message = sb.toString()
                Log.i("BTLinkProvider/Client", "Device: " + device.address + " Before unserialize (message: '" + message + "')")
                val identityPacket = NetworkPacket.unserialize(message)
                Log.i("BTLinkProvider/Client", "Device: " + device.address + " After unserialize")

                if (!DeviceInfo.isValidIdentityPacket(identityPacket)) {
                    Log.w("BTLinkProvider/Client", "Invalid identity packet received.")
                    connection.close()
                    return
                }

                Log.i("BTLinkProvider/Client", "Received identity packet")
                val myId = DeviceHelper.getDeviceId(context)
                if (identityPacket.getString("deviceId") == myId) {
                    // Probably won't happen, but just to be safe
                    connection.close()
                    return
                }
                if (visibleDevices.containsKey(identityPacket.getString("deviceId"))) {
                    return
                }
                Log.i("BTLinkProvider/Client", "identity packet received, creating link")
                val pemEncodedCertificateString = identityPacket.getString("certificate")
                val base64CertificateString = pemEncodedCertificateString
                        .replace("-----BEGIN CERTIFICATE-----\n", "")
                        .replace("-----END CERTIFICATE-----\n", "")
                val pemEncodedCertificateBytes = Base64.decode(base64CertificateString, 0)
                val certificate = SslHelper.parseCertificate(pemEncodedCertificateBytes)
                val deviceInfo = fromIdentityPacketAndCert(identityPacket, certificate)
                val link = BluetoothLink(context, connection, inputStream, outputStream,
                        socket.remoteDevice, deviceInfo, this@BluetoothLinkProvider)
                val myDeviceInfo = DeviceHelper.getDeviceInfo(context)
                val np2 = myDeviceInfo.toIdentityPacket()
                np2["certificate"] = Base64.encodeToString(SslHelper.certificate.encoded, 0)
                Log.i("BTLinkProvider/Client", "about to send packet np2")
                link.sendPacket(np2, object : Device.SendPacketStatusCallback() {
                    override fun onSuccess() {
                        try {
                            addLink(identityPacket, link)
                        } catch (e: CertificateException) {
                            e.printStackTrace()
                        }
                    }

                    override fun onFailure(e: Throwable) {}
                }, true)
            } catch (e: Exception) {
                Log.e("BTLinkProvider/Client", "Connection lost/disconnected on " + device.address, e)
                synchronized(sockets) { sockets.remove(device, socket) }
            }
        }
    }

    companion object {
        private val SERVICE_UUID = UUID.fromString("185f3df4-3268-4e3f-9fca-d4d5059915bd")
        private val BYTE_REVERSED_SERVICE_UUID = UUID(java.lang.Long.reverseBytes(SERVICE_UUID.leastSignificantBits), java.lang.Long.reverseBytes(SERVICE_UUID.mostSignificantBits))
    }
}
