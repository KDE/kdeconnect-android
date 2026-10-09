/*
 * SPDX-FileCopyrightText: 2017 Nicolas Fella <nicolas.fella@gmx.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.share

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.helpers.NotificationHelper
import org.kde.kdeconnect_tp.BuildConfig
import org.kde.kdeconnect_tp.R
import java.io.File
import java.io.IOException

internal class ReceiveNotification(private val device: Device, private val jobId: Long) {
    private val notificationManager: NotificationManager =
        device.context.getSystemService<NotificationManager>()!!
    private val notificationId: Int = System.currentTimeMillis().toInt()
    private var builder: NotificationCompat.Builder =
        NotificationCompat.Builder(device.context, NotificationHelper.Channels.FILETRANSFER_DOWNLOAD)
            .setSmallIcon(android.R.drawable.stat_sys_download)
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
            putExtra(SharePlugin.CANCEL_SHARE_DEVICE_ID_EXTRA, device.deviceId)
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            device.context,
            0,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        builder.addAction(
            R.drawable.ic_reject_pairing_24dp,
            device.context.getString(R.string.cancel),
            cancelPendingIntent
        )
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
        builder = NotificationCompat.Builder(device.context, NotificationHelper.Channels.FILETRANSFER_COMPLETE)
        builder.setContentTitle(message)
            .setTicker(message)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
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

    fun setURI(destinationUri: Uri, mimeType: String?, filename: String?) {
        /*
         * We only support file URIs (because sending a content uri to another app does not work for security reasons).
         * In effect, that means only the default download folder currently works.
         *
         * TODO: implement our own content provider (instead of support-v4's FileProvider). It should:
         *  - Proxy to real files (in case of the default download folder)
         *  - Proxy to the underlying content uri (in case of a custom download folder)
         */

        // If it's an image, try to show it in the notification
        if (mimeType != null && mimeType.startsWith("image/")) {
            // https://developer.android.com/topic/performance/graphics/load-bitmap
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            try {
                device.context.contentResolver.openInputStream(destinationUri)?.use { decodeBoundsInputStream ->
                    BitmapFactory.decodeStream(decodeBoundsInputStream, null, options)
                }

                options.inJustDecodeBounds = false
                options.inSampleSize = calculateInSampleSize(options, BIG_IMAGE_WIDTH, BIG_IMAGE_HEIGHT)

                val image = device.context.contentResolver.openInputStream(destinationUri)?.use { decodeInputStream ->
                    BitmapFactory.decodeStream(decodeInputStream, null, options)
                }
                if (image != null) {
                    builder.setLargeIcon(image)
                    builder.setStyle(
                        NotificationCompat.BigPictureStyle()
                            .bigPicture(image)
                    )
                }
            } catch (_: IOException) {
            }
        }

        val intent = Intent(Intent.ACTION_VIEW)
        var shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && "file" == destinationUri.scheme) {
            // Nougat and later require "content://" uris instead of "file://" uris
            val file = File(destinationUri.path ?: "")
            val contentUri = FileProvider.getUriForFile(
                device.context,
                BuildConfig.APPLICATION_ID + ".fileprovider",
                file
            )
            intent.setDataAndType(contentUri, mimeType)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri)
        } else {
            intent.setDataAndType(destinationUri, mimeType)
            shareIntent.putExtra(Intent.EXTRA_STREAM, destinationUri)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)

        val resultPendingIntent = PendingIntent.getActivity(
            device.context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        builder.setContentText(device.context.resources.getString(R.string.received_file_text, filename ?: ""))
            .setContentIntent(resultPendingIntent)

        shareIntent = Intent.createChooser(
            shareIntent,
            device.context.getString(R.string.share_received_file, destinationUri.lastPathSegment)
        )
        val sharePendingIntent = PendingIntent.getActivity(
            device.context,
            System.currentTimeMillis().toInt(),
            shareIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val shareAction = NotificationCompat.Action.Builder(
            R.drawable.ic_share_white,
            device.context.getString(R.string.share),
            sharePendingIntent
        )
        builder.addAction(shareAction.build())
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, targetWidth: Int, targetHeight: Int): Int {
        var inSampleSize = 1

        if (options.outHeight > targetHeight || options.outWidth > targetWidth) {
            val halfHeight = options.outHeight / 2
            val halfWidth = options.outWidth / 2

            while ((halfHeight / inSampleSize) >= targetHeight &&
                (halfWidth / inSampleSize) >= targetWidth
            ) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    companion object {
        // https://documentation.onesignal.com/docs/android-customizations#section-big-picture
        private const val BIG_IMAGE_WIDTH = 1440
        private const val BIG_IMAGE_HEIGHT = 720
    }
}
