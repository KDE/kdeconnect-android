/*
 * SPDX-FileCopyrightText: 2020 Vincent Blücher <vincent.bluecher@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Helpers;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.kde.kdeconnect_tp.R;

public class IntentHelper {

    /**
     * On API 29+: post a high priority notification which starts the given Intent when clicked
     * On API <=28: launch a given Intent directly since no background restrictions apply on these platforms.
     * @param context the Context from which the Intent is started
     * @param intent the Intent to be started
     * @param title a title which is shown in the notification on Android 10+
     */
    public static void startActivityFromBackgroundOrCreateNotification(Context context, Intent intent, String title) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !LifecycleHelper.isInForeground()) {
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_MUTABLE);
            Notification notification = new NotificationCompat
                    .Builder(context, NotificationHelper.Channels.HIGHPRIORITY)
                    .setContentIntent(pendingIntent)
                    .setFullScreenIntent(pendingIntent, true)
                    .setContentTitle(title)
                    .setContentText(context.getString(R.string.tap_to_open))
                    .setSmallIcon(R.drawable.ic_notification)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .build();
            int id = (int) System.currentTimeMillis();
            NotificationManagerCompat.from(context).notify(id, notification);
        } else {
            context.startActivity(intent);
        }
    }
}
