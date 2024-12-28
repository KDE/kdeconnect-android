/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Helpers;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationManagerCompat;

import org.kde.kdeconnect_tp.R;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class NotificationHelper {

    public static class Channels {
        public final static String PERSISTENT = "persistent";
        public final static String DEFAULT = "default";
        public final static String MEDIA_CONTROL = "media_control";

        public final static String FILETRANSFER_DOWNLOAD = "filetransfer";
        public final static String FILETRANSFER_UPLOAD = "filetransfer_upload";
        public final static String FILETRANSFER_ERROR = "filetransfer_error";

        public final static String RECEIVENOTIFICATION = "receive";
        public final static String HIGHPRIORITY = "highpriority";
        public final static String CONTINUEWATCHING = "continuewatching";

    }

    public static void notifyCompat(NotificationManager notificationManager, int notificationId, Notification notification) {
        try {
            notificationManager.notify(notificationId, notification);
        } catch (Exception e) {
            //4.1 will throw an exception about not having the VIBRATE permission, ignore it.
            //https://android.googlesource.com/platform/frameworks/base/+/android-4.2.1_r1.2%5E%5E!/
        }
    }

    public static void notifyCompat(NotificationManager notificationManager, String tag, int notificationId, Notification notification) {
        try {
            notificationManager.notify(tag, notificationId, notification);
        } catch (Exception e) {
            //4.1 will throw an exception about not having the VIBRATE permission, ignore it.
            //https://android.googlesource.com/platform/frameworks/base/+/android-4.2.1_r1.2%5E%5E!/
        }
    }

    public static void initializeChannels(Context context) {
        final NotificationChannelCompat persistentChannel = new NotificationChannelCompat
                .Builder(Channels.PERSISTENT, NotificationManagerCompat.IMPORTANCE_MIN)
                .setName(context.getString(R.string.notification_channel_persistent))
                .build();
        final NotificationChannelCompat defaultChannel = new NotificationChannelCompat
                .Builder(Channels.DEFAULT, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notification_channel_default))
                .build();
        final NotificationChannelCompat mediaChannel = new NotificationChannelCompat
                .Builder(Channels.MEDIA_CONTROL, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.notification_channel_media_control))
                .build();
        final NotificationChannelCompat fileTransferDownloadChannel = new NotificationChannelCompat
                .Builder(Channels.FILETRANSFER_DOWNLOAD, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.notification_channel_filetransfer))
                .setVibrationEnabled(false)
                .build();
        final NotificationChannelCompat fileTransferUploadChannel = new NotificationChannelCompat
                .Builder(Channels.FILETRANSFER_UPLOAD, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.notification_channel_filetransfer_upload))
                .setVibrationEnabled(false)
                .build();
        final NotificationChannelCompat fileTransferErrorChannel = new NotificationChannelCompat
                .Builder(Channels.FILETRANSFER_ERROR, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.notification_channel_filetransfer_error))
                .setVibrationEnabled(false)
                .build();
        final NotificationChannelCompat receiveNotificationChannel = new NotificationChannelCompat
                .Builder(Channels.RECEIVENOTIFICATION, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notification_channel_receivenotification))
                .build();
        final NotificationChannelCompat continueWatchingChannel = new NotificationChannelCompat
                .Builder(Channels.HIGHPRIORITY, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.notification_channel_high_priority))
                .build();
        /* This notification should be highly visible *only* if the user looks at their phone */
        /* It should not be a distraction. It should be a convenient button to press          */
        final NotificationChannelCompat highPriorityChannel = new NotificationChannelCompat
                .Builder(Channels.CONTINUEWATCHING, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.notification_channel_keepwatching))
                .setVibrationEnabled(false)
                .setLightsEnabled(false)
                .setSound(null, null)
                .build();
        final List<NotificationChannelCompat> channels = Arrays.asList(persistentChannel,
                defaultChannel, mediaChannel, fileTransferDownloadChannel, fileTransferUploadChannel,
                fileTransferErrorChannel, receiveNotificationChannel, highPriorityChannel,
                continueWatchingChannel);

        NotificationManagerCompat.from(context).createNotificationChannelsCompat(channels);

        // Delete any notification channels which weren't added.
        // Use this to deprecate old channels.
        NotificationManagerCompat.from(context).deleteUnlistedNotificationChannels(
                channels.stream()
                        .map(NotificationChannelCompat::getId)
                        .collect(Collectors.toList()));
    }

    public static void setPersistentNotificationEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putBoolean("persistentNotification", enabled).apply();
    }

    public static boolean isPersistentNotificationEnabled(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return true;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean("persistentNotification", false);
    }

}
