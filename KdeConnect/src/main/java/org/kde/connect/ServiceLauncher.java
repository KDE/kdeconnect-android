package org.kde.connect;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.util.Log;

public class ServiceLauncher extends BroadcastReceiver
{
    public void onReceive(Context context, Intent intent)
    {

        Log.e("KdeConnect", "Broadcast event: "+intent.getAction());

        String action = intent.getAction();
        if(action.equals(Intent.ACTION_PACKAGE_REPLACED)) {
            Log.e("KdeConnect", "UpdateReceiver");
            if (!intent.getData().getSchemeSpecificPart().equals(context.getPackageName())) {
                Log.e("KdeConnect", "Ignoring, it's not me!");
                return;
            }
            BackgroundService.Start(context);
        } else if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            Log.e("KdeConnect", "ServiceLauncher");
            BackgroundService.Start(context);
        } else if (action.equals(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION)) {
            Log.e("KdeConnect", "Connection state changed, trying to connect");
            BackgroundService service = BackgroundService.GetInstance();
            service.reachComputers();
        } else {
            Log.e("KdeConnect", "Ignoring broadcast event: "+intent.getAction());
        }

    }
}
