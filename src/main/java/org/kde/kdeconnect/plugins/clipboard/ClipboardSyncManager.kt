/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.clipboard

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.util.Log
import android.widget.Toast
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.helpers.ThreadHelper
import org.kde.kdeconnect.helpers.TrustedDevices
import org.kde.kdeconnect.helpers.TrustedNetworkHelper
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.ui.background.ConnectionLab
import org.kde.kdeconnect.ui.background.ShizukuSupport
import org.kde.kdeconnect_tp.R
import rikka.shizuku.Shizuku
import java.security.MessageDigest
import java.util.UUID

internal data class ClipboardSyncSettings(
    val enabled: Boolean,
    val trustedWifiOnly: Boolean,
    val plainTextOnly: Boolean,
    val showConfirmation: Boolean,
)

internal object ClipboardSyncPreferences {
    private const val FILE = "ecosystem_clipboard_sync"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_TRUSTED_WIFI = "trusted_wifi_only"
    private const val KEY_PLAIN_TEXT_ONLY = "plain_text_only"
    private const val KEY_CONFIRMATION = "show_confirmation"
    private const val KEY_LAST_LOCAL_HASH = "last_local_hash"

    fun read(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).let {
        ClipboardSyncSettings(
            enabled = it.getBoolean(KEY_ENABLED, false),
            trustedWifiOnly = it.getBoolean(KEY_TRUSTED_WIFI, false),
            plainTextOnly = it.getBoolean(KEY_PLAIN_TEXT_ONLY, true),
            showConfirmation = it.getBoolean(KEY_CONFIRMATION, true),
        )
    }

    fun update(context: Context, transform: (ClipboardSyncSettings) -> ClipboardSyncSettings) {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val next = transform(read(context))
        prefs.edit {
            putBoolean(KEY_ENABLED, next.enabled)
            putBoolean(KEY_TRUSTED_WIFI, next.trustedWifiOnly)
            putBoolean(KEY_PLAIN_TEXT_ONLY, next.plainTextOnly)
            putBoolean(KEY_CONFIRMATION, next.showConfirmation)
        }
    }

    fun lastLocalHash(context: Context): String? = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        .getString(KEY_LAST_LOCAL_HASH, null)

    fun rememberLocalHash(context: Context, hash: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putString(KEY_LAST_LOCAL_HASH, hash) }
    }
}

/** Coordinates the privileged watcher, loop protection, and delivery over KDE Connect's paired transport. */
internal object ClipboardSyncManager {
    private const val TAG = "EcosystemClipboard"
    private const val SERVICE_TAG = "ecosystem_clipboard_watcher"
    private const val REMOTE_COOLDOWN_MS = 5_000L
    private const val DELIVERY_RETRY_MS = 15_000L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val remoteHashes = LinkedHashMap<String, Long>()
    private val outboxLock = Any()
    private val inFlight = mutableMapOf<String, Long>()
    @Volatile private var retryScheduled = false
    private lateinit var appContext: Context
    private var initialized = false
    private var isBound = false
    private var watcher: IClipboardWatcher? = null
    var watcherActive: Boolean by mutableStateOf(false)
        private set

