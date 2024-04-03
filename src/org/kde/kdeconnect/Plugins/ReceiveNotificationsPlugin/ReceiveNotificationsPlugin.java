/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.ReceiveNotificationsPlugin;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.apache.commons.lang3.ArrayUtils;
import org.kde.kdeconnect.Helpers.NotificationHelper;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect.UserInterface.MainActivity;
import org.kde.kdeconnect_tp.R;

import java.io.InputStream;
import java.util.Objects;

@PluginFactory.LoadablePlugin
public class ReceiveNotificationsPlugin extends Plugin {

    private final static String PACKET_TYPE_NOTIFICATION = "kdeconnect.notification";
    private final static String PACKET_TYPE_NOTIFICATION_REQUEST = "kdeconnect.notification.request";

    @Override
    public @NonNull String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_receive_notifications);
    }

    @Override
    public @NonNull String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_receive_notifications_desc);
    }

    @Override
    public boolean isEnabledByDefault() {
        return false;
    }

    @Override
    public boolean onCreate() {
        // request all existing notifications
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_NOTIFICATION_REQUEST);
        np.set("request", true);
        getDevice().sendPacket(np);
        return true;
    }

    @Override
    public boolean onPacketReceived(final NetworkPacket np) {

        if (!np.has("ticker") || !np.has("appName") || !np.has("id")) {
            Log.e("NotificationsPlugin", "Received notification packet lacks properties");
            return true;
        }

        if (np.getBoolean("silent", false)) {
            return true;
        }

        PendingIntent resultPendingIntent = PendingIntent.getActivity(
                context,
                0,
                new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
        );

        Bitmap largeIcon = null;
        if (np.hasPayload()) {
            int width = context.getResources().getDimensionPixelSize(android.R.dimen.notification_large_icon_width);
            int height = context.getResources().getDimensionPixelSize(android.R.dimen.notification_large_icon_height);
            final InputStream input = np.getPayload().getInputStream();
            largeIcon = BitmapFactory.decodeStream(input);
            np.getPayload().close();

            if (largeIcon != null) {
                //Log.i("NotificationsPlugin", "hasPayload: size=" + largeIcon.getWidth() + "/" + largeIcon.getHeight() + " opti=" + width + "/" + height);
                if (largeIcon.getWidth() > width || largeIcon.getHeight() > height) {
                    // older API levels don't scale notification icons automatically, therefore:
                    largeIcon = Bitmap.createScaledBitmap(largeIcon, width, height, false);
                }
            }
        }

        NotificationManager notificationManager = ContextCompat.getSystemService(context, NotificationManager.class);

        Notification noti = new NotificationCompat.Builder(context, NotificationHelper.Channels.RECEIVENOTIFICATION)
                .setContentTitle(np.getString("appName"))
                .setContentText(np.getString("ticker"))
                .setContentIntent(resultPendingIntent)
                .setTicker(np.getString("ticker"))
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(largeIcon)
                .setAutoCancel(true)
                .setLocalOnly(true)  // to avoid bouncing the notification back to other kdeconnect nodes
                .setDefaults(Notification.DEFAULT_ALL)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(np.getString("ticker")))
                .build();

        NotificationHelper.notifyCompat(notificationManager, "kdeconnectId:" + np.getString("id", "0"), np.getInt("id", 0), noti);

        return true;
    }

    @Override
    public @NonNull String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_NOTIFICATION};
    }

    @Override
    public @NonNull String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_NOTIFICATION_REQUEST};
    }

    @NonNull
    @Override
    protected String[] getRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new String[]{Manifest.permission.POST_NOTIFICATIONS};
        } else {
            return ArrayUtils.EMPTY_STRING_ARRAY;
        }
    }

    @Override
    protected int getPermissionExplanation() {
        return R.string.receive_notifications_permission_explanation;
    }
}
