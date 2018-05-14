package org.kde.kdeconnect.Helpers;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;

public class NotificationHelper {

    public static class Channels {
        public final static String PERSISTENT = "persistent";
        public final static String DEFAULT = "default";
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

    public static void initializeChannels(NotificationManager manager) {

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            return;
        }


        {
            NotificationChannel channel = new NotificationChannel(Channels.DEFAULT, "Other notifications", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Rest of KDE Connect notifications");
            manager.createNotificationChannel(channel);
        }

        {
            NotificationChannel channel = new NotificationChannel(Channels.PERSISTENT, "Persistent indicator", NotificationManager.IMPORTANCE_MIN);
            channel.setDescription("Always present running indicator");
            manager.createNotificationChannel(channel);
        }

    }

}
