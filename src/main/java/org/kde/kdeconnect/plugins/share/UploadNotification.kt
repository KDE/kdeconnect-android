/*
 * SPDX-FileCopyrightText: 2019 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.share

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.preference.PreferenceManager
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.helpers.NotificationHelper

internal class UploadNotification(private val device: Device, private val jobId: Long) {
    private val notificationManager: NotificationManager = device.context.getSystemService<NotificationManager>()!!
    private val notificationId: Int = SystemClock.elapsedRealtime().toInt()
    private var builder: NotificationCompat.Builder =
        NotificationCompat.Builder(device.context, NotificationHelper.Channels.FILETRANSFER_UPLOAD)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setAutoCancel(true)
            .setOngoing(true)
            .setProgress(100, 0, true)

    init {
        addCancelAction()
    }

    fun show() {
        notificationManager.notify(notificationId, builder.build())
    }

    fun cancel() {
        notificationManager.cancel(notificationId)
    }

    fun addCancelAction() {
        val cancelIntent = Intent(device.context, ShareBroadcastReceiver::class.java).apply {
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            action = SharePlugin.ACTION_CANCEL_SHARE
            putExtra(SharePlugin.CANCEL_SHARE_BACKGROUND_JOB_ID_EXTRA, jobId)
        }

        // Use a unique request code: extras are ignored when matching PendingIntents, so with a fixed one
        // all the cancel buttons would share a single PendingIntent with the job id of the latest notification
        val cancelPendingIntent = PendingIntent.getBroadcast(
            device.context,
            jobId.toInt(),
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        builder.addAction(org.kde.kdeconnect_tp.R.drawable.ic_reject_pairing_24dp, device.context.getString(org.kde.kdeconnect_tp.R.string.cancel), cancelPendingIntent)
    }

    fun setTitle(title: String) {
        builder.setContentTitle(title)
        builder.setTicker(title)
    }

    fun setProgress(progress: Int, progressMessage: String) {
        builder.setProgress(100, progress, false)
        builder.setContentText(progressMessage)
        builder.setStyle(NotificationCompat.BigTextStyle().bigText(progressMessage))
    }

    fun setFinished(message: String) {
        builder = NotificationCompat.Builder(device.context, NotificationHelper.Channels.FILETRANSFER_UPLOAD)
        builder.setContentTitle(message)
            .setTicker(message)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setAutoCancel(true)
            .setOngoing(false)

        val prefs = PreferenceManager.getDefaultSharedPreferences(device.context)
        if (prefs.getBoolean("share_notification_preference", true)) {
            builder.setDefaults(Notification.DEFAULT_ALL)
        }
    }

    fun setFailed(message: String) {
        setFinished(message)
        builder.setSmallIcon(android.R.drawable.stat_notify_error)
            .setChannelId(NotificationHelper.Channels.FILETRANSFER_ERROR)
    }

}

