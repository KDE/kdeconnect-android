package org.kde.kdeconnect;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.util.Log;

public class KdeConnectBroadcastReceiver extends BroadcastReceiver
{


    public void onReceive(Context context, Intent intent) {

        //Log.e("KdeConnect", "Broadcast event: "+intent.getAction());

        String action = intent.getAction();

        if(action.equals(Intent.ACTION_PACKAGE_REPLACED)) {
            Log.i("KdeConnect", "UpdateReceiver");
            if (!intent.getData().getSchemeSpecificPart().equals(context.getPackageName())) {
                Log.i("KdeConnect", "Ignoring, it's not me!");
                return;
            }
            BackgroundService.RunCommand(context, new BackgroundService.InstanceCallback() {
                @Override
                public void onServiceStart(BackgroundService service) {

                }
            });
        } else if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            Log.i("KdeConnect", "KdeConnectBroadcastReceiver");
            BackgroundService.RunCommand(context, new BackgroundService.InstanceCallback() {
                @Override
                public void onServiceStart(BackgroundService service) {

                }
            });
        } else if (action.equals(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION)
                || action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)
                || action.equals(ConnectivityManager.CONNECTIVITY_ACTION)
                ) {
            Log.i("KdeConnect", "Connection state changed, trying to connect");
            BackgroundService.RunCommand(context, new BackgroundService.InstanceCallback() {
                @Override
                public void onServiceStart(BackgroundService service) {
                    service.onNetworkChange();
                }
            });
        } else if (action.equals(Intent.ACTION_SCREEN_ON)) {
            BackgroundService.RunCommand(context, new BackgroundService.InstanceCallback() {
                @Override
                public void onServiceStart(BackgroundService service) {
                    service.onNetworkChange();
                }
            });
        } else {
            Log.i("KdeConnectBroadcastReceiver", "Ignoring broadcast event: "+intent.getAction());
        }

    }



}
