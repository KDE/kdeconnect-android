package org.kde.connect.PackageReceivers;

import android.R;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import org.kde.connect.BackgroundToast;
import org.kde.connect.Device;
import org.kde.connect.NetworkPackage;

public class PingPackageReceiver implements BasePackageReceiver {

    Context context;

    public PingPackageReceiver(Context ctx) {
        context = ctx;
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
