/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui.background

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import org.kde.kdeconnect.helpers.NotificationHelper
import org.kde.kdeconnect_tp.R
import rikka.shizuku.Shizuku

internal data class ShizukuState(val running: Boolean, val authorized: Boolean, val previouslyRunning: Boolean)

internal object ShizukuSupport {
    private const val PREFERENCES = "ecosystem_background"
    private const val NOTIFICATION_ID = 0x53485A

    fun state(context: Context): ShizukuState {
        val running = runCatching { Shizuku.pingBinder() && !Shizuku.isPreV11() }.getOrDefault(false)
        val authorized = running && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        return ShizukuState(running, authorized,
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean("shizuku_seen", false))
    }

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        Shizuku.addBinderReceivedListenerSticky {
            prefs.edit { putBoolean("shizuku_seen", true) }
            NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID)
        }
        Shizuku.addBinderDeadListener { remind(appContext) }
        // Allow the provider time to receive the binder before checking after process startup.
        Handler(Looper.getMainLooper()).postDelayed({
            val boot = Settings.Global.getInt(appContext.contentResolver, Settings.Global.BOOT_COUNT, -1)
            if (state(appContext).previouslyRunning && !state(appContext).running &&
                prefs.getInt("shizuku_reminded_boot", -2) != boot) {
                remind(appContext)
                prefs.edit { putInt("shizuku_reminded_boot", boot) }
            }
        }, 3000)
    }

    private fun remind(context: Context) {
        if (!state(context).previouslyRunning) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val intent = Intent(context, BackgroundFeaturesActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, NotificationHelper.Channels.DEFAULT)
            .setSmallIcon(R.drawable.ic_settings_white_32dp)
            .setContentTitle(context.getString(R.string.shizuku_restart_title))
            .setContentText(context.getString(R.string.shizuku_restart_hint))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.shizuku_restart_hint)))
            .setContentIntent(pendingIntent).setAutoCancel(true).setSilent(true).build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
