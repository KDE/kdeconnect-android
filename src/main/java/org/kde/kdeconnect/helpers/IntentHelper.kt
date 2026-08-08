/*
 * SPDX-FileCopyrightText: 2020 Vincent Blücher <vincent.bluecher@gmail.com>
 * SPDX-FileCopyrightText: 2026 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.helpers

import android.app.PendingIntent
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.URLUtil
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.IntentCompat
import androidx.core.os.BundleCompat
import org.kde.kdeconnect.helpers.LifecycleHelper.isInForeground
import org.kde.kdeconnect_tp.R
import kotlin.text.endsWith

object IntentHelper {
    /**
     * On API 29+: post a high priority notification which starts the given Intent when clicked
     * On API <=28: launch a given Intent directly since no background restrictions apply on these platforms.
     * @param context the Context from which the Intent is started
     * @param intent the Intent to be started
     * @param title a title which is shown in the notification on Android 10+
     */
    fun startActivityFromBackgroundOrCreateNotification(context: Context, intent: Intent, title: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isInForeground) {
            val flags = PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, flags)
            val notification = NotificationCompat.Builder(context, NotificationHelper.Channels.HIGHPRIORITY)
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, true)
                .setContentTitle(title)
                .setContentText(context.getString(R.string.tap_to_open))
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            val id = System.currentTimeMillis().toInt()
            NotificationManagerCompat.from(context).notify(id, notification)
        } else {
            context.startActivity(intent)
        }
    }

    fun streamsFromIntent(intent: Intent): List<Uri> {
        val extras = intent.extras
        if (extras == null || !extras.containsKey(Intent.EXTRA_STREAM)) {
            return emptyList()
        }
        val uriList = if (Intent.ACTION_SEND_MULTIPLE == intent.action) {
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                ?.filterNotNull()
                ?: emptyList()
        } else {
            listOfNotNull(BundleCompat.getParcelable(extras, Intent.EXTRA_STREAM, Uri::class.java))
        }
        return uriList
    }

    fun parseIntentUrls(intent: Intent): List<String> {
        val extras = intent.extras
        val text = extras?.getString(Intent.EXTRA_TEXT)?.trim()
            ?: return emptyList()

        // Detect shared YouTube videos, so we can open them in the browser instead of as text
        val subject = extras.getString(Intent.EXTRA_SUBJECT)
        if (subject != null && subject.endsWith("YouTube")) {
            val index = text.indexOf(": http://youtu.be/")
            if (index > 0) {
                return listOf(text.substring(index + 2)) //Skip ": "
            }
        }

        if (text.contains("\n")) {
            // Firefox separates the links with an empty newline (ie: \n\n).
            // Chrome sets the mime type to text/uri-list and prefixes each link with a number 1., 2., etc.
            val isUriList = intent.clipData?.description?.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST) == true

            var urls = text.split("\n").filter { it.isNotBlank() }
            if (isUriList) {
                urls = urls.map { it.replace(Regex("""^\d+\.\s*"""), "") }
            }

            if (urls.all { isUrl(it) }) {
                return urls
            }
        } else if (isUrl(text)) {
            return listOf(text)
        }

        return emptyList()
    }

    private fun isUrl(text: String): Boolean =
        URLUtil.isHttpUrl(text) || URLUtil.isHttpsUrl(text)
}
