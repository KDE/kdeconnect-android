/*
 * Copyright 2014 Albert Vaca Cintora <albertvaka@gmail.com>
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

package org.kde.kdeconnect.Plugins.ReceiveNotificationsPlugin;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.kde.kdeconnect.Helpers.NotificationHelper;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect.UserInterface.MainActivity;
import org.kde.kdeconnect_tp.R;

import java.io.InputStream;

@PluginFactory.LoadablePlugin
public class ReceiveNotificationsPlugin extends Plugin {

    private final static String PACKET_TYPE_NOTIFICATION = "kdeconnect.notification";
    private final static String PACKET_TYPE_NOTIFICATION_REQUEST = "kdeconnect.notification.request";

    @Override
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_receive_notifications);
    }

    @Override
    public String getDescription() {
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
        device.sendPacket(np);
        return true;
    }

    @Override
    public boolean onPacketReceived(final NetworkPacket np) {

        if (!np.has("ticker") || !np.has("appName") || !np.has("id")) {
            Log.e("NotificationsPlugin", "Received notification package lacks properties");
            return true;
        }

        if (np.getBoolean("silent", false)) {
            return true;
        }

        PendingIntent resultPendingIntent = PendingIntent.getActivity(
                context,
                0,
                new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT
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
    public String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_NOTIFICATION};
    }

    @Override
    public String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_NOTIFICATION_REQUEST};
    }
}
