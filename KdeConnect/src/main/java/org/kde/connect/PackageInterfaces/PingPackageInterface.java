package org.kde.connect.PackageInterfaces;

import android.R;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.util.Log;

import org.kde.connect.Device;
import org.kde.connect.NetworkPackage;


public class PingPackageInterface extends BasePackageInterface {

    Context context;

    public PingPackageInterface(Context ctx) {
        context = ctx;
    }

    public void sendPing() {
        Log.e("PingPackageInterface", "sendPing to "+countLinkedDevices());

        NetworkPackage lastPackage = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_PING);
        sendPackage(lastPackage);
    }

    @Override
    public void onPackageReceived(Device d, NetworkPackage np) {
        Log.e("PingPackageReceiver", "onPackageReceived");
        if (np.getType().equals(NetworkPackage.PACKAGE_TYPE_PING)) {
            Log.e("PingPackageReceiver", "was a ping!");

            Notification noti = new Notification.Builder(context)
                    .setContentTitle(d.getName())
                    .setContentText("Ping!")
                    .setTicker("Ping!")
                    .setSmallIcon(R.drawable.ic_dialog_alert)
                    .setAutoCancel(true)
                    .setDefaults(Notification.DEFAULT_SOUND)
                    .build();

            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.notify(0, noti);

        }
    }

}
