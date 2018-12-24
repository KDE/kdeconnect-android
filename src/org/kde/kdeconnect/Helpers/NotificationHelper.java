package org.kde.kdeconnect.Helpers;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

import org.kde.kdeconnect_tp.R;

public class NotificationHelper {

    public static class Channels {
        public final static String PERSISTENT = "persistent";
        public final static String DEFAULT = "default";
        public final static String MEDIA_CONTROL = "media_control";
        public final static String FILETRANSFER = "filetransfer";
        public final static String RECEIVENOTIFICATION = "receive";
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

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationChannel persistentChannel = new NotificationChannel(
                Channels.PERSISTENT,
                context.getString(R.string.notification_channel_persistent),
                NotificationManager.IMPORTANCE_MIN);

        manager.createNotificationChannel(persistentChannel);

        manager.createNotificationChannel(new NotificationChannel(
                Channels.DEFAULT,
                context.getString(R.string.notification_channel_default),
                NotificationManager.IMPORTANCE_DEFAULT)
        );

        manager.createNotificationChannel(new NotificationChannel(
                Channels.MEDIA_CONTROL,
                context.getString(R.string.notification_channel_media_control),
                NotificationManager.IMPORTANCE_LOW)
        );

        NotificationChannel fileTransfer = new NotificationChannel(
                Channels.FILETRANSFER,
                context.getString(R.string.notification_channel_filetransfer),
                NotificationManager.IMPORTANCE_LOW);

        fileTransfer.enableVibration(false);

        manager.createNotificationChannel(fileTransfer);

        manager.createNotificationChannel(new NotificationChannel(
                Channels.RECEIVENOTIFICATION,
                context.getString(R.string.notification_channel_receivenotification),
                NotificationManager.IMPORTANCE_DEFAULT)
        );

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
