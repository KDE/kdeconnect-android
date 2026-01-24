/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.util.Log

class KdeConnectBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Log.e("KdeConnect", "Broadcast event: "+intent.getAction());

        val action = intent.action

        when (action) {
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.i("KdeConnect", "MyUpdateReceiver")
                BackgroundService.Start(context)
            }

            Intent.ACTION_BOOT_COMPLETED -> {
                Log.i("KdeConnect", "KdeConnectBroadcastReceiver")
                try {
                    BackgroundService.Start(context)
                } catch (e: IllegalStateException) { // To catch ForegroundServiceStartNotAllowedException
                    Log.w("BroadcastReceiver", "Couldn't start the foreground service.", e)
                }
            }

            WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION, WifiManager.WIFI_STATE_CHANGED_ACTION, ConnectivityManager.CONNECTIVITY_ACTION -> {
                Log.i("KdeConnect", "Connection state changed, trying to connect")
                BackgroundService.ForceRefreshConnections(context)
            }

            Intent.ACTION_SCREEN_ON -> try {
                BackgroundService.ForceRefreshConnections(context)
            } catch (e: IllegalStateException) { // To catch ForegroundServiceStartNotAllowedException
                Log.w("BroadcastReceiver", "Couldn't start the foreground service.", e)
            }

            else -> Log.i("BroadcastReceiver", "Ignoring broadcast event: ${intent.action}")
        }
    }
}
