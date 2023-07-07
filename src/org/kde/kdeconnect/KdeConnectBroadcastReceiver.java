/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.util.Log;

public class KdeConnectBroadcastReceiver extends BroadcastReceiver {

    public void onReceive(Context context, Intent intent) {

        //Log.e("KdeConnect", "Broadcast event: "+intent.getAction());

        String action = intent.getAction();

        switch (action) {
            case Intent.ACTION_MY_PACKAGE_REPLACED:
                Log.i("KdeConnect", "MyUpdateReceiver");
                BackgroundService.Start(context);
                break;
            case Intent.ACTION_BOOT_COMPLETED:
                Log.i("KdeConnect", "KdeConnectBroadcastReceiver");
                BackgroundService.Start(context);
                break;
            case WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION:
            case WifiManager.WIFI_STATE_CHANGED_ACTION:
            case ConnectivityManager.CONNECTIVITY_ACTION:
                Log.i("KdeConnect", "Connection state changed, trying to connect");
                BackgroundService.ForceRefreshConnections(context);
                break;
            case Intent.ACTION_SCREEN_ON:
                BackgroundService.ForceRefreshConnections(context);
                break;
            default:
                Log.i("BroadcastReceiver", "Ignoring broadcast event: " + intent.getAction());
                break;
        }

    }


}
