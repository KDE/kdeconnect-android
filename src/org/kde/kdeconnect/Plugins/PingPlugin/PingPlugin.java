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
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.view.View;
import android.widget.Button;

import org.kde.kdeconnect.NetworkPackage;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.UserInterface.MainActivity;
import org.kde.kdeconnect_tp.R;


public class PingPlugin extends Plugin {

    @Override
    public String getPluginName() {
        return "plugin_ping";
    }

    @Override
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_ping);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_ping_desc);
    }

    @Override
    public Drawable getIcon() {
        return context.getResources().getDrawable(R.drawable.icon);
    }

    @Override
    public boolean hasSettings() {
        return false;
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public void onDestroy() {

    }

    @Override
    public boolean onPackageReceived(NetworkPackage np) {

        //Log.e("PingPackageReceiver", "onPackageReceived");
        if (np.getType().equals(NetworkPackage.PACKAGE_TYPE_PING)) {
            //Log.e("PingPackageReceiver", "was a ping!");

            TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
            stackBuilder.addParentStack(MainActivity.class);
            stackBuilder.addNextIntent(new Intent(context, MainActivity.class));
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
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setAutoCancel(true)
                    .setDefaults(Notification.DEFAULT_ALL)
                    .build();

            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.notify(id, noti);
            return true;

        }
        return false;
    }

    @Override
    public AlertDialog getErrorDialog(Activity deviceActivity) {
        return null;
    }

    @Override
    public Button getInterfaceButton(Activity activity) {
        Button b = new Button(activity);
        b.setText(R.string.send_ping);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                device.sendPackage(new NetworkPackage(NetworkPackage.PACKAGE_TYPE_PING));
            }
        });
        return b;
    }

}
