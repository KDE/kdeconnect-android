/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui.background

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothDevice
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.pm.PackageManager
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.ui.SettingsFragment
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.core.net.toUri
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.plugins.clipboard.ClipboardSyncManager
import org.kde.kdeconnect.plugins.clipboard.ClipboardSyncSettings
import org.kde.kdeconnect.plugins.notifications.NotificationReceiver
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect_tp.R
import rikka.shizuku.Shizuku

class BackgroundFeaturesActivity : AppCompatActivity() {
    private var shizukuState by mutableStateOf(ShizukuState(false, false, false))
    private var healthState by mutableStateOf(HealthCheckState(ShizukuState(false, false, false), false, false, false, "", "", false))
    private var disconnectEntries by mutableStateOf(emptyList<DisconnectLogEntry>())
    private var labEntries by mutableStateOf(emptyList<ConnectionLabRow>())
    private var showingHealth = false
    private var showingDisconnectLog = false
    private var showingClipboardSync = false
    private var showingCompanion = false
    private var showingConnectionLab = false
    private var pairingBusy by mutableStateOf(false)
    private var companionMessage by mutableStateOf<Int?>(null)
    private var pendingCompanionId: String? = null
    private val companionChooser = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        pairingBusy = false
        if (result.resultCode == RESULT_OK && Build.VERSION.SDK_INT >= 33) {
            val info = result.data?.getParcelableExtra(CompanionDeviceManager.EXTRA_ASSOCIATION, AssociationInfo::class.java)
            val peerId = pendingCompanionId
            if (info != null && peerId != null) completeCompanion(info, peerId)
        }
        pendingCompanionId = null
        refresh()
    }
    private val bluetoothEnable = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) pendingCompanionId?.let(::pairCompanion)
        else { pairingBusy = false; pendingCompanionId = null }
    }
    private val discoverablePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) openDiscoverablePrompt() else companionMessage = R.string.companion_permission_needed
    }
    private val bluetoothPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.BLUETOOTH_CONNECT] == true && permissions[Manifest.permission.BLUETOOTH_SCAN] == true) pendingCompanionId?.let(::pairCompanion)
        else { pairingBusy = false; pendingCompanionId = null; companionMessage = R.string.companion_permission_needed }
    }
    private val wifiDirectPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) BackgroundService.ForceRefreshConnections(this)
    }
    private var clipboardSettings by mutableStateOf(ClipboardSyncSettings(false, false, true, true))
    private val receivedListener = Shizuku.OnBinderReceivedListener { runOnUiThread(::refresh) }
    private val deadListener = Shizuku.OnBinderDeadListener { runOnUiThread(::refresh) }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, _ -> runOnUiThread(::refresh) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingCompanionId = savedInstanceState?.getString("pending_companion")
        pairingBusy = pendingCompanionId != null
        showingCompanion = intent.getStringExtra(EXTRA_SCREEN) == SCREEN_COMPANION
        showingHealth = intent.getStringExtra(EXTRA_SCREEN) == SCREEN_HEALTH
        showingDisconnectLog = intent.getStringExtra(EXTRA_SCREEN) == SCREEN_DISCONNECT_LOG
        showingClipboardSync = intent.getStringExtra(EXTRA_SCREEN) == SCREEN_CLIPBOARD_SYNC
        showingConnectionLab = intent.getStringExtra(EXTRA_SCREEN) == SCREEN_CONNECTION_LAB
        if (showingConnectionLab) {
            val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.NEARBY_WIFI_DEVICES
                else Manifest.permission.ACCESS_FINE_LOCATION
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                wifiDirectPermission.launch(permission)
            }
        }
        when (intent.getStringExtra(EXTRA_LAB_ACTION)) {
            LAB_ACTION_START -> ConnectionLab.start(this, intent.getStringExtra(EXTRA_SCENARIO).orEmpty(), ClipboardSyncManager.pendingDeliveryCount())
            LAB_ACTION_DROP -> ConnectionLab.markDrop(intent.getStringExtra(EXTRA_SCENARIO).orEmpty())
            LAB_ACTION_NOTE -> ConnectionLab.recordNote(intent.getStringExtra(EXTRA_SCENARIO).orEmpty())
            LAB_ACTION_FINISH -> ConnectionLab.finish(this, ClipboardSyncManager.pendingDeliveryCount())
            LAB_ACTION_EXPORT -> java.io.File(filesDir, "connection-lab.tsv").writeText(ConnectionLab.exportTsv(this))
        }
        Shizuku.addBinderReceivedListenerSticky(receivedListener)
        Shizuku.addBinderDeadListener(deadListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        refresh()
        setContent {
            KdeTheme(this) {
                if (showingCompanion) {
                    CompanionSetupScreen(this, pairingBusy, companionMessage, onBack = { finish() },
                        onPair = ::pairCompanion, onRemove = { CompanionSupport.remove(this, it); refresh() },
                        onCheck = { CompanionSupport.restore(this); refresh() }, onDiscoverable = {
                            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED) {
                                openDiscoverablePrompt()
                            } else discoverablePermission.launch(Manifest.permission.BLUETOOTH_ADVERTISE)
                        })
                } else if (showingDisconnectLog) {
                    DisconnectLogScreen(disconnectEntries, onBack = { finish() }, onRefresh = ::refresh)
                } else if (showingConnectionLab) {
                    ConnectionLabScreen(
                        rows = labEntries,
                        onBack = { finish() },
                        onStart = { ConnectionLab.start(this, it, ClipboardSyncManager.pendingDeliveryCount()); refreshLab() },
                        onDrop = { ConnectionLab.markDrop(); refreshLab() },
                        onFinish = { ConnectionLab.finish(this, ClipboardSyncManager.pendingDeliveryCount()); refreshLab() },
                        onExport = { exportConnectionLab() },
                    )
                } else if (showingHealth) {
                    HealthCheckScreen(healthState.copy(keepAlive = KeepAliveSupport.report, keepAliveBusy = KeepAliveSupport.busy), onBack = { finish() }, onCheck = ::refresh,
                        onOpenShizuku = { openSettings(Intent(this, BackgroundFeaturesActivity::class.java).putExtra(EXTRA_SCREEN, SCREEN_SHIZUKU)) },
                        onOpenBattery = ::openBatterySettings,
                        onOpenCompanion = {
                            openSettings(Intent(this, BackgroundFeaturesActivity::class.java).putExtra(EXTRA_SCREEN, SCREEN_COMPANION))
                        },
                        onApplyKeepAlive = { KeepAliveSupport.refresh(apply = true) },
                        onOpenNotificationSettings = {
                            openSettings(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                .putExtra(Settings.EXTRA_CHANNEL_ID, org.kde.kdeconnect.helpers.NotificationHelper.Channels.PERSISTENT))
                        },
                        onOpenConnection = { openSettings(Intent(Settings.ACTION_WIFI_SETTINGS)) },
                        onOpenNotifications = { openSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                        onOpenOemSettings = ::openOemAutostart,
                        onOpenOemBattery = ::openOemBatterySettings,
                        onOpenClipboardSettings = { openClipboardSync() },
                        onOpenShizukuSetup = { openShizukuSetup() },
                        onConfirmAutostart = {
                            getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit { putBoolean(KEY_OEM_AUTOSTART_CONFIRMED, true) }
                            refresh()
                        })
                } else if (showingClipboardSync) {
                    ClipboardSyncScreen(
                        settings = clipboardSettings,
                        watcherActive = ClipboardSyncManager.watcherActive,
                        automaticAvailable = ClipboardSyncManager.isAutomaticAvailable(this),
                        onBack = { finish() },
                        onSettingsChanged = { settings ->
                            ClipboardSyncManager.updateSettings(this) { settings }
                            clipboardSettings = settings
                            refresh()
                        },
                        onCheck = ::refresh,
                        onOpenShizuku = { openShizukuSetup() },
                    )
                } else {
                    ShizukuSetupScreen(shizukuState, onBack = { finish() }, onCheck = ::refresh,
                        onOpenShizuku = ::openShizuku,
                        onOpenDebugging = { openSettings(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) },
                        onRequestPermission = ::requestPermission)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pending_companion", pendingCompanionId)
        super.onSaveInstanceState(outState)
    }

    private fun openDiscoverablePrompt() {
        openSettings(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120))
    }

    private fun pairCompanion(peerId: String) {
        if (!CompanionSupport.supported(this)) return
        val peer = KdeConnect.getInstance().getDevice(peerId) ?: return
        if (!peer.isPaired || !org.kde.kdeconnect.helpers.TrustedDevices.isTrustedDevice(this, peerId)) return
        pendingCompanionId = peerId
        pairingBusy = true
        val permissions = arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        if (permissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            bluetoothPermissions.launch(permissions)
            return
        }
        if (getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled != true) {
            bluetoothEnable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        try {
            val request = AssociationRequest.Builder().addDeviceFilter(BluetoothDeviceFilter.Builder().build()).build()
            getSystemService(CompanionDeviceManager::class.java).associate(request, mainExecutor,
                object : CompanionDeviceManager.Callback() {
                    override fun onAssociationPending(chooser: android.content.IntentSender) {
                        if (!isDestroyed) companionChooser.launch(IntentSenderRequest.Builder(chooser).build())
                    }
                    override fun onAssociationCreated(info: AssociationInfo) {
                        // Use application context for persistence even if Android rotated the activity.
                        CompanionSupport.register(applicationContext, info, peerId)
                        if (!isDestroyed) completeCompanion(info, peerId)
                    }
                    override fun onFailure(error: CharSequence?) {
                        if (!isDestroyed) { pairingBusy = false; pendingCompanionId = null; companionMessage = R.string.companion_failed }
                    }
                })
        } catch (_: RuntimeException) {
            pairingBusy = false
            pendingCompanionId = null
            companionMessage = R.string.companion_failed
        }
    }

    private fun completeCompanion(info: AssociationInfo, peerId: String) {
        val observed = CompanionSupport.register(this, info, peerId)
        companionMessage = if (observed) R.string.companion_complete else R.string.companion_observe_failed
        pairingBusy = false
        pendingCompanionId = null
        // A classic Bluetooth association needs a bond and connection for presence detection.
        runCatching {
            val device = getSystemService(BluetoothManager::class.java).adapter.getRemoteDevice(requireNotNull(info.deviceMacAddress).toString())
            if (device.bondState == BluetoothDevice.BOND_NONE) device.createBond()
            PreferenceManager.getDefaultSharedPreferences(this).edit { putBoolean(SettingsFragment.KEY_BLUETOOTH_ENABLED, true) }
            BackgroundService.ForceRefreshConnections(this)
        }.onFailure { companionMessage = R.string.companion_permission_needed }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(receivedListener)
        Shizuku.removeBinderDeadListener(deadListener)
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }

    private fun refresh() {
        ClipboardSyncManager.refresh()
        KeepAliveSupport.refresh()
        clipboardSettings = ClipboardSyncManager.settings(this)
        shizukuState = ShizukuSupport.state(this)
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val listenerComponent = ComponentName(this, NotificationReceiver::class.java).flattenToString()
        val notificationAccess = enabledListeners?.split(':')?.any { it == listenerComponent } == true
        val powerManager = getSystemService(PowerManager::class.java)
        val oemName = when (Build.MANUFACTURER.lowercase()) {
            "xiaomi" -> getString(R.string.health_hyperos)
            "oneplus", "oplus" -> getString(R.string.health_oxygenos)
            else -> getString(R.string.health_oem_generic)
        }
        val oemInstructions = when (Build.MANUFACTURER.lowercase()) {
            "xiaomi" -> getString(R.string.health_oem_hyperos_instructions)
            "oneplus", "oplus" -> getString(R.string.health_oem_oxygenos_instructions)
            else -> getString(R.string.health_oem_generic_instructions)
        }
        healthState = HealthCheckState(
            shizuku = shizukuState,
            batteryUnrestricted = powerManager?.isIgnoringBatteryOptimizations(packageName) == true,
            connectedDevice = KdeConnect.getInstance().devices.values.any { it.isPaired && it.isReachable },
            notificationAccess = notificationAccess,
            oemName = oemName,
            oemInstructions = oemInstructions,
            oemAutostartConfirmed = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                .getBoolean(KEY_OEM_AUTOSTART_CONFIRMED, false),
            clipboardSync = ClipboardSyncManager.settings(this),
            clipboardWatcherActive = ClipboardSyncManager.watcherActive,
            clipboardAutomaticAvailable = ClipboardSyncManager.isAutomaticAvailable(this),
        )
        disconnectEntries = DisconnectLog.entries(this)
        refreshLab()
    }

    private fun refreshLab() { labEntries = ConnectionLab.rows(this) }

    private fun exportConnectionLab() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/tab-separated-values"
            putExtra(Intent.EXTRA_TEXT, ConnectionLab.exportTsv(this@BackgroundFeaturesActivity))
        }
        startActivity(Intent.createChooser(intent, getString(R.string.connection_lab_export)))
    }

    private fun openBatterySettings() {
        openSettings(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            "package:$packageName".toUri()))
    }

    private fun openOemAutostart() {
        val brand = Build.MANUFACTURER.lowercase()
        val intent = when (brand) {
            "xiaomi" -> Intent().setClassName("com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity")
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())
        }
        if (!tryOpenSettings(intent)) {
            openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
        }
    }

    private fun openOemBatterySettings() {
        val intent = if (Build.MANUFACTURER.equals("xiaomi", ignoreCase = true)) {
            Intent().setClassName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
                .putExtra("package_name", packageName)
                .putExtra("package_label", getString(R.string.kde_connect))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())
        }
        if (!tryOpenSettings(intent)) {
            openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
        }
    }

    private fun requestPermission() {
        if (!ShizukuSupport.state(this).running) { refresh(); return }
        runCatching {
            if (Shizuku.shouldShowRequestPermissionRationale()) openShizuku()
            else Shizuku.requestPermission(1)
        }.onFailure { refresh() }
    }

    private fun openClipboardSync() {
        openSettings(Intent(this, BackgroundFeaturesActivity::class.java).putExtra(EXTRA_SCREEN, SCREEN_CLIPBOARD_SYNC))
    }

    private fun openShizukuSetup() {
        openSettings(Intent(this, BackgroundFeaturesActivity::class.java).putExtra(EXTRA_SCREEN, SCREEN_SHIZUKU))
    }

    private fun openShizuku() {
        openSettings(packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            ?: Intent(Intent.ACTION_VIEW, "https://shizuku.rikka.app/download/".toUri()))
    }

    internal fun openSettings(intent: Intent) {
        if (!tryOpenSettings(intent)) {
            Toast.makeText(this, R.string.background_cannot_open, Toast.LENGTH_LONG).show()
        }
    }

    private fun tryOpenSettings(intent: Intent): Boolean = runCatching {
        startActivity(intent)
        true
    }.getOrDefault(false)

    companion object {
        const val EXTRA_SCREEN = "background_screen"
        const val SCREEN_HEALTH = "health"
        const val SCREEN_DISCONNECT_LOG = "disconnect_log"
        const val SCREEN_CLIPBOARD_SYNC = "clipboard_sync"
        const val SCREEN_COMPANION = "companion"
        const val SCREEN_CONNECTION_LAB = "connection_lab"
        const val EXTRA_LAB_ACTION = "lab_action"
        const val EXTRA_SCENARIO = "scenario"
        const val LAB_ACTION_START = "start"
        const val LAB_ACTION_DROP = "drop"
        const val LAB_ACTION_NOTE = "note"
        const val LAB_ACTION_FINISH = "finish"
        const val LAB_ACTION_EXPORT = "export"
        private const val SCREEN_SHIZUKU = "shizuku"
        private const val PREFERENCES = "ecosystem_background"
        private const val KEY_OEM_AUTOSTART_CONFIRMED = "oem_autostart_confirmed"
    }
}
