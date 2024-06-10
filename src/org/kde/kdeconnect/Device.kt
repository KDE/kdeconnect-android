/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.annotation.AnyThread
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.apache.commons.collections4.MultiValuedMap
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap
import org.kde.kdeconnect.Backends.BaseLink
import org.kde.kdeconnect.Backends.BaseLink.PacketReceiver
import org.kde.kdeconnect.DeviceInfo.Companion.loadFromSettings
import org.kde.kdeconnect.DeviceStats.countReceived
import org.kde.kdeconnect.DeviceStats.countSent
import org.kde.kdeconnect.Helpers.DeviceHelper
import org.kde.kdeconnect.Helpers.NotificationHelper
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper
import org.kde.kdeconnect.PairingHandler.PairingCallback
import org.kde.kdeconnect.Plugins.Plugin
import org.kde.kdeconnect.Plugins.Plugin.Companion.getPluginKey
import org.kde.kdeconnect.Plugins.PluginFactory
import org.kde.kdeconnect.UserInterface.MainActivity
import org.kde.kdeconnect_tp.R
import java.io.IOException
import java.security.cert.Certificate
import java.util.Vector
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.CopyOnWriteArrayList

class Device : PacketReceiver {

    data class NetworkPacketWithCallback(val np : NetworkPacket, val callback: SendPacketStatusCallback)

    val context: Context

    @VisibleForTesting
    val deviceInfo: DeviceInfo

    /**
     * The notification ID for the pairing notification.
     * This ID should be only set once, and it should be unique for each device.
     * We use the current time in milliseconds as the ID as default.
     */
    private var notificationId = 0

    @VisibleForTesting
    var pairingHandler: PairingHandler

    private val links = CopyOnWriteArrayList<BaseLink>()

    /**
     * Plugins that have matching capabilities.
     */
    var supportedPlugins: List<String>
        private set

    /**
     * Plugins that have been instantiated successfully. A subset of supportedPlugins.
     */
    val loadedPlugins: ConcurrentMap<String, Plugin> = ConcurrentHashMap()

    /**
     * Plugins that have not been instantiated because of missing permissions.
     * The supportedPlugins that aren't in loadedPlugins will be here.
     */
    val pluginsWithoutPermissions: ConcurrentMap<String, Plugin> = ConcurrentHashMap()

    /**
     * Subset of loadedPlugins that, despite being able to run, will have some limitation because of missing permissions.
     */
    val pluginsWithoutOptionalPermissions: ConcurrentMap<String, Plugin> = ConcurrentHashMap()

    /**
     * Same as loadedPlugins but indexed by incoming packet type
     */
    private var pluginsByIncomingInterface: MultiValuedMap<String, String> = ArrayListValuedHashMap()

    private val settings: SharedPreferences

    private val pairingCallbacks = CopyOnWriteArrayList<PairingCallback>()
    private val pluginsChangedListeners = CopyOnWriteArrayList<PluginsChangedListener>()

    private val sendChannel = Channel<NetworkPacketWithCallback>(Channel.UNLIMITED)
    private var sendCoroutine : Job? = null

    /**
     * Constructor for remembered, already-trusted devices.
     * Given the deviceId, it will load the other properties from SharedPreferences.
     */
    internal constructor(context: Context, deviceId: String) {
        this.context = context
        this.settings = context.getSharedPreferences(deviceId, Context.MODE_PRIVATE)
        this.deviceInfo = loadFromSettings(context, deviceId, settings)
        this.pairingHandler = PairingHandler(this, createDefaultPairingCallback(), PairingHandler.PairState.Paired)
        this.supportedPlugins = Vector(PluginFactory.getAvailablePlugins()) // Assume all are supported until we receive capabilities
        Log.i("Device", "Loading trusted device: ${deviceInfo.name}")
    }

    /**
     * Constructor for devices discovered but not trusted yet.
     * Gets the DeviceInfo by calling link.getDeviceInfo() on the link passed.
     * This constructor also calls addLink() with the link you pass to it, since it's not legal to have an unpaired Device with 0 links.
     */
    internal constructor(context: Context, link: BaseLink) {
        this.context = context
        this.deviceInfo = link.deviceInfo
        this.settings = context.getSharedPreferences(deviceInfo.id, Context.MODE_PRIVATE)
        this.pairingHandler = PairingHandler(this, createDefaultPairingCallback(), PairingHandler.PairState.NotPaired)
        this.supportedPlugins = Vector(PluginFactory.getAvailablePlugins()) // Assume all are supported until we receive capabilities
        Log.i("Device", "Creating untrusted device: " + deviceInfo.name)
        addLink(link)
    }

