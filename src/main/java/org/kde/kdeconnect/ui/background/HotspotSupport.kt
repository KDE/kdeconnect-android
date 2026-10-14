/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui.background

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.backends.ConnectionTransport
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.helpers.ThreadHelper
import rikka.shizuku.Shizuku
import java.security.MessageDigest

/** Promotes a paired peer's private hotspot only after higher-priority links had time to appear. */
internal object HotspotSupport {
    private const val TAG = "EcosystemHotspot"
    private const val FALLBACK_GRACE_MS = 5_000L
    private const val LINK_CONFIRM_MS = 8_000L
    private const val RETRY_INITIAL_MS = 3_000L
    private const val RETRY_MAX_MS = 60_000L
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var appContext: Context
    private var initialized = false
    private var bound = false
    private var service: IHotspotControl? = null
    private var pendingTarget: Device? = null
    private var activeTargetId: String? = null
    private var lastFailureAt = 0L
    private var firstNeededAt = 0L
    private var retryDelayMs = RETRY_INITIAL_MS
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = IHotspotControl.Stub.asInterface(binder)
            operateForPendingTarget()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
            lastFailureAt = SystemClock.elapsedRealtime()
            handler.postDelayed(::reconcile, retryDelayMs)
        }

        override fun onBindingDied(name: ComponentName?) = onServiceDisconnected(name)
        override fun onNullBinding(name: ComponentName?) = onServiceDisconnected(name)
    }
    private val args by lazy {
        Shizuku.UserServiceArgs(ComponentName(appContext, HotspotUserService::class.java))
            .tag("ecosystem_hotspot").version(1).daemon(false).processNameSuffix("hotspot")
    }

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        Shizuku.addBinderReceivedListenerSticky { handler.post(::reconcile) }
        Shizuku.addBinderDeadListener { handler.post { connection.onServiceDisconnected(null) } }
        Shizuku.addRequestPermissionResultListener { _, _ -> handler.post(::reconcile) }
        KdeConnect.getInstance().addDeviceListChangedCallback("HotspotFallback") {
            handler.post(::reconcile)
        }
        reconcile()
    }

    fun onNetworkAvailable(network: Network): Boolean {
        val target = pendingTarget ?: return false
        if (isHost(target)) return false
        val capabilities = appContext.getSystemService(ConnectivityManager::class.java)
            ?.getNetworkCapabilities(network) ?: return false
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return false
        val expectedSsid = credentials(target).first
        val actualSsid = runCatching {
            appContext.getSystemService(WifiManager::class.java)?.connectionInfo?.ssid?.trim('"')
        }.getOrNull()
        if (actualSsid != expectedSsid) return false
        val route = appContext.getSystemService(ConnectivityManager::class.java)
            ?.getLinkProperties(network)?.routes?.firstOrNull { it.isDefaultRoute && it.gateway != null } ?: return false
        BackgroundService.instance?.announceIdentityOnNetwork(
            ConnectionTransport.HOTSPOT,
            requireNotNull(route.gateway),
            network,
        )
        return true
    }

    private fun reconcile() {
        if (!initialized) return
        val pairedDevices = KdeConnect.getInstance().devices.values.filter { it.isPaired }
        val active = activeTargetId?.let { id -> pairedDevices.firstOrNull { it.deviceId == id } }
        if (active?.activeTransport == ConnectionTransport.HOTSPOT) {
            // Keep our AP alive while it is the working route; stop only after a better link appears.
            pendingTarget = active
            firstNeededAt = 0
            return
        }
        val target = pairedDevices
            .filter { it.isPaired && it.activeTransport.let { t -> t == null || t.priority < ConnectionTransport.HOTSPOT.priority } }
            .minByOrNull { it.deviceId } // the same target is selected deterministically on both devices
        if (target == null) {
            firstNeededAt = 0
            pendingTarget = null
            stopIfHost()
            return
        }
        if (firstNeededAt == 0L || pendingTarget?.deviceId != target.deviceId) {
            firstNeededAt = SystemClock.elapsedRealtime()
            pendingTarget = target
            handler.postDelayed(::reconcile, FALLBACK_GRACE_MS)
            return
        }
        if (SystemClock.elapsedRealtime() - firstNeededAt < FALLBACK_GRACE_MS) return
        if (activeTargetId == target.deviceId && target.activeTransport == ConnectionTransport.HOTSPOT) return
        if (SystemClock.elapsedRealtime() - lastFailureAt < retryDelayMs) return
        if (!ShizukuSupport.state(appContext).authorized) {
            Log.i(TAG, "Hotspot fallback waiting for Shizuku authorization")
            return
        }
        ensureService()
    }

    private fun ensureService() {
        if (service != null) {
            operateForPendingTarget()
            return
        }
        if (bound) return
        try {
            bound = true
            Shizuku.bindUserService(args, connection)
        } catch (e: RuntimeException) {
            bound = false
            lastFailureAt = SystemClock.elapsedRealtime()
            Log.e(TAG, "Could not bind the Shizuku hotspot controller", e)
            handler.postDelayed(::reconcile, retryDelayMs)
        }
    }

    private fun operateForPendingTarget() {
        val target = pendingTarget ?: return
        val controller = service ?: return
        val (ssid, passphrase) = credentials(target)
        val hosting = isHost(target)
        BackgroundService.instance?.setActiveLinkTransport(ConnectionTransport.HOTSPOT)
        ThreadHelper.execute {
            val raw = runCatching {
                if (hosting) controller.startHotspot(ssid, passphrase)
                else controller.joinHotspot(ssid, passphrase)
            }.getOrElse { JSONObject().put("ok", false).put("error", it.message ?: it.javaClass.simpleName).toString() }
            val ok = runCatching { JSONObject(raw).optBoolean("ok") }.getOrDefault(false)
            Log.i(TAG, "${if (hosting) "host" else "join"} result for ${target.name}: $raw")
            handler.post {
                if (ok) {
                    activeTargetId = target.deviceId
                    handler.postDelayed({
                        if (target.activeTransport == ConnectionTransport.HOTSPOT) {
                            lastFailureAt = 0
                            retryDelayMs = RETRY_INITIAL_MS
                            firstNeededAt = 0
                        } else {
                            lastFailureAt = SystemClock.elapsedRealtime()
                            retryDelayMs = (retryDelayMs * 2).coerceAtMost(RETRY_MAX_MS)
                            handler.postDelayed(::reconcile, retryDelayMs)
                        }
                    }, LINK_CONFIRM_MS)
                } else {
                    lastFailureAt = SystemClock.elapsedRealtime()
                    retryDelayMs = (retryDelayMs * 2).coerceAtMost(RETRY_MAX_MS)
                    handler.postDelayed(::reconcile, retryDelayMs)
                }
            }
        }
    }

    private fun stopIfHost() {
        val prior = activeTargetId?.let { KdeConnect.getInstance().devices[it] }
        if (prior != null && isHost(prior)) {
            val controller = service
            if (controller != null) {
                ThreadHelper.execute {
                    runCatching { controller.stopHotspot() }
                        .onFailure { Log.w(TAG, "Could not stop private hotspot", it) }
                }
            }
        }
        activeTargetId = null
        lastFailureAt = 0
        retryDelayMs = RETRY_INITIAL_MS
        val nextTransport = prior?.activeTransport
            ?.takeIf { it.priority > ConnectionTransport.HOTSPOT.priority }
            ?: ConnectionTransport.WIFI
        BackgroundService.instance?.setActiveLinkTransport(nextTransport)
    }

    private fun isHost(target: Device): Boolean = DeviceHelper.getDeviceId(appContext) < target.deviceId

    private fun credentials(target: Device): Pair<String, String> {
        val localId = DeviceHelper.getDeviceId(appContext)
        val ids = listOf(localId, target.deviceId).sorted()
        val digest = MessageDigest.getInstance("SHA-256").digest(ids.joinToString(":").toByteArray())
        val suffix = Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            .uppercase().replace(Regex("[^A-Z0-9]"), "").take(12)
        val key = Base64.encodeToString(digest.copyOfRange(8, 24), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            .replace(Regex("[^A-Za-z0-9]"), "").take(20) + "7a"
        return "Ecosystem$suffix" to key
    }
}
