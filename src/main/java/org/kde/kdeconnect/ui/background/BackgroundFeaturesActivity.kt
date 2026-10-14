/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui.background

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
    private var showingHealth = false
    private var showingDisconnectLog = false
    private var showingClipboardSync = false
    private var clipboardSettings by mutableStateOf(ClipboardSyncSettings(false, false, true, true))
    private val receivedListener = Shizuku.OnBinderReceivedListener { runOnUiThread(::refresh) }
    private val deadListener = Shizuku.OnBinderDeadListener { runOnUiThread(::refresh) }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, _ -> runOnUiThread(::refresh) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        showingHealth = intent.getStringExtra(EXTRA_SCREEN) == SCREEN_HEALTH
        showingDisconnectLog = intent.getStringExtra(EXTRA_SCREEN) == SCREEN_DISCONNECT_LOG
        showingClipboardSync = intent.getStringExtra(EXTRA_SCREEN) == SCREEN_CLIPBOARD_SYNC
        Shizuku.addBinderReceivedListenerSticky(receivedListener)
        Shizuku.addBinderDeadListener(deadListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        refresh()
        setContent {
            KdeTheme(this) {
                if (showingDisconnectLog) {
                    DisconnectLogScreen(disconnectEntries, onBack = { finish() }, onRefresh = ::refresh)
                } else if (showingHealth) {
                    HealthCheckScreen(healthState, onBack = { finish() }, onCheck = ::refresh,
                        onOpenShizuku = { openSettings(Intent(this, BackgroundFeaturesActivity::class.java).putExtra(EXTRA_SCREEN, SCREEN_SHIZUKU)) },
                        onOpenBattery = ::openBatterySettings,
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
        private const val SCREEN_SHIZUKU = "shizuku"
        private const val PREFERENCES = "ecosystem_background"
        private const val KEY_OEM_AUTOSTART_CONFIRMED = "oem_autostart_confirmed"
    }
}