    fun supportsPacketType(type: String): Boolean =
        deviceInfo.incomingCapabilities?.contains(type) ?: true

    fun interface PluginsChangedListener {
        fun onPluginsChanged(device: Device)
    }

    val connectivityType: String?
        get() = links.firstOrNull()?.name

    val name: String
        get() = deviceInfo.name

    val icon: Drawable
        get() = deviceInfo.type.getIcon(context)

    val deviceType: DeviceType
        get() = deviceInfo.type

    val deviceId: String
        get() = deviceInfo.id

    val certificate: Certificate
        get() = deviceInfo.certificate

    // Returns 0 if the version matches, < 0 if it is older or > 0 if it is newer
    fun compareProtocolVersion(): Int =
        deviceInfo.protocolVersion - DeviceHelper.ProtocolVersion


    val isPaired: Boolean
        get() = pairingHandler.state == PairingHandler.PairState.Paired

    val isPairRequested: Boolean
        get() = pairingHandler.state == PairingHandler.PairState.Requested

    val isPairRequestedByPeer: Boolean
        get() = pairingHandler.state == PairingHandler.PairState.RequestedByPeer

    fun addPairingCallback(callback: PairingCallback) = pairingCallbacks.add(callback)

    fun removePairingCallback(callback: PairingCallback) = pairingCallbacks.remove(callback)

    fun requestPairing() = pairingHandler.requestPairing()

    fun unpair() = pairingHandler.unpair()

    /* This method is called after accepting pair request form GUI */
    fun acceptPairing() {
        Log.i("Device", "Accepted pair request started by the other device")
        pairingHandler.acceptPairing()
    }

    /* This method is called after rejecting pairing from GUI */
    fun cancelPairing() {
        Log.i("Device", "This side cancelled the pair request")
        pairingHandler.cancelPairing()
    }

