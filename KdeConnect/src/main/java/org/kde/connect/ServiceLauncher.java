package org.kde.connect;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import org.kde.connect.PackageInterfaces.SmsNotificationPackageInterface;

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
            BackgroundService.RunCommand(context, new BackgroundService.InstanceCallback() {
                @Override
                public void onServiceStart(BackgroundService service) {

                }
            });
        } else if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            Log.e("KdeConnect", "ServiceLauncher");
            BackgroundService.RunCommand(context, new BackgroundService.InstanceCallback() {
                @Override
                public void onServiceStart(BackgroundService service) {

                }
            });
        } else if (action.equals(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION)
                || action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)
                || action.equals(ConnectivityManager.CONNECTIVITY_ACTION)
                ) {
            Log.e("KdeConnect", "Connection state changed, trying to connect");
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
        } else if(action.equals("android.provider.Telephony.SMS_RECEIVED")) {
            Log.e("ServiceLauncher","Sms receiver");
            final Bundle bundle = intent.getExtras();
            if (bundle == null) return;
            BackgroundService.RunCommand(context, new BackgroundService.InstanceCallback() {
                @Override
                public void onServiceStart(BackgroundService service) {
                    SmsNotificationPackageInterface smsnotify = (SmsNotificationPackageInterface)service.getPackageInterface(SmsNotificationPackageInterface.class);
                    if (smsnotify == null) return;
                    try {
                        Object[] pdus = (Object[]) bundle.get("pdus");
                        for (Object pdu : pdus) {
                            SmsMessage message = SmsMessage.createFromPdu((byte[])pdu);
                            smsnotify.smsBroadcastReceived(message);
                        }
                    } catch(Exception e) {
                        e.printStackTrace();
                        Log.e("ServiceLauncher","Sms receiver exception");
                    }
                }
            });
        } else {
            Log.e("ServiceLauncher", "Ignoring broadcast event: "+intent.getAction());
        }

    }
}
