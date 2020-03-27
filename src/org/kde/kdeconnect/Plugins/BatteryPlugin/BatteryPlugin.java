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

import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect_tp.R;

@PluginFactory.LoadablePlugin
public class BatteryPlugin extends Plugin {

    private final static String PACKET_TYPE_BATTERY = "kdeconnect.battery";
    private final static String PACKET_TYPE_BATTERY_REQUEST = "kdeconnect.battery.request";

    // keep these fields in sync with kdeconnect-kded:BatteryPlugin.h:ThresholdBatteryEvent
    private static final int THRESHOLD_EVENT_NONE = 0;
    private static final int THRESHOLD_EVENT_BATTERY_LOW = 1;

    private final NetworkPacket batteryInfo = new NetworkPacket(PACKET_TYPE_BATTERY);

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

            int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 1);
            int plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);

            int currentCharge = (level == -1) ? batteryInfo.getInt("currentCharge") : level * 100 / scale;
            boolean isCharging = (plugged == -1) ? batteryInfo.getBoolean("isCharging") : (0 != plugged);
            boolean lowBattery = Intent.ACTION_BATTERY_LOW.equals(batteryIntent.getAction());
            int thresholdEvent = lowBattery ? THRESHOLD_EVENT_BATTERY_LOW : THRESHOLD_EVENT_NONE;

            if (isCharging != batteryInfo.getBoolean("isCharging")
                    || currentCharge != batteryInfo.getInt("currentCharge")
                    || thresholdEvent != batteryInfo.getInt("thresholdEvent")
                    ) {

                batteryInfo.set("currentCharge", currentCharge);
                batteryInfo.set("isCharging", isCharging);
                batteryInfo.set("thresholdEvent", thresholdEvent);
                device.sendPacket(batteryInfo);

            }
        }
    };

    @Override
    public boolean onCreate() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        intentFilter.addAction(Intent.ACTION_BATTERY_LOW);
        Intent currentState = context.registerReceiver(receiver, intentFilter);
        receiver.onReceive(context, currentState);
        return true;
    }

    @Override
    public void onDestroy() {
        //It's okay to call this only once, even though we registered it for two filters
        context.unregisterReceiver(receiver);
    }

    @Override
    public boolean onPacketReceived(NetworkPacket np) {

        if (np.getBoolean("request")) {
            device.sendPacket(batteryInfo);
        }

        return true;
    }

    @Override
    public String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_BATTERY_REQUEST};
    }

    @Override
    public String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_BATTERY};
    }

}
