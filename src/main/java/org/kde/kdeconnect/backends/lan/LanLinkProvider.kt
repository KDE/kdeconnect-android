/*
 * SPDX-FileCopyrightText: 2026 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.backends.lan

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.net.Network
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import org.kde.kdeconnect.DeviceInfo
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.backends.BaseLink
import org.kde.kdeconnect.backends.BaseLinkProvider
import org.kde.kdeconnect.extensions.closeSafe
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.helpers.ThreadHelper
import org.kde.kdeconnect.helpers.TrustedDevices
import org.kde.kdeconnect.helpers.TrustedNetworkHelper
import org.kde.kdeconnect.helpers.isPrivateAddress
import org.kde.kdeconnect.helpers.readLineBounded
import org.kde.kdeconnect.helpers.security.SslHelper
import org.kde.kdeconnect.ui.CustomDevicesActivity
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.cert.CertificateException
import java.util.concurrent.ConcurrentHashMap
import javax.net.SocketFactory
import javax.net.ssl.HandshakeCompletedEvent
import javax.net.ssl.SSLSocket

/**
 * This LanLinkProvider creates [LanLink]s to other devices on the same network.
 */
class LanLinkProvider(private val context: Context) : BaseLinkProvider() {

    private val visibleDevices = ConcurrentHashMap<String, LanLink>() // Links by device id

    private val mdnsDiscovery = MdnsDiscovery(context, this)

    private val lastConnectionTimeByDeviceId = ConcurrentHashMap<String, Long>()
    private val lastConnectionTimeByIp = ConcurrentHashMap<InetAddress, Long>()

