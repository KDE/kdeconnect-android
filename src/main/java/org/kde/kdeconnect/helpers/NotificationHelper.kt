/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.helpers

import android.content.Context
import android.os.Build
import android.preference.PreferenceManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import org.kde.kdeconnect_tp.R
import androidx.core.content.edit

object NotificationHelper {
    fun initializeChannels(context: Context) {
        val persistentChannel = NotificationChannelCompat.Builder(Channels.PERSISTENT, NotificationManagerCompat.IMPORTANCE_MIN)
            .setName(context.getString(R.string.notification_channel_persistent))
            .build()
        val defaultChannel = NotificationChannelCompat.Builder(Channels.DEFAULT, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.notification_channel_default))
            .build()
        val mediaChannel = NotificationChannelCompat.Builder(Channels.MEDIA_CONTROL, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(context.getString(R.string.notification_channel_media_control))
            .build()
        val fileTransferDownloadChannel = NotificationChannelCompat.Builder(Channels.FILETRANSFER_DOWNLOAD, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(context.getString(R.string.notification_channel_filetransfer))
            .setVibrationEnabled(false)
            .build()
        val fileTransferDownloadCompleteChannel = NotificationChannelCompat.Builder(Channels.FILETRANSFER_COMPLETE, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(context.getString(R.string.notification_channel_filetransfer_complete))
            .setVibrationEnabled(false)
            .build()
        val fileTransferUploadChannel = NotificationChannelCompat.Builder(Channels.FILETRANSFER_UPLOAD, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(context.getString(R.string.notification_channel_filetransfer_upload))
            .setVibrationEnabled(false)
            .build()
        val fileTransferErrorChannel = NotificationChannelCompat.Builder(Channels.FILETRANSFER_ERROR, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(context.getString(R.string.notification_channel_filetransfer_error))
            .setVibrationEnabled(false)
            .build()
        val receiveNotificationChannel = NotificationChannelCompat.Builder(Channels.RECEIVENOTIFICATION, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.notification_channel_receivenotification))
            .build()
        val  highPriorityChannel= NotificationChannelCompat.Builder(Channels.HIGHPRIORITY, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(context.getString(R.string.notification_channel_high_priority))
            .build()
        /* This notification should be highly visible *only* if the user looks at their phone */
        /* It should not be a distraction. It should be a convenient button to press          */
        val continueWatchingChannel = NotificationChannelCompat.Builder(Channels.CONTINUEWATCHING, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(context.getString(R.string.notification_channel_keepwatching))
            .setVibrationEnabled(false)
            .setLightsEnabled(false)
            .setSound(null, null)
            .build()
        val channels = listOf(
            persistentChannel,
            defaultChannel, mediaChannel, fileTransferDownloadChannel, fileTransferDownloadCompleteChannel, fileTransferUploadChannel,
            fileTransferErrorChannel, receiveNotificationChannel, highPriorityChannel,
            continueWatchingChannel
        )

        val nm = NotificationManagerCompat.from(context)

        nm.createNotificationChannelsCompat(channels)

        // Delete any notification channels which weren't added.
        // Use this to deprecate old channels.
        nm.deleteUnlistedNotificationChannels(channels.map { channel -> channel.id })
    }

    fun setPersistentNotificationEnabled(context: Context?, enabled: Boolean) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit { putBoolean("persistentNotification", enabled) }
    }

    fun isPersistentNotificationEnabled(context: Context?): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return true
        }
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getBoolean("persistentNotification", false)
    }

    object Channels {
        const val PERSISTENT: String = "persistent"
        const val DEFAULT: String = "default"
        const val MEDIA_CONTROL: String = "media_control"

        const val FILETRANSFER_DOWNLOAD: String = "filetransfer"
        const val FILETRANSFER_UPLOAD: String = "filetransfer_upload"
        const val FILETRANSFER_ERROR: String = "filetransfer_error"
        const val FILETRANSFER_COMPLETE: String = "filetransfer_complete"

        const val RECEIVENOTIFICATION: String = "receive"
        const val HIGHPRIORITY: String = "highpriority"
        const val CONTINUEWATCHING: String = "continuewatching"
    }
}
