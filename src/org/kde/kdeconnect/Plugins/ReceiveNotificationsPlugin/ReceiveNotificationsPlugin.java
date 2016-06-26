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
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

import org.kde.kdeconnect.NetworkPackage;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.UserInterface.MaterialActivity;
import org.kde.kdeconnect_tp.R;

import java.io.InputStream;

public class ReceiveNotificationsPlugin extends Plugin {

    public final static String PACKAGE_TYPE_NOTIFICATION = "kdeconnect.notification";
    public final static String PACKAGE_TYPE_NOTIFICATION_REQUEST = "kdeconnect.notification.request";

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
        NetworkPackage np = new NetworkPackage(PACKAGE_TYPE_NOTIFICATION_REQUEST);
        np.set("request", true);
        device.sendPackage(np);
        return true;
    }

    @Override
    public boolean onPackageReceived(final NetworkPackage np) {

        if (!np.has("ticker") || !np.has("appName") || !np.has("id")) {
            Log.e("NotificationsPlugin", "Received notification package lacks properties");
        } else {
            TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
            stackBuilder.addParentStack(MaterialActivity.class);
            stackBuilder.addNextIntent(new Intent(context, MaterialActivity.class));
            PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(
                    0,
                    PendingIntent.FLAG_UPDATE_CURRENT
            );

            Bitmap largeIcon = null;
            if (np.hasPayload()) {
                int width = 64;   // default icon dimensions
                int height = 64;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                    width = context.getResources().getDimensionPixelSize(android.R.dimen.notification_large_icon_width);
                    height = context.getResources().getDimensionPixelSize(android.R.dimen.notification_large_icon_height);
                }
                final InputStream input = np.getPayload();
                largeIcon = BitmapFactory.decodeStream(np.getPayload());
                try { input.close(); } catch (Exception e) { }
                if (largeIcon != null) {
                    //Log.i("NotificationsPlugin", "hasPayload: size=" + largeIcon.getWidth() + "/" + largeIcon.getHeight() + " opti=" + width + "/" + height);
                    if (largeIcon.getWidth() > width || largeIcon.getHeight() > height) {
                        // older API levels don't scale notification icons automatically, therefore:
                        largeIcon = Bitmap.createScaledBitmap(largeIcon, width, height, false);
                    }
                }
            }
            Notification noti = new NotificationCompat.Builder(context)
                    .setContentTitle(np.getString("appName"))
                    .setContentText(np.getString("ticker"))
                    .setContentIntent(resultPendingIntent)
                    .setTicker(np.getString("ticker"))
                    .setSmallIcon(R.drawable.ic_notification)
                    .setLargeIcon(largeIcon)
                    .setAutoCancel(true)
                    .setLocalOnly(true)  // to avoid bouncing the notification back to other kdeconnect nodes
                    .setDefaults(Notification.DEFAULT_ALL)
                    .build();

            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            try {
                // tag all incoming notifications
                notificationManager.notify("kdeconnectId:" + np.getString("id", "0"), np.getInt("id", 0), noti);
            } catch (Exception e) {
                //4.1 will throw an exception about not having the VIBRATE permission, ignore it.
                //https://android.googlesource.com/platform/frameworks/base/+/android-4.2.1_r1.2%5E%5E!/
            }
        }

        return true;
    }

    @Override
    public String[] getSupportedPackageTypes() {
        return new String[]{PACKAGE_TYPE_NOTIFICATION};
    }

    @Override
    public String[] getOutgoingPackageTypes() {
        return new String[]{PACKAGE_TYPE_NOTIFICATION_REQUEST};
    }

}
