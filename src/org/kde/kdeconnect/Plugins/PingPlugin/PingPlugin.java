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

package org.kde.kdeconnect.Plugins.PingPlugin;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

import org.kde.kdeconnect.NetworkPackage;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.UserInterface.MaterialActivity;
import org.kde.kdeconnect_tp.R;


public class PingPlugin extends Plugin {

    public final static String PACKAGE_TYPE_PING = "kdeconnect.ping";

    @Override
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_ping);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_ping_desc);
    }

    @Override
    public boolean onPackageReceived(NetworkPackage np) {

        if (!np.getType().equals(PACKAGE_TYPE_PING)) {
            Log.e("PingPlugin", "Ping plugin should not receive packets other than pings!");
            return false;
        }

        //Log.e("PingPackageReceiver", "was a ping!");

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addParentStack(MaterialActivity.class);
        stackBuilder.addNextIntent(new Intent(context, MaterialActivity.class));
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(
            0,
            PendingIntent.FLAG_UPDATE_CURRENT
        );

        int id;
        String message;
        if (np.has("message")) {
            message = np.getString("message");
            id = (int)System.currentTimeMillis();
        } else {
            message = "Ping!";
            id = 42; //A unique id to create only one notification
        }

        Notification noti = new NotificationCompat.Builder(context)
                .setContentTitle(device.getName())
                .setContentText(message)
                .setContentIntent(resultPendingIntent)
                .setTicker(message)
                .setSmallIcon(R.drawable.ic_notification)
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .build();

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        try {
            notificationManager.notify(id, noti);
        } catch(Exception e) {
            //4.1 will throw an exception about not having the VIBRATE permission, ignore it.
            //https://android.googlesource.com/platform/frameworks/base/+/android-4.2.1_r1.2%5E%5E!/
        }

        return true;

    }

    @Override
    public String getActionName() {
        return context.getString(R.string.send_ping);
    }

    @Override
    public void startMainActivity(Activity activity) {
        if (device != null) {
            device.sendPackage(new NetworkPackage(PACKAGE_TYPE_PING));
        }
    }

    @Override
    public boolean hasMainActivity() {
        return true;
    }

    @Override
    public boolean displayInContextMenu() {
        return true;
    }

    @Override
    public String[] getSupportedPackageTypes() {
        return new String[]{PACKAGE_TYPE_PING};
    }

    @Override
    public String[] getOutgoingPackageTypes() {
        return new String[]{PACKAGE_TYPE_PING};
    }

}
