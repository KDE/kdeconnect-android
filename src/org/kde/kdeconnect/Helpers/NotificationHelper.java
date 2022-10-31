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

public class NotificationHelper {

    public static class Channels {
        public final static String PERSISTENT_NO_DEVICES = "persistent_no_devices";
        public final static String PERSISTENT_WITH_DEVICES = "persistent_with_devices";
        public final static String DEFAULT = "default";
        public final static String MEDIA_CONTROL = "media_control";
        public final static String FILETRANSFER = "filetransfer";
        public final static String SMS_MMS = "sms_mms";
        public final static String RECEIVENOTIFICATION = "receive";
        public final static String HIGHPRIORITY = "highpriority";
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
        final NotificationChannelCompat persistentChannelNoDevices = new NotificationChannelCompat
                .Builder(Channels.PERSISTENT_NO_DEVICES, NotificationManagerCompat.IMPORTANCE_MIN)
                .setName(context.getString(R.string.notification_channel_persistent_no_devices))
                .build();
        final NotificationChannelCompat persistentChannelWithDevices = new NotificationChannelCompat
                .Builder(Channels.PERSISTENT_WITH_DEVICES, NotificationManagerCompat.IMPORTANCE_MIN)
                .setName(context.getString(R.string.notification_channel_persistent_with_devices))
                .build();
        final NotificationChannelCompat defaultChannel = new NotificationChannelCompat
                .Builder(Channels.DEFAULT, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notification_channel_default))
                .build();
        final NotificationChannelCompat mediaChannel = new NotificationChannelCompat
                .Builder(Channels.MEDIA_CONTROL, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.notification_channel_media_control))
                .build();
        final NotificationChannelCompat fileTransferChannel = new NotificationChannelCompat
                .Builder(Channels.FILETRANSFER, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.notification_channel_filetransfer))
                .setVibrationEnabled(false)
                .build();
        final NotificationChannelCompat receiveNotificationChannel = new NotificationChannelCompat
                .Builder(Channels.RECEIVENOTIFICATION, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notification_channel_receivenotification))
                .build();
        final NotificationChannelCompat smsMmsChannel = new NotificationChannelCompat
                .Builder(Channels.SMS_MMS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notification_channel_sms_mms))
                .build();
        final NotificationChannelCompat highPriorityChannel = new NotificationChannelCompat
                .Builder(Channels.HIGHPRIORITY, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.notification_channel_high_priority))
                .build();

        final List<NotificationChannelCompat> channels = Arrays.asList(persistentChannelNoDevices,
                persistentChannelWithDevices, defaultChannel, mediaChannel, fileTransferChannel,
                receiveNotificationChannel, smsMmsChannel, highPriorityChannel);
        NotificationManagerCompat.from(context).createNotificationChannelsCompat(channels);
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
