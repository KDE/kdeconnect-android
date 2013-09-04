package org.kde.connect.Plugins.PingPlugin;

import android.R;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.v4.app.NotificationCompat;
import android.view.View;
import android.widget.Button;

import org.kde.connect.NetworkPackage;
import org.kde.connect.Plugins.Plugin;


public class PingPlugin extends Plugin {

    /*static {
        PluginFactory.registerPlugin(PingPlugin.class);
    }*/

    @Override
    public String getPluginName() {
        return "plugin_ping";
    }

    @Override
    public String getDisplayName() {
        return context.getResources().getString(org.kde.kdeconnect.R.string.pref_plugin_ping);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(org.kde.kdeconnect.R.string.pref_plugin_ping_desc);
    }

    @Override
    public Drawable getIcon() {
        return context.getResources().getDrawable(org.kde.kdeconnect.R.drawable.icon);
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

            Notification noti = new NotificationCompat.Builder(context)
                    .setContentTitle(device.getName())
                    .setContentText("Ping!")
                    .setTicker("Ping!")
                    .setSmallIcon(R.drawable.ic_dialog_alert)
                    .setAutoCancel(true)
                    .setDefaults(Notification.DEFAULT_SOUND)
                    .build();

            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.notify(42 /*a unique id to create only one notification*/, noti);
            return true;

        }
        return false;
    }

    @Override
    public AlertDialog getErrorDialog(Context baseContext) {
        return null;
    }

    @Override
    public Button getInterfaceButton(Activity activity) {
        Button b = new Button(activity);
        b.setText("Send ping"); //TODO: i18n
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                device.sendPackage(new NetworkPackage(NetworkPackage.PACKAGE_TYPE_PING));
            }
        });
        return b;
    }

}