    private val customDevicePreferences: SharedPreferences =
        context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)

    @Volatile
    private var customDeviceAddresses = emptyList<InetAddress>()

    private val customDeviceListener = OnSharedPreferenceChangeListener { _, key ->
        if (key == CustomDevicesActivity.KEY_CUSTOM_DEVICE_LIST_PREFERENCE) {
            updateCustomDevicesList()
        }
    }

    fun updateCustomDevicesList() {
        customDeviceAddresses = CustomDevicesActivity.getCustomDeviceList(context).flatMap {
            runCatching {
                InetAddress.getAllByName(it.host).toList()
            }.getOrDefault(emptyList())
        }
    }

    @Volatile
    private var tcpServer: ServerSocket? = null
    @Volatile
    private var udpServer: DatagramSocket? = null

    private val networkChangeScope = CoroutineScope(Dispatchers.IO)
    private var networkChangeJob: Job? = null
    @Volatile
    private var isStopped = true

    fun hasDevice(id: String): Boolean {
        return visibleDevices.containsKey(id)
    }

    override fun onConnectionLost(link: BaseLink) {
        val deviceId = link.deviceId
        // Do not let an old connection remove its replacement.
        if (visibleDevices.remove(deviceId, link)) {
            super.onConnectionLost(link)
        }
    }

    // Another device discovered us and is connecting to us via TCP. The first thing they send should be their connection packet.
    @WorkerThread
    @Throws(IOException::class, CertificateException::class)
    private fun tcpPacketReceived(socket: Socket) {
        val address = socket.getInetAddress()

        if (rateLimitByIp(address)) {
            Log.i("LanLinkProvider", "Discarding second TCP packet from the same ip $address received too quickly" )
            socket.closeSafe()
            return
        }

        if (!isAllowedAddress(address)) {
            Log.i("LanLinkProvider", "Discarding TCP packet from a non-local IP: $address")
            socket.closeSafe()
            return
        }

        val connectionPacket: NetworkPacket
        try {
            // We don't use a BufferedInputStream on purpose, since BufferedReader reads ahead and would require
            // us to keep a single BufferedInputStream instance and pass it around to make sure we don't lose data.
            // This means we are readying byte by byte directly from the OS, which is slow, but only for the handshake.
            val message = readLineBounded(socket.getInputStream(), MAX_IDENTITY_PACKET_SIZE)
            //Log.e("TcpListener", "Received TCP packet: $message");
            connectionPacket = NetworkPacket.unserialize(message)
        } catch (e: Exception) {
            Log.e("KDE/LanLinkProvider", "Exception while receiving TCP packet", e)
            socket.closeSafe()
            return
        }

        if (!DeviceInfo.isValidConnectionPacket(connectionPacket)) {
            Log.w("KDE/LanLinkProvider", "Invalid connection packet received.")
            socket.closeSafe()
            return
        }

        val deviceId = connectionPacket.getString("deviceId")
        if (deviceId == DeviceHelper.getDeviceId(context)) {
            // Ignore connections from myself
            socket.closeSafe()
            return
        }

        if (rateLimitByDeviceId(deviceId)) {
            Log.i("LanLinkProvider", "Discarding second connection from the same device $deviceId received too quickly")
            socket.closeSafe()
            return
        }

        val deviceTrusted = TrustedDevices.isTrustedDevice(context, deviceId)
        if (!deviceTrusted && !TrustedNetworkHelper.isTrustedNetwork(context)) {
            Log.i("KDE/LanLinkProvider", "Ignoring connection because the device is not trusted and I'm not on a trusted network.")
            socket.closeSafe()
            return
        }

        Log.i("KDE/LanLinkProvider", "Connection packet received from a TCP connection from $deviceId")

        val targetDeviceId = connectionPacket.getString("targetDeviceId")
        val targetProtocolVersion = connectionPacket.getIntOrNull("targetProtocolVersion")
        if (targetDeviceId != DeviceHelper.getDeviceId(context)) {
            Log.e("KDE/LanLinkProvider", "Received a connection request for a device that isn't me: $targetDeviceId")
            socket.closeSafe()
            return
        }
        if (targetProtocolVersion != null && targetProtocolVersion != DeviceHelper.PROTOCOL_VERSION) {
            Log.e("KDE/LanLinkProvider", "Received a connection request for a protocol version that isn't mine: $targetProtocolVersion")
            socket.closeSafe()
            return
        }

        val protocolVersion = connectionPacket.getInt("protocolVersion", 0)
        if (protocolVersion < 8) {
            Log.w("KDE/LanLinkProvider", "Ignoring device $deviceId using too old protocol version $protocolVersion")
            socket.closeSafe()
            return
        }

        if (deviceTrusted && isProtocolDowngrade(deviceId, protocolVersion)) {
            Log.w("KDE/LanLinkProvider", "Refusing to connect to a device using an older protocol version:$protocolVersion")
            socket.closeSafe()
            return
        }


        startSslHandshake(socket, deviceId, protocolVersion, ConnectionStarted.Locally, deviceTrusted)
    }

    private fun isAllowedAddress(address: InetAddress): Boolean {
        return isPrivateAddress(address) || customDeviceAddresses.contains(address)
    }

    fun rateLimitByIp(address: InetAddress): Boolean {
        val now = SystemClock.elapsedRealtime()
        val last = lastConnectionTimeByIp[address]
        if (last != null && (last + MILLIS_DELAY_BETWEEN_CONNECTIONS_TO_SAME_DEVICE > now)) {
            return true
        }
        lastConnectionTimeByIp[address] = now
        if (lastConnectionTimeByIp.size > MAX_RATE_LIMIT_ENTRIES) {
            lastConnectionTimeByIp.entries.removeIf { it.value + MILLIS_DELAY_BETWEEN_CONNECTIONS_TO_SAME_DEVICE < now }
        }
        return false
    }

    fun rateLimitByDeviceId(deviceId: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        val last = lastConnectionTimeByDeviceId[deviceId]
        if (last != null && (last + MILLIS_DELAY_BETWEEN_CONNECTIONS_TO_SAME_DEVICE > now)) {
            return true
        }
        lastConnectionTimeByDeviceId[deviceId] = now
        if (lastConnectionTimeByDeviceId.size > MAX_RATE_LIMIT_ENTRIES) {
            lastConnectionTimeByDeviceId.entries.removeIf { it.value + MILLIS_DELAY_BETWEEN_CONNECTIONS_TO_SAME_DEVICE < now }
        }
        return false
    }

    // We received their UDP broadcast and should connect to their TCP socket.
    @WorkerThread
    private fun udpPacketReceived(packet: DatagramPacket) {
        val address = packet.address

        if (rateLimitByIp(address)) {
            Log.i("LanLinkProvider", "Discarding second UDP packet from the same ip $address received too quickly")
            return
        }

        val message = String(packet.data, 0, packet.length, Charsets.UTF_8)
        val discoveryPacket: NetworkPacket
        try {
            discoveryPacket = NetworkPacket.unserialize(message)
        } catch (e: SerializationException) {
            Log.w("KDE/LanLinkProvider", "Invalid UDP discovery packet received: ${e.message}")
            return
        }
        if (!DeviceInfo.isValidUdpDiscoveryPacket(discoveryPacket)) {
            Log.w("KDE/LanLinkProvider", "Invalid UDP discovery packet received.")
            return
        }

        val deviceId = discoveryPacket.getString("deviceId")
        if (deviceId == DeviceHelper.getDeviceId(context)) {
            // Ignore my own broadcast
            return
        }

        val protocolVersion = discoveryPacket.getInt("protocolVersion", 0)
        val tcpPort = discoveryPacket.getInt("tcpPort", -1)

        deviceDiscovered(address, tcpPort, deviceId, protocolVersion, null, false)
    }

    /**
     * We discovered a device (via mDNS or UDP broadcast) and want to connect to it.
     * Blocks until the handshake is completed, so it should be called on a worker thread.
     *
     * @param network                   if not null, the network to which the TCP socket will be bound
     * @param reverseConnectionOnError  if connecting fails, send them a UDP discovery packet so they try to connect to us instead
     */
    @WorkerThread
    fun deviceDiscovered(address: InetAddress, port: Int, deviceId: String, protocolVersion: Int, network: Network?, reverseConnectionOnError: Boolean) {
        if (protocolVersion < 8) {
            Log.w("KDE/LanLinkProvider", "Ignoring device $deviceId using too old protocol version $protocolVersion")
            return
        }

        if (port !in MIN_PORT..MAX_PORT) {
            Log.e("LanLinkProvider", "TCP port outside of kdeconnect's range")
            return
        }

        if (!isAllowedAddress(address)) {
            Log.i("LanLinkProvider", "Not connecting to a non-local IP: $address")
            return
        }

        if (rateLimitByDeviceId(deviceId)) {
            Log.i("LanLinkProvider", "Discarding second discovery of the same device $deviceId received too quickly")
            return
        }

        val deviceTrusted = TrustedDevices.isTrustedDevice(context, deviceId)
        if (!deviceTrusted && !TrustedNetworkHelper.isTrustedNetwork(context)) {
            Log.i("KDE/LanLinkProvider", "Ignoring discovered device because it is not trusted and I'm not on a trusted network.")
            return
        }

        if (deviceTrusted && isProtocolDowngrade(deviceId, protocolVersion)) {
            Log.w("KDE/LanLinkProvider", "Refusing to connect to a device using an older protocol version:$protocolVersion")
            return
        }

        Log.i("KDE/LanLinkProvider", "Attempting to connect to $deviceId at $address:$port")

        var socket: Socket? = null
        try {
            val socketFactory = network?.socketFactory ?: SocketFactory.getDefault()
            socket = socketFactory.createSocket(address, port)
            configureSocket(socket)

            val myDeviceInfo = DeviceHelper.getDeviceInfo(context)
            val connectionPacket = myDeviceInfo.toConnectionPacket(deviceId, protocolVersion)

            val out = socket.getOutputStream()
            out.write(connectionPacket.serialize().toByteArray())
            out.flush()
        } catch (e: IOException) {
            Log.e("LanLinkProvider", "Exception connecting to $deviceId", e)
            socket?.closeSafe()
            if (reverseConnectionOnError) {
                Log.i("LanLinkProvider", "Connection error, attempting reverse connection")
                sendUdpDiscoveryPacket(listOf(address), network)
            }
            return
        }

        try {
            startSslHandshake(socket, deviceId, protocolVersion, ConnectionStarted.Remotely, deviceTrusted)
        } catch (e: IOException) {
            Log.e("LanLinkProvider", "Exception during handshake with $deviceId", e)
            socket.closeSafe()
        } catch (e: CertificateException) {
            Log.e("LanLinkProvider", "Exception during handshake with $deviceId", e)
            socket.closeSafe()
        }
    }

    private fun configureSocket(socket: Socket) {
        try {
            socket.setKeepAlive(true)
            socket.setSoTimeout(10 * 1000)
        } catch (e: SocketException) {
            Log.e("LanLink", "Exception", e)
        }
    }

    /**
     * Starts the SSL handshake with a remote device after the connection packet has been exchanged.
     * Called from [tcpPacketReceived] and [deviceDiscovered].
     * Should be called on a new thread since it blocks until the handshake is completed.
     *
     * @param socket            a new Socket, which should be used to receive packets from the remote device
     * @param deviceId          id of the remote device, as received in the connection or discovery packet
     * @param protocolVersion   protocol version of the remote device, as received in the connection or discovery packet
     * @param connectionStarted which side started this connection
     * @param deviceTrusted     whether the remote device is trusted
     */
    @WorkerThread
    @Throws(IOException::class, CertificateException::class)
    private fun startSslHandshake(
        socket: Socket,
        deviceId: String,
        protocolVersion: Int,
        connectionStarted: ConnectionStarted,
        deviceTrusted: Boolean
    ) {
        if (deviceTrusted && !TrustedDevices.isCertificateStored(context, deviceId)) {
            Log.e("KDE/LanLinkProvider", "Device trusted but no cert stored. This should not happen.")
            socket.closeSafe()
            return
        }

        if (!deviceTrusted && visibleDevices.size >= MAX_UNPAIRED_CONNECTIONS) {
            Log.w("KDE/LanLinkProvider", "Too many devices on the network. Ignoring $deviceId")
            socket.closeSafe()
            return
        }

        Log.i("KDE/LanLinkProvider", "Starting SSL handshake with $deviceId trusted:$deviceTrusted")

        // If I'm the TCP server I will be the SSL client and vice-versa.
        val clientMode = (connectionStarted == ConnectionStarted.Locally)
        val sslSocket = SslHelper.convertToSslSocket(context, socket, deviceId, deviceTrusted, clientMode)
        sslSocket.addHandshakeCompletedListener { event: HandshakeCompletedEvent ->
            // Start a new thread because some Android versions don't allow calling sslSocket.getOutputStream() from the callback
            ThreadHelper.runOnNewThread {
                val mode = if (clientMode) "client" else "server"
                try {
                    val myDeviceInfo = DeviceHelper.getDeviceInfo(context)
                    val myIdentity = myDeviceInfo.toIdentityPacket()

                    val writer = sslSocket.getOutputStream()
                    writer.write(myIdentity.serialize().toByteArray(Charsets.UTF_8))
                    writer.flush()
                    val line = readLineBounded(sslSocket.getInputStream(), MAX_IDENTITY_PACKET_SIZE)
                    val secureIdentityPacket = NetworkPacket.unserialize(line)
                    if (!DeviceInfo.isValidIdentityPacket(secureIdentityPacket)) {
                        Log.e("KDE/LanLinkProvider", "Identity packet isn't valid")
                        sslSocket.closeSafe()
                        return@runOnNewThread
                    }
                    val newProtocolVersion = secureIdentityPacket.getInt("protocolVersion")
                    if (newProtocolVersion != protocolVersion) {
                        Log.e("KDE/LanLinkProvider", "Protocol version changed half-way through the handshake: $protocolVersion -> $newProtocolVersion")
                        sslSocket.closeSafe()
                        return@runOnNewThread
                    }
                    val newDeviceId = secureIdentityPacket.getString("deviceId")
                    if (newDeviceId != deviceId) {
                        Log.e("KDE/LanLinkProvider", "Device ID changed half-way through the handshake: $deviceId -> $newDeviceId")
                        sslSocket.closeSafe()
                        return@runOnNewThread
                    }
                    val certificate = event.peerCertificates[0]
                    val deviceInfo = DeviceInfo.fromIdentityPacketAndCert(secureIdentityPacket, certificate)
                    Log.i("KDE/LanLinkProvider", "Handshake as $mode successful with ${deviceInfo.name} secured with ${event.cipherSuite}")
                    addOrUpdateLink(sslSocket, deviceInfo)
                } catch (e: SerializationException) {
                    Log.e("KDE/LanLinkProvider", "Remote device doesn't correctly implement protocol version 8", e)
                    sslSocket.closeSafe()
                } catch (e: IOException) {
                    Log.e("KDE/LanLinkProvider", "Handshake as $mode failed with $deviceId", e)
                    sslSocket.closeSafe()
                }
            }
        }

        //Handshake is blocking, so do it on another thread and free this thread to keep receiving new connection
        Log.d("LanLinkProvider", "Starting handshake")
        sslSocket.startHandshake()
        Log.d("LanLinkProvider", "Handshake done")
    }

    private fun isProtocolDowngrade(deviceId: String, protocolVersion: Int): Boolean {
        val lastKnownProtocolVersion = DeviceInfo.loadProtocolVersionFromSettings(context, deviceId)
        return lastKnownProtocolVersion > protocolVersion
    }

    /**
     * Add or update a link in the [.visibleDevices] map.
     *
     * @param socket           a new Socket, which should be used to send and receive packets from the remote device
     * @param deviceInfo       remote device info
     * @throws IOException if an exception is thrown by [LanLink.reset]
     */
    @WorkerThread
    @Synchronized
    private fun addOrUpdateLink(socket: SSLSocket, deviceInfo: DeviceInfo) {
        var linkCreated = false
        val link = visibleDevices.computeIfAbsent(deviceInfo.id) {
            linkCreated = true
            LanLink(context, this, deviceInfo, socket)
        }

        if (linkCreated) {
            Log.d("KDE/LanLinkProvider", "Creating a new link for device ${deviceInfo.id}")
            onConnectionReceived(link)
            link.startListening()
        } else {
            if (link.deviceInfo.certificate != deviceInfo.certificate) {
                Log.e("LanLinkProvider", "LanLink was asked to replace a socket but the certificate doesn't match, aborting")
                socket.closeSafe()
                return
            }
            Log.d("KDE/LanLinkProvider", "Reusing same link for device ${deviceInfo.id}")
            link.reset(socket, deviceInfo)
            onDeviceInfoUpdated(deviceInfo)
        }
    }

    @Synchronized
    private fun setupUdpListener() {
        if (udpServer != null) {
            return
        }
        var newUdpServer: DatagramSocket? = null
        try {
            newUdpServer = DatagramSocket(null)
            newUdpServer.reuseAddress = true
            newUdpServer.broadcast = true
            newUdpServer.bind(InetSocketAddress(UDP_PORT))
            udpServer = newUdpServer
        } catch (e: SocketException) {
            // We ignore this exception and continue without being able to receive broadcasts instead of crashing the app.
            Log.e("LanLinkProvider", "Error binding udp server. We can send udp broadcasts but not receive them", e)
            newUdpServer?.closeSafe()
            return
        }
        ThreadHelper.runOnNewThread {
            Log.i("UdpListener", "Starting UDP listener")
            while (!isStopped) {
                try {
                    val packet = DatagramPacket(ByteArray(MAX_UDP_PACKET_SIZE), MAX_UDP_PACKET_SIZE)
                    newUdpServer.receive(packet)
                    ThreadHelper.runOnNewThread {
                        try {
                            udpPacketReceived(packet)
                        } catch (e: Exception) {
                            Log.e(
                                "LanLinkProvider",
                                "Unhandled exception receiving incoming UDP connection",
                                e
                            )
                        }
                    }
                } catch (e: IOException) {
                    Log.e("LanLinkProvider", "UdpReceive exception", e)
                }
            }
            Log.w("UdpListener", "Stopping UDP listener")
        }
    }

    @Synchronized
    private fun setupTcpListener() {
        if (tcpServer != null) {
            return
        }
        val newTcpServer: ServerSocket
        try {
            newTcpServer = openServerSocketOnFreePort(MIN_PORT)
            tcpServer = newTcpServer
        } catch (e: IOException) {
            Log.e("LanLinkProvider", "Error opening tcp server", e)
            return
        }
        ThreadHelper.runOnNewThread {
            while (!isStopped) {
                try {
                    val socket = newTcpServer.accept()
                    configureSocket(socket)
                    ThreadHelper.runOnNewThread {
                        try {
                            tcpPacketReceived(socket)
                        } catch (e: IOException) {
                            socket.closeSafe()
                            Log.e("LanLinkProvider", "Exception receiving incoming TCP connection", e)
                        } catch (e: CertificateException) {
                            socket.closeSafe()
                            Log.e("LanLinkProvider", "Exception receiving incoming TCP connection", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("LanLinkProvider", "TcpReceive exception", e)
                }
            }
            Log.w("TcpListener", "Stopping TCP listener")
        }
    }

    private fun broadcastUdpDiscoveryPacket(network: Network?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED) {
            Log.w("LanLinkProvider", "Will not UDP broadcast, missing ACCESS_LOCAL_NETWORK permission")
            return
        }
        ThreadHelper.runOnNewThread {
            val ipList = ArrayList<InetAddress>()
            if (TrustedNetworkHelper.isTrustedNetwork(context)) {
                ipList.add(InetAddress.getByName("255.255.255.255")) // Default: broadcast.
            } else {
                Log.i("LanLinkProvider", "Current network isn't trusted, not broadcasting")
            }
            updateCustomDevicesList()
            ipList.addAll(customDeviceAddresses)
            if (ipList.isNotEmpty()) {
                sendUdpDiscoveryPacket(ipList, network)
            }
        }
    }

    @WorkerThread
    fun sendUdpDiscoveryPacket(ipList: List<InetAddress>, network: Network?) {
        val tcpServer = tcpServer
        if (tcpServer?.isBound != true) {
            Log.i("LanLinkProvider", "Won't broadcast UDP packet if TCP socket is not ready yet")
            return
        }

        val myDeviceInfo = DeviceHelper.getDeviceInfo(context)
        val discoveryPacket = myDeviceInfo.toUdpDiscoveryPacket(tcpServer.getLocalPort())

        val bytes: ByteArray
        try {
            bytes = discoveryPacket.serialize().toByteArray(Charsets.UTF_8)
        } catch (e: SerializationException) {
            Log.e("KDE/LanLinkProvider", "Failed to serialize UDP discovery packet", e)
            return
        }

        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            if (network != null) {
                try {
                    network.bindSocket(socket)
                } catch (e: IOException) {
                    Log.w("LanLinkProvider", "Couldn't bind socket to the network")
                    e.printStackTrace()
                }
            }
            socket.setReuseAddress(true)
            socket.setBroadcast(true)
        } catch (e: SocketException) {
            Log.e("KDE/LanLinkProvider", "Failed to create DatagramSocket", e)
            socket?.closeSafe()
            return
        }

        for (ip in ipList) {
            try {
                socket.send(DatagramPacket(bytes, bytes.size, ip, MIN_PORT))
                //Log.i("KDE/LanLinkProvider","UDP discovery packet sent to address "+client);
            } catch (e: IOException) {
                Log.e("KDE/LanLinkProvider", "Sending UDP discovery packet failed. Invalid address? ($ip)", e)
            }
        }

        socket.closeSafe()
    }

    override fun onStart() {
        //Log.i("KDE/LanLinkProvider", "onStart");
        isStopped = false

        updateCustomDevicesList()
        customDevicePreferences.registerOnSharedPreferenceChangeListener(customDeviceListener)

        setupUdpListener()
        setupTcpListener()

        synchronized(mdnsDiscovery) {
            mdnsDiscovery.startDiscovering()
            if (TrustedNetworkHelper.isTrustedNetwork(context)) {
                mdnsDiscovery.startAnnouncing()
            }
        }

        broadcastUdpDiscoveryPacket(null)
    }

    @Synchronized
    override fun onNetworkChange(network: Network?) {
        // Stop announcing right away, or we could announce ourselves on an untrusted network
        if (!TrustedNetworkHelper.isTrustedNetwork(context)) {
            synchronized(mdnsDiscovery) {
                mdnsDiscovery.stopAnnouncing() // noop if already stopped
            }
        }
        if (network != null) {
            handleNetworkChange(network)
        } else {
            // Debounce refreshes without a target network
            networkChangeJob?.cancel()
            networkChangeJob = networkChangeScope.launch {
                delay(timeMillis = NETWORK_CHANGE_DEBOUNCE_MS)
                handleNetworkChange(null)
            }
        }
    }

    private fun handleNetworkChange(network: Network?) {
        if (isStopped) return
        setupUdpListener()
        setupTcpListener()

        broadcastUdpDiscoveryPacket(network)
        synchronized(mdnsDiscovery) {
            if (TrustedNetworkHelper.isTrustedNetwork(context)) {
                mdnsDiscovery.startAnnouncing() // noop if already announcing
            } else {
                mdnsDiscovery.stopAnnouncing() // noop if already stopped
            }
            mdnsDiscovery.stopDiscovering()
            mdnsDiscovery.startDiscovering()
        }
    }

    override fun onStop() {
        //Log.i("KDE/LanLinkProvider", "onStop");
        isStopped = true
        customDevicePreferences.unregisterOnSharedPreferenceChangeListener(customDeviceListener)
        synchronized(mdnsDiscovery) {
            mdnsDiscovery.stopAnnouncing()
            mdnsDiscovery.stopDiscovering()
        }
        tcpServer?.closeSafe()
        udpServer?.closeSafe()
        tcpServer = null
        udpServer = null
    }

    override val name: String = "LanLinkProvider"

    override val priority: Int = 20

    val tcpPort: Int?
        get() = tcpServer?.getLocalPort()

    private enum class ConnectionStarted {
        Locally, Remotely
    }

    companion object {
        private const val UDP_PORT: Int = 1716
        private const val MIN_PORT: Int = 1716
        private const val MAX_PORT: Int = 1764
        const val PAYLOAD_TRANSFER_MIN_PORT: Int = 1739

        private const val MAX_IDENTITY_PACKET_SIZE: Int = 1024 * 512
        private const val MAX_UDP_PACKET_SIZE: Int = 1024 * 512

        const val MILLIS_DELAY_BETWEEN_CONNECTIONS_TO_SAME_DEVICE: Long = 1000L

        private const val MAX_RATE_LIMIT_ENTRIES: Int = 255
        private const val MAX_UNPAIRED_CONNECTIONS: Int = 42
        private const val NETWORK_CHANGE_DEBOUNCE_MS: Long = 200

        @Throws(IOException::class)
        fun openServerSocketOnFreePort(minPort: Int): ServerSocket {
            var tcpPort = minPort
            while (tcpPort <= MAX_PORT) {
                try {
                    val candidateServer = ServerSocket(tcpPort)
                    Log.i("KDE/LanLink", "Using port $tcpPort")
                    return candidateServer
                } catch (e: IOException) {
                    tcpPort++
                    if (tcpPort == MAX_PORT) {
                        Log.e("KDE/LanLink", "No ports available")
                        throw e //Propagate exception
                    }
                }
            }
            throw RuntimeException("This should not be reachable")
        }
    }
}
