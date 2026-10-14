/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.backends.wifidirect

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.backends.BaseLinkProvider
import org.kde.kdeconnect.backends.ConnectionTransport
import org.kde.kdeconnect.backends.lan.LanLinkProvider
import java.net.InetAddress
import java.util.Locale

/** Establishes a Wi-Fi Direct group and reuses the authenticated LAN socket protocol over it. */
internal class WifiDirectLinkProvider(
    private val context: Context,
    private val lanProvider: LanLinkProvider,
) : BaseLinkProvider() {
    private val handler = Handler(Looper.getMainLooper())
    private val connectivity by lazy { context.getSystemService(ConnectivityManager::class.java) }
    private var manager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var registered = false
    private var lastConnectAttempt = 0L
    private var connectRetryDelayMs = CONNECT_RETRY_INITIAL_MS

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    if (intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, WifiP2pManager.WIFI_P2P_STATE_DISABLED) ==
                        WifiP2pManager.WIFI_P2P_STATE_ENABLED) discoverPeers()
                    else clearGroupTransport()
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> requestPeers()
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> requestConnectionInfo()
            }
        }
    }

    override fun onStart() {
        initializeP2p()
    }

    private fun initializeP2p() {
        if (manager != null) return
        if (!hasRuntimePermission()) {
            Log.w(TAG, "Wi-Fi Direct disabled: nearby Wi-Fi permission has not been granted")
            return
        }
        manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        val p2pManager = manager ?: return
        channel = p2pManager.initialize(context, Looper.getMainLooper()) { clearGroupTransport() }
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        registered = true
        discoverPeers()
    }

    override fun onNetworkChange(network: Network?) {
        if (manager == null) {
            initializeP2p()
            return
        }
        val capabilities = network?.let { connectivity?.getNetworkCapabilities(it) }
        val interfaceName = network?.let { connectivity?.getLinkProperties(it)?.interfaceName }.orEmpty()
        val normalWifiAvailable =
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true &&
                !interfaceName.startsWith("p2p", ignoreCase = true)
        val wifiBack = normalWifiAvailable
        if (wifiBack) {
            stopGroup()
            clearGroupTransport()
        } else {
            discoverPeers()
            requestConnectionInfo()
        }
    }

    override fun onStop() {
        if (registered) runCatching { context.unregisterReceiver(receiver) }
        registered = false
        stopGroup()
        clearGroupTransport()
        channel = null
        manager = null
    }

    override val name: String = "WifiDirectLinkProvider"
    override val priority: Int = ConnectionTransport.WIFI_DIRECT.priority
    override val transport: ConnectionTransport = ConnectionTransport.WIFI_DIRECT

    private fun hasRuntimePermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.NEARBY_WIFI_DEVICES
            else Manifest.permission.ACCESS_FINE_LOCATION
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun discoverPeers() {
        if (!hasRuntimePermission()) return
        val p2p = manager ?: return
        val p2pChannel = channel ?: return
        runCatching {
            p2p.discoverPeers(p2pChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() { requestPeers() }
                override fun onFailure(reason: Int) { Log.d(TAG, "Wi-Fi Direct discovery deferred: reason=$reason") }
            })
        }.onFailure { Log.w(TAG, "Wi-Fi Direct discovery failed", it) }
    }

    private fun requestPeers() {
        if (!hasRuntimePermission()) return
        val p2p = manager ?: return
        val p2pChannel = channel ?: return
        runCatching {
            p2p.requestPeers(p2pChannel) { peers: WifiP2pDeviceList ->
                if (!needsWiFiDirect()) return@requestPeers
                val pairedNames = KdeConnect.getInstance().devices.values
                    .filter { it.isPaired && (it.activeTransport?.priority ?: 0) < ConnectionTransport.WIFI_DIRECT.priority }
                    .map { normalizeName(it.name) }
                val peer = peers.deviceList.firstOrNull { device ->
                    device.status == WifiP2pDevice.AVAILABLE && pairedNames.any { name ->
                        name.isNotBlank() && normalizeName(device.deviceName).contains(name)
                    }
                } ?: return@requestPeers
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastConnectAttempt < CONNECT_RETRY_MS) return@requestPeers
                lastConnectAttempt = now
                p2p.connect(p2pChannel, WifiP2pConfig().apply { deviceAddress = peer.deviceAddress },
                    object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {
                            connectRetryDelayMs = CONNECT_RETRY_INITIAL_MS
                            Log.i(TAG, "Requested Wi-Fi Direct connection to ${peer.deviceName}")
                        }
                        override fun onFailure(reason: Int) {
                            Log.w(TAG, "Wi-Fi Direct connect failed: reason=$reason")
                            val retryAfter = connectRetryDelayMs
                            connectRetryDelayMs = (connectRetryDelayMs * 2).coerceAtMost(CONNECT_RETRY_MAX_MS)
                            handler.postDelayed(::discoverPeers, retryAfter)
                        }
                    })
            }
        }.onFailure { Log.w(TAG, "Could not inspect Wi-Fi Direct peers", it) }
    }

    private fun needsWiFiDirect(): Boolean = KdeConnect.getInstance().devices.values.any {
        it.isPaired && (it.activeTransport?.priority ?: 0) < ConnectionTransport.WIFI_DIRECT.priority
    }

    private fun requestConnectionInfo() {
        if (!hasRuntimePermission()) return
        val p2p = manager ?: return
        val p2pChannel = channel ?: return
        runCatching {
            p2p.requestConnectionInfo(p2pChannel) { info: WifiP2pInfo ->
                if (!info.groupFormed) {
                    clearGroupTransport()
                    return@requestConnectionInfo
                }
                lanProvider.setActiveTransport(ConnectionTransport.WIFI_DIRECT)
                if (!info.isGroupOwner) sendIdentityToGroupOwner(info.groupOwnerAddress, 0)
            }
        }.onFailure { Log.w(TAG, "Could not inspect Wi-Fi Direct group", it) }
    }

    private fun sendIdentityToGroupOwner(address: InetAddress, attempt: Int) {
        if (attempt >= NETWORK_LOOKUP_ATTEMPTS) {
            Log.w(TAG, "Wi-Fi Direct group is up but its Android Network was not published")
            return
        }
        val network = findP2pNetwork()
        if (network == null) {
            handler.postDelayed({ sendIdentityToGroupOwner(address, attempt + 1) }, NETWORK_LOOKUP_DELAY_MS)
            return
        }
        lanProvider.sendUdpIdentityPacket(listOf(address), network)
    }

    private fun findP2pNetwork(): Network? = connectivity?.allNetworks?.firstOrNull { network ->
        val interfaceName = connectivity?.getLinkProperties(network)?.interfaceName.orEmpty()
        val capabilities = connectivity?.getNetworkCapabilities(network)
        interfaceName.startsWith("p2p", ignoreCase = true) &&
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    private fun stopGroup() {
        val p2p = manager ?: return
        val p2pChannel = channel ?: return
        if (!hasRuntimePermission()) return
        runCatching { p2p.removeGroup(p2pChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { Log.i(TAG, "Stopped Wi-Fi Direct group") }
            override fun onFailure(reason: Int) { Log.d(TAG, "Wi-Fi Direct group already stopped: reason=$reason") }
        }) }
    }

    private fun clearGroupTransport() {
        lanProvider.setActiveTransport(ConnectionTransport.WIFI)
    }

    private fun normalizeName(name: String): String = name
        .lowercase(Locale.ROOT)
        .replace(Regex("^direct-[a-z0-9]{2}-"), "")
        .replace(Regex("[^a-z0-9]"), "")

    companion object {
        private const val TAG = "WifiDirectLink"
        private const val CONNECT_RETRY_INITIAL_MS = 3_000L
        private const val CONNECT_RETRY_MAX_MS = 30_000L
        private const val CONNECT_RETRY_MS = 5_000L
        private const val NETWORK_LOOKUP_ATTEMPTS = 20
        private const val NETWORK_LOOKUP_DELAY_MS = 500L
    }
}
