package org.kde.kdeconnect.Helpers;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;

public class NotificationHelper {

    private static NotificationChannel defaultChannel;

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

    public static String getDefaultChannelId(NotificationManager manager) {

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (defaultChannel == null) {
                String id = "default";
                CharSequence name = "KDE Connect";
                int importance = NotificationManager.IMPORTANCE_DEFAULT;
                defaultChannel = new NotificationChannel(id, name, importance);
                manager.createNotificationChannel(defaultChannel);
            }
            return defaultChannel.getId();
        }
        return null;
    }

}