    private val serviceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(appContext, EcosystemClipboardUserService::class.java))
            .daemon(true)
            .tag(SERVICE_TAG)
            .version(6)
            .processNameSuffix("clipboard_watcher")
    }

    private val callback = object : IClipboardSyncCallback.Stub() {
        override fun onClipboardChanged(text: String?, sensitive: Boolean, plainText: Boolean, remoteDevice: Boolean) {
            if (text != null) onLocalClipboardChanged(text, sensitive, plainText, remoteDevice)
        }
    }

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val remote = IClipboardWatcher.Stub.asInterface(service)
            if (remote == null) {
                if (isBound) runCatching { Shizuku.unbindUserService(serviceArgs, serviceConnection, true) }
                isBound = false
                return updateWatcherState(false)
            }
            watcher = remote
            var started = false
            try {
                remote.setCallback(callback)
                started = remote.start()
            } catch (e: Exception) {
                Log.w(TAG, "Could not start Shizuku clipboard watcher", e)
            }
            if (!started) {
                watcher = null
                if (isBound) {
                    runCatching { Shizuku.unbindUserService(serviceArgs, serviceConnection, true) }
                    isBound = false
                }
            }
            updateWatcherState(started)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            watcher = null
            isBound = false
            updateWatcherState(false)
            mainHandler.postDelayed(::refresh, 1_000)
        }

        override fun onBindingDied(name: ComponentName?) = onServiceDisconnected(name)
        override fun onNullBinding(name: ComponentName?) = onServiceDisconnected(name)
    }

    private val shizukuReceived = Shizuku.OnBinderReceivedListener { mainHandler.post(::refresh) }
    private val shizukuDead = Shizuku.OnBinderDeadListener {
        watcher = null
        isBound = false
        updateWatcherState(false)
    }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ -> mainHandler.post(::refresh) }

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        KdeConnect.getInstance().addDeviceListChangedCallback("ClipboardDeliveryQueue") {
            ThreadHelper.execute { flushPendingClipboard() }
        }
        Shizuku.addBinderReceivedListenerSticky(shizukuReceived)
        Shizuku.addBinderDeadListener(shizukuDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        ThreadHelper.execute(::replayPendingIncoming)
        refresh()
    }

    fun settings(context: Context) = ClipboardSyncPreferences.read(context)

    fun contentId(text: String): String = hash(text)

    fun updateSettings(context: Context, transform: (ClipboardSyncSettings) -> ClipboardSyncSettings) {
        ClipboardSyncPreferences.update(context, transform)
        refresh()
    }

    fun refresh() {
        if (!initialized) return
        val settings = ClipboardSyncPreferences.read(appContext)
        if (!settings.enabled || !ShizukuSupport.state(appContext).authorized) {
            stopWatcher()
            return
        }
        if (isBound) return
        try {
            isBound = true
            Shizuku.bindUserService(serviceArgs, serviceConnection)
        } catch (e: Exception) {
            isBound = false
            updateWatcherState(false)
            Log.w(TAG, "Could not bind Shizuku clipboard watcher", e)
        }
    }

    fun isAutomaticAvailable(context: Context): Boolean {
        val settings = ClipboardSyncPreferences.read(context)
        return settings.enabled && watcherActive && isTrustedWifiAllowed(context, settings)
    }

    fun onLocalClipboardChanged(text: String, sensitive: Boolean, plainText: Boolean, remoteDevice: Boolean = false) {
        if (text.isBlank() || sensitive || remoteDevice) return
        val settings = ClipboardSyncPreferences.read(appContext)
        if (!settings.enabled || !watcherActive || (settings.plainTextOnly && !plainText) ||
            !isTrustedWifiAllowed(appContext, settings)) return

        val hash = hash(text)
        synchronized(remoteHashes) {
            pruneRemoteHashes()
            if (remoteHashes.containsKey(hash)) return
        }
        if (ClipboardSyncPreferences.lastLocalHash(appContext) == hash) return
        ClipboardSyncPreferences.rememberLocalHash(appContext, hash)
        val eventId = UUID.randomUUID().toString()
        val destinations = KdeConnect.getInstance().devices.values
            .filter { it.isPaired && it.isPluginEnabled(Plugin.getPluginKey(ClipboardPlugin::class.java)) &&
                TrustedDevices.isTrustedDevice(appContext, it.deviceId) }
        destinations.forEach { device -> enqueueClipboard(device.deviceId, eventId, text, System.currentTimeMillis()) }
        flushPendingClipboard()
    }

    /** Applies a peer clip after recording it so the privileged watcher cannot echo it back. */
    fun applyIncoming(context: Context, text: String?, id: String?, sourceName: String, sensitive: Boolean = false): Boolean {
        if (text.isNullOrBlank() || sensitive) return false
        val settings = ClipboardSyncPreferences.read(context)
        if (id != null && !settings.enabled) return false
        if (!id.isNullOrBlank() && !beginIncomingDelivery(context, id, text, sourceName)) {
            ConnectionLab.recordClipboardDuplicate()
            return true
        }
        val hash = hash(text)
        synchronized(remoteHashes) {
            pruneRemoteHashes()
            if (remoteHashes.containsKey(hash)) {
                if (!id.isNullOrBlank()) finishIncomingDelivery(context, id)
                return true
            }
            remoteHashes[hash] = System.currentTimeMillis()
        }
        if (ClipboardListener.instance(context).currentContent == text) {
            if (!id.isNullOrBlank()) finishIncomingDelivery(context, id)
            return true
        }
        ClipboardListener.instance(context).setText(text, remoteDevice = true)
        if (!id.isNullOrBlank()) finishIncomingDelivery(context, id)
        if (settings.showConfirmation) {
            mainHandler.post {
                Toast.makeText(context.applicationContext,
                    context.getString(R.string.clipboard_received_from, sourceName), Toast.LENGTH_SHORT).show()
            }
        }
        return true
    }

    private data class PendingClipboard(val deviceId: String, val id: String, val text: String, val sentAt: Long)

    private fun enqueueClipboard(deviceId: String, id: String, text: String, sentAt: Long) {
        synchronized(outboxLock) {
            val pending = readOutbox()
            if (pending.none { it.deviceId == deviceId && it.id == id }) {
                pending.add(PendingClipboard(deviceId, id, text, sentAt))
                writeOutbox(pending)
            }
        }
    }

    private fun flushPendingClipboard() {
        if (!initialized || !ClipboardSyncPreferences.read(appContext).enabled) return
        val pending = synchronized(outboxLock) { readOutbox().toList() }
        val devices = KdeConnect.getInstance().devices
        pending.forEach { item ->
            val device = devices[item.deviceId] ?: return@forEach
            if (!device.isReachable || !device.isPaired ||
                !device.isPluginEnabled(Plugin.getPluginKey(ClipboardPlugin::class.java)) ||
                !TrustedDevices.isTrustedDevice(appContext, item.deviceId)) return@forEach
            val deliveryKey = "${item.deviceId}:${item.id}"
            val now = SystemClock.elapsedRealtime()
            val shouldSend = synchronized(inFlight) {
                val previous = inFlight[deliveryKey]
                if (previous != null && now - previous < DELIVERY_RETRY_MS) false
                else { inFlight[deliveryKey] = now; true }
            }
            if (!shouldSend) return@forEach
            val packet = NetworkPacket(ClipboardPlugin.PACKET_TYPE_CLIPBOARD)
            packet["content"] = item.text
            packet[ClipboardPlugin.ECOSYSTEM_CLIPBOARD_ID] = item.id
            packet[ClipboardPlugin.ECOSYSTEM_CLIPBOARD_SENT_AT] = item.sentAt
            device.sendPacket(packet)
        }
        if (pending.isNotEmpty()) scheduleDeliveryRetry()
    }

    fun confirmDelivery(deviceId: String, id: String) {
        if (id.isBlank()) return
        synchronized(outboxLock) {
            val pending = readOutbox()
            pending.removeAll { it.deviceId == deviceId && it.id == id }
            writeOutbox(pending)
        }
        synchronized(inFlight) { inFlight.remove("$deviceId:$id") }
    }

    fun pendingDeliveryCount(): Int = synchronized(outboxLock) { readOutbox().size }

    private fun scheduleDeliveryRetry() {
        if (retryScheduled) return
        retryScheduled = true
        mainHandler.postDelayed({
            retryScheduled = false
            ThreadHelper.execute { flushPendingClipboard() }
        }, DELIVERY_RETRY_MS)
    }

    private fun beginIncomingDelivery(context: Context, id: String, text: String, sourceName: String): Boolean = synchronized(outboxLock) {
        val prefs = context.getSharedPreferences("ecosystem_clipboard_sync", Context.MODE_PRIVATE)
        val seen = runCatching { JSONArray(prefs.getString("seen_clipboard_delivery_ids", "[]")) }
            .getOrDefault(JSONArray())
        if ((0 until seen.length()).any { seen.optString(it) == id }) return@synchronized false
        val pending = runCatching { JSONArray(prefs.getString("pending_incoming_clipboard", "[]")) }
            .getOrDefault(JSONArray())
        if ((0 until pending.length()).any { pending.optJSONObject(it)?.optString("id") == id }) return@synchronized true
        pending.put(JSONObject().put("id", id).put("text", text).put("source", sourceName))
        prefs.edit { putString("pending_incoming_clipboard", pending.toString()) }
        true
    }

    private fun finishIncomingDelivery(context: Context, id: String) = synchronized(outboxLock) {
        val prefs = context.getSharedPreferences("ecosystem_clipboard_sync", Context.MODE_PRIVATE)
        val seen = runCatching { JSONArray(prefs.getString("seen_clipboard_delivery_ids", "[]")) }
            .getOrDefault(JSONArray())
        val nextSeen = JSONArray()
        for (index in (seen.length() - 499).coerceAtLeast(0) until seen.length()) nextSeen.put(seen.optString(index))
        if ((0 until seen.length()).none { seen.optString(it) == id }) nextSeen.put(id)
        val pending = runCatching { JSONArray(prefs.getString("pending_incoming_clipboard", "[]")) }
            .getOrDefault(JSONArray())
        val nextPending = JSONArray()
        for (index in 0 until pending.length()) {
            val row = pending.optJSONObject(index) ?: continue
            if (row.optString("id") != id) nextPending.put(row)
        }
        prefs.edit {
            putString("seen_clipboard_delivery_ids", nextSeen.toString())
            putString("pending_incoming_clipboard", nextPending.toString())
        }
    }

    private fun replayPendingIncoming() {
        if (!initialized) return
        val prefs = appContext.getSharedPreferences("ecosystem_clipboard_sync", Context.MODE_PRIVATE)
        val pending = runCatching { JSONArray(prefs.getString("pending_incoming_clipboard", "[]")) }
            .getOrDefault(JSONArray())
        for (index in 0 until pending.length()) {
            val row = pending.optJSONObject(index) ?: continue
            applyIncoming(appContext, row.optString("text"), row.optString("id"), row.optString("source"))
        }
    }

    private fun readOutbox(): MutableList<PendingClipboard> {
        val prefs = appContext.getSharedPreferences("ecosystem_clipboard_sync", Context.MODE_PRIVATE)
        val json = runCatching { JSONArray(prefs.getString("pending_clipboard_delivery", "[]")) }
            .getOrDefault(JSONArray())
        return (0 until json.length()).mapNotNull { index ->
            val row = json.optJSONObject(index) ?: return@mapNotNull null
            val deviceId = row.optString("device")
            val id = row.optString("id")
            if (deviceId.isBlank() || id.isBlank()) null
            else PendingClipboard(deviceId, id, row.optString("text"), row.optLong("sentAt"))
        }.toMutableList()
    }

    private fun writeOutbox(pending: List<PendingClipboard>) {
        val json = JSONArray()
        pending.forEach { row ->
            json.put(JSONObject().put("device", row.deviceId).put("id", row.id)
                .put("text", row.text).put("sentAt", row.sentAt))
        }
        appContext.getSharedPreferences("ecosystem_clipboard_sync", Context.MODE_PRIVATE).edit {
            putString("pending_clipboard_delivery", json.toString())
        }
    }

    fun recordClipboardDelay(sentAt: Long) {
        if (sentAt > 0) ConnectionLab.recordClipboardDelay((System.currentTimeMillis() - sentAt).coerceAtLeast(0))
    }

    private fun isTrustedWifiAllowed(context: Context, settings: ClipboardSyncSettings): Boolean {
        if (!settings.trustedWifiOnly) return true
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return false
        val trustedNetworks = TrustedNetworkHelper(context)
        if (!trustedNetworks.hasPermissions || trustedNetworks.allNetworksAllowed) return false
        val currentSsid = trustedNetworks.getCurrentSSID()?.trim('"') ?: return false
        return trustedNetworks.trustedNetworks.any { it.trim('"') == currentSsid }
    }

    private fun stopWatcher() {
        val oldWatcher = watcher
        watcher = null
        if (oldWatcher != null) runCatching { oldWatcher.stop() }
        if (isBound) {
            if (runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
                runCatching { Shizuku.unbindUserService(serviceArgs, serviceConnection, true) }
            }
            isBound = false
        }
        updateWatcherState(false)
    }

    private fun updateWatcherState(active: Boolean) {
        if (watcherActive == active) return
        mainHandler.post {
            watcherActive = active
            BackgroundService.instance?.updateForegroundNotification()
        }
    }

    private fun pruneRemoteHashes() {
        val now = System.currentTimeMillis()
        remoteHashes.entries.removeAll { now - it.value > REMOTE_COOLDOWN_MS }
    }

    private fun hash(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
