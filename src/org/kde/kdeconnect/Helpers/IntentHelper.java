/*
 * Copyright 2020 Vincent Blücher <vincent.bluecher@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License or (at your option) version 3 or any later version
 * accepted by the membership of KDE e.V. (or its successor approved
 * by the membership of KDE e.V.), which shall act as a proxy
 * defined in Section 14 of version 3 of the license.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kde.kdeconnect.Helpers;

import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.kde.kdeconnect.MyApplication;
import org.kde.kdeconnect_tp.R;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class IntentHelper {

    /**
     * On API 29+: post a high priority notification which starts the given Intent when clicked
     * On API <=28: launch a given Intent directly since no background restrictions apply on these platforms.
     * @param context the Context from which the Intent is started
     * @param intent the Intent to be started
     * @param title a title which is shown in the notification on Android 10+
     */
    public static void startActivityFromBackground(Context context, Intent intent, String title) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !MyApplication.isInForeground()) {
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT);
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