    private fun createDefaultPairingCallback(): PairingCallback {
        return object : PairingCallback {
            override fun incomingPairRequest() {
                displayPairingNotification()
                pairingCallbacks.forEach(PairingCallback::incomingPairRequest)
            }

            override fun pairingSuccessful() {
                Log.i("Device", "pairing successful, adding to trusted devices list")

                hidePairingNotification()

                // Store current device certificate so we can check it in the future (TOFU)
                deviceInfo.saveInSettings(this@Device.settings)

                // Store as trusted device
                val preferences = context.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE)
                preferences.edit().putBoolean(deviceInfo.id, true).apply()

                try {
                    reloadPluginsFromSettings()

                    pairingCallbacks.forEach(PairingCallback::pairingSuccessful)
                } catch (e: Exception) {
                    Log.e("Device", "Exception in pairingSuccessful. Not unpairing because saving the trusted device succeeded", e)
                }
            }

            override fun pairingFailed(error: String) {
                hidePairingNotification()
                pairingCallbacks.forEach { it.pairingFailed(error) }
            }

            override fun unpaired() {
                Log.i("Device", "unpaired, removing from trusted devices list")
                val preferences = context.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE)
                preferences.edit().remove(deviceInfo.id).apply()

                val devicePreferences = context.getSharedPreferences(deviceInfo.id, Context.MODE_PRIVATE)
                devicePreferences.edit().clear().apply()

                pairingCallbacks.forEach(PairingCallback::unpaired)

                notifyPluginsOfDeviceUnpaired(context, deviceInfo.id)

                reloadPluginsFromSettings()
            }
        }
    }

    //
    // Notification related methods used during pairing
    //
    fun displayPairingNotification() {
        hidePairingNotification()

        notificationId = System.currentTimeMillis().toInt()

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_DEVICE_ID, deviceId)
            putExtra(MainActivity.PAIR_REQUEST_STATUS, MainActivity.PAIRING_PENDING)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val acceptIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_DEVICE_ID, deviceId)
            putExtra(MainActivity.PAIR_REQUEST_STATUS, MainActivity.PAIRING_ACCEPTED)
        }
        val rejectIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_DEVICE_ID, deviceId)
            putExtra(MainActivity.PAIR_REQUEST_STATUS, MainActivity.PAIRING_REJECTED)
        }

        val acceptedPendingIntent = PendingIntent.getActivity(
            context,
            2,
            acceptIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE
        )
        val rejectedPendingIntent = PendingIntent.getActivity(
            context,
            4,
            rejectIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE
        )

        val res = context.resources

        val notificationManager = ContextCompat.getSystemService(context, NotificationManager::class.java)!!

        val verificationKey = SslHelper.getVerificationKey(SslHelper.certificate, deviceInfo.certificate)

        val noti = NotificationCompat.Builder(context, NotificationHelper.Channels.DEFAULT)
            .setContentTitle(res.getString(R.string.pairing_request_from, name))
            .setContentText(res.getString(R.string.pairing_verification_code, verificationKey))
            .setTicker(res.getString(R.string.pair_requested))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_accept_pairing_24dp, res.getString(R.string.pairing_accept), acceptedPendingIntent)
            .addAction(R.drawable.ic_reject_pairing_24dp, res.getString(R.string.pairing_reject), rejectedPendingIntent)
            .setAutoCancel(true)
            .setDefaults(Notification.DEFAULT_ALL)
            .build()

        NotificationHelper.notifyCompat(notificationManager, notificationId, noti)
    }

    fun hidePairingNotification() {
        val notificationManager = ContextCompat.getSystemService(context, NotificationManager::class.java)!!
        notificationManager.cancel(notificationId)
    }

    val isReachable: Boolean
        get() = links.isNotEmpty()

    fun addLink(link: BaseLink) {
        synchronized(sendChannel) {
            if (sendCoroutine == null) {
                sendCoroutine = CoroutineScope(Dispatchers.IO).launch {
                    for ((np, callback) in sendChannel) {
                        sendPacketBlocking(np, callback)
                    }
                }
            }
        }

        // FilesHelper.LogOpenFileCount();
        links.add(link)

        links.sortWith { o1, o2 ->
            o2.linkProvider.priority compareTo o1.linkProvider.priority
        }

        link.addPacketReceiver(this)

        val hasChanges = updateDeviceInfo(link.deviceInfo)

        if (hasChanges || links.size == 1) {
            reloadPluginsFromSettings()
        }
    }

    fun removeLink(link: BaseLink) {
        // FilesHelper.LogOpenFileCount();

        link.removePacketReceiver(this)
        links.remove(link)
        Log.i(
            "KDE/Device",
            "removeLink: ${link.linkProvider.name} -> $name active links: ${links.size}"
        )
        if (links.isEmpty()) {
            reloadPluginsFromSettings()
            synchronized(sendChannel) {
                sendCoroutine?.cancel(CancellationException("Device disconnected"))
                sendCoroutine = null
            }
        }
    }

    fun updateDeviceInfo(newDeviceInfo: DeviceInfo): Boolean {
        var hasChanges = false
        if (deviceInfo.name != newDeviceInfo.name || deviceInfo.type != newDeviceInfo.type) {
            hasChanges = true
            deviceInfo.name = newDeviceInfo.name
            deviceInfo.type = newDeviceInfo.type
            if (isPaired) {
                deviceInfo.saveInSettings(settings)
            }
        }

        val incomingCapabilities = deviceInfo.incomingCapabilities
        val outgoingCapabilities = deviceInfo.outgoingCapabilities
        val newIncomingCapabilities = newDeviceInfo.incomingCapabilities
        val newOutgoingCapabilities = newDeviceInfo.outgoingCapabilities
        if (
            !newIncomingCapabilities.isNullOrEmpty() &&
            !newOutgoingCapabilities.isNullOrEmpty() &&
            (
                incomingCapabilities != newIncomingCapabilities ||
                outgoingCapabilities != newOutgoingCapabilities
            )
        ) {
            hasChanges = true
            Log.i("updateDeviceInfo", "Updating supported plugins according to new capabilities")
            supportedPlugins = Vector(
                PluginFactory.pluginsForCapabilities(
                    newIncomingCapabilities,
                    newOutgoingCapabilities
                )
            )
        }

        return hasChanges
    }

    override fun onPacketReceived(np: NetworkPacket) {
        countReceived(deviceId, np.type)

        if (NetworkPacket.PACKET_TYPE_PAIR == np.type) {
            Log.i("KDE/Device", "Pair packet")
            pairingHandler.packetReceived(np)
            return
        }

        // pluginsByIncomingInterface may not be built yet
        if (pluginsByIncomingInterface.isEmpty) {
            reloadPluginsFromSettings()
        }

        if (!isPaired) {
            // If it is pair packet, it should be captured by "if" at start
            // If not and device is paired, it should be captured by isPaired
            // Else unpair, this handles the situation when one device unpairs,
            // but other don't know like unpairing when wi-fi is off.

            unpair()
        }

        // The following code when `isPaired == false` is NOT USED.
        // It adds support for receiving packets from not trusted devices,
        // but as of March 2023 no plugin implements "onUnpairedDevicePacketReceived".
        notifyPluginPacketReceived(np)
    }

    private fun notifyPluginPacketReceived(np: NetworkPacket) {
        val targetPlugins = pluginsByIncomingInterface[np.type] // Returns an empty collection if the key doesn't exist
        if (targetPlugins.isEmpty()) {
            Log.w("Device", "Ignoring packet with type ${np.type} because no plugin can handle it")
            return
        }
        targetPlugins.map { it to loadedPlugins[it]!! }.forEach { (pluginKey, plugin) ->
            plugin.runCatching {
                if (isPaired) onPacketReceived(np) else onUnpairedDevicePacketReceived(np)
            }.onFailure { e ->
                Log.e("Device", "Exception in ${pluginKey}'s onPacketReceived()", e)
            }
        }
    }

    abstract class SendPacketStatusCallback {
        abstract fun onSuccess()

        abstract fun onFailure(e: Throwable)

        open fun onPayloadProgressChanged(percent: Int) {}
    }

    private val defaultCallback: SendPacketStatusCallback = object : SendPacketStatusCallback() {
        override fun onSuccess() {
        }

        override fun onFailure(e: Throwable) {
            Log.e("Device", "Send packet exception", e)
        }
    }

    /**
     * Send a packet to the device asynchronously
     * @param np The packet
     * @param callback A callback for success/failure
     */
    @AnyThread
    fun sendPacket(np: NetworkPacket, callback: SendPacketStatusCallback) {
        sendChannel.trySend(NetworkPacketWithCallback(np, callback))
    }

    @AnyThread
    fun sendPacket(np: NetworkPacket) = sendPacket(np, defaultCallback)

    @WorkerThread
    fun sendPacketBlocking(np: NetworkPacket, callback: SendPacketStatusCallback): Boolean =
        sendPacketBlocking(np, callback, false)

    @WorkerThread
    fun sendPacketBlocking(np: NetworkPacket): Boolean = sendPacketBlocking(np, defaultCallback, false)

    /**
     * Send `np` over one of this device's connected [.links].
     *
     * @param np                        the packet to send
     * @param callback                  a callback that can receive realtime updates
     * @param sendPayloadFromSameThread when set to true and np contains a Payload, this function
     * won't return until the Payload has been received by the
     * other end, or times out after 10 seconds
     * @return true if the packet was sent ok, false otherwise
     * @see BaseLink.sendPacket
     */
    @WorkerThread
    fun sendPacketBlocking(
        np: NetworkPacket,
        callback: SendPacketStatusCallback,
        sendPayloadFromSameThread: Boolean
    ): Boolean {
        val success = links.any { link ->
            try {
                link.sendPacket(np, callback, sendPayloadFromSameThread)
            } catch (e: IOException) {
                Log.w("KDE/sendPacket", "Failed to send packet", e)
                false
            }.also { sent ->
                countSent(deviceId, np.type, sent)
            }
        }

        if (!success) {
            Log.e(
                "KDE/sendPacket",
                "No device link (of ${links.size} available) could send the packet. Packet ${np.type} to ${deviceInfo.name} lost!"
            )
        }

        return success
    }

    //
    // Plugin-related functions
    //
    fun <T : Plugin> getPlugin(pluginClass: Class<T>): T? {
        val plugin = getPlugin(getPluginKey(pluginClass))
        return plugin?.let(pluginClass::cast)
    }

    fun getPlugin(pluginKey: String): Plugin? = loadedPlugins[pluginKey]

    fun getPluginIncludingWithoutPermissions(pluginKey: String): Plugin? {
        return loadedPlugins[pluginKey] ?: pluginsWithoutPermissions[pluginKey]
    }

    @Synchronized
    private fun addPlugin(pluginKey: String): Boolean {
        val existing = loadedPlugins[pluginKey]
        if (existing != null) {
            if (!existing.isCompatible) {
                Log.d("KDE/addPlugin", "Minimum requirements (e.g. API level) not fulfilled $pluginKey")
                return false
            }

            // Log.w("KDE/addPlugin","plugin already present:" + pluginKey);
            if (existing.checkOptionalPermissions()) {
                Log.d("KDE/addPlugin", "Optional Permissions OK $pluginKey")
                pluginsWithoutOptionalPermissions.remove(pluginKey)
            } else {
                Log.d("KDE/addPlugin", "No optional permission $pluginKey")
                pluginsWithoutOptionalPermissions[pluginKey] = existing
            }
            return true
        }

        val plugin = PluginFactory.instantiatePluginForDevice(context, pluginKey, this) ?: run {
            Log.e("KDE/addPlugin", "could not instantiate plugin: $pluginKey")
            return false
        }

        if (!plugin.isCompatible) {
            Log.d("KDE/addPlugin", "Minimum requirements (e.g. API level) not fulfilled $pluginKey")
            return false
        }

        if (!plugin.checkRequiredPermissions()) {
            Log.d("KDE/addPlugin", "No permission $pluginKey")
            loadedPlugins.remove(pluginKey)
            pluginsWithoutPermissions[pluginKey] = plugin
            return false
        } else {
            Log.d("KDE/addPlugin", "Permissions OK $pluginKey")
            loadedPlugins[pluginKey] = plugin
            pluginsWithoutPermissions.remove(pluginKey)
            if (plugin.checkOptionalPermissions()) {
                Log.d("KDE/addPlugin", "Optional Permissions OK $pluginKey")
                pluginsWithoutOptionalPermissions.remove(pluginKey)
            } else {
                Log.d("KDE/addPlugin", "No optional permission $pluginKey")
                pluginsWithoutOptionalPermissions[pluginKey] = plugin
            }
        }

        return runCatching {
            plugin.onCreate()
        }.onFailure {
            Log.e("KDE/addPlugin", "plugin failed to load $pluginKey", it)
        }.getOrDefault(false)
    }

    @Synchronized
    private fun removePlugin(pluginKey: String): Boolean {
        val plugin = loadedPlugins.remove(pluginKey) ?: return false

        try {
            plugin.onDestroy()
            // Log.e("removePlugin","removed " + pluginKey);
        } catch (e: Exception) {
            Log.e("KDE/removePlugin", "Exception calling onDestroy for plugin $pluginKey", e)
        }

        return true
    }

    fun setPluginEnabled(pluginKey: String, value: Boolean) {
        settings.edit().putBoolean(pluginKey, value).apply()
        reloadPluginsFromSettings()
    }

    fun isPluginEnabled(pluginKey: String): Boolean {
        val enabledByDefault = PluginFactory.getPluginInfo(pluginKey).isEnabledByDefault
        return settings.getBoolean(pluginKey, enabledByDefault)
    }

    fun notifyPluginsOfDeviceUnpaired(context: Context, deviceId: String) {
        for (pluginKey in supportedPlugins) {
            // This is a hacky way to temporarily create plugins just so that they can be notified of the
            // device being unpaired. This else part will only come into picture when 1) the user tries to
            // unpair a device while that device is not reachable or 2) the plugin was never initialized
            // for this device, e.g., the plugins that need additional permissions from the user, and those
            // permissions were never granted.
            val plugin = getPlugin(pluginKey) ?: PluginFactory.instantiatePluginForDevice(context, pluginKey, this)
            plugin?.onDeviceUnpaired(context, deviceId)
        }
    }

    fun reloadPluginsFromSettings() {
        Log.i("Device", "${deviceInfo.name}: reloading plugins")
        val newPluginsByIncomingInterface: MultiValuedMap<String, String> = ArrayListValuedHashMap()

        supportedPlugins.forEach { pluginKey ->
            val pluginInfo = PluginFactory.getPluginInfo(pluginKey)
            val listenToUnpaired = pluginInfo.listenToUnpaired()

            val pluginEnabled = (isPaired || listenToUnpaired) && this.isReachable && isPluginEnabled(pluginKey)

            if (pluginEnabled && addPlugin(pluginKey)) {
                pluginInfo.supportedPacketTypes.forEach { packetType ->
                    newPluginsByIncomingInterface.put(packetType, pluginKey)
                }
            } else {
                removePlugin(pluginKey)
            }
        }

        pluginsByIncomingInterface = newPluginsByIncomingInterface

        onPluginsChanged()
    }

    fun onPluginsChanged() = pluginsChangedListeners.forEach { it.onPluginsChanged(this) }

    fun addPluginsChangedListener(listener: PluginsChangedListener) = pluginsChangedListeners.add(listener)

    fun removePluginsChangedListener(listener: PluginsChangedListener) = pluginsChangedListeners.remove(listener)

    fun disconnect() {
        links.forEach(BaseLink::disconnect)
    }
}
