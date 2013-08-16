package org.kde.connect.Plugins;

import android.R;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.util.Log;

import org.kde.connect.NetworkPackage;


public class PingPlugin extends Plugin {

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

            Notification noti = new Notification.Builder(context)
                    .setContentTitle(device.getName())
                    .setContentText("Ping!")
                    .setTicker("Ping!")
                    .setSmallIcon(R.drawable.ic_dialog_alert)
                    .setAutoCancel(true)
                    .setDefaults(Notification.DEFAULT_SOUND)
                    .build();

            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.notify(0, noti);
            return true;

        }
        return false;
    }

    public void sendPing() {
        Log.e("PingPlugin", "sendPing");
        NetworkPackage lastPackage = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_PING);
        device.sendPackage(lastPackage);
    }

}
