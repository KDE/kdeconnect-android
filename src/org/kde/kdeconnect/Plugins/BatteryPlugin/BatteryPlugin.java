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

package org.kde.kdeconnect.Plugins.BatteryPlugin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import org.kde.kdeconnect.NetworkPackage;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect_tp.R;


public class BatteryPlugin extends Plugin {

    public final static String PACKAGE_TYPE_BATTERY = "kdeconnect.battery";
    public final static String PACKAGE_TYPE_BATTERY_REQUEST = "kdeconnect.battery.request";

    // keep these fields in sync with kdeconnect-kded:BatteryPlugin.h:ThresholdBatteryEvent
    private static final int THRESHOLD_EVENT_NONE= 0;
    private static final int THRESHOLD_EVENT_BATTERY_LOW = 1;

    private NetworkPackage lastInfo = null;

    @Override
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_battery);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_battery_desc);
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent batteryIntent) {

            Intent batteryChargeIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int level = batteryChargeIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryChargeIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 1);
            int currentCharge = level*100 / scale;
            boolean isCharging = (0 != batteryChargeIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0));
            boolean lowBattery = Intent.ACTION_BATTERY_LOW.equals(batteryIntent.getAction());
            int thresholdEvent = lowBattery? THRESHOLD_EVENT_BATTERY_LOW : THRESHOLD_EVENT_NONE;

            if (lastInfo != null
                && isCharging == lastInfo.getBoolean("isCharging")
                && currentCharge == lastInfo.getInt("currentCharge")
                && thresholdEvent == lastInfo.getInt("thresholdEvent")
            ) {

                //Do not send again if nothing has changed
                return;

            } else {

                NetworkPackage np = new NetworkPackage(PACKAGE_TYPE_BATTERY);
                np.set("currentCharge", currentCharge);
                np.set("isCharging", isCharging);
                np.set("thresholdEvent", thresholdEvent);
                device.sendPackage(np);
                lastInfo = np;

            }

        }
    };

    @Override
    public boolean onCreate() {
        context.registerReceiver(receiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        context.registerReceiver(receiver, new IntentFilter(Intent.ACTION_BATTERY_LOW));
        return true;
    }

    @Override
    public void onDestroy() {
        //It's okay to call this only once, even though we registered it for two filters
        context.unregisterReceiver(receiver);
    }

    @Override
    public boolean onPackageReceived(NetworkPackage np) {

        if (np.getBoolean("request")) {
            if (lastInfo != null) {
                device.sendPackage(lastInfo);
            }
        }

        return true;
    }

    @Override
    public String[] getSupportedPackageTypes() {
        String[] packetTypes = {PACKAGE_TYPE_BATTERY_REQUEST};
        return packetTypes;
    }

    @Override
    public String[] getOutgoingPackageTypes() {
        String[] packetTypes = {PACKAGE_TYPE_BATTERY};
        return packetTypes;
    }

}
