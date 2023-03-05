/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.BatteryPlugin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

    public static boolean isLowBattery(@NonNull DeviceBatteryInfo info) {
        return info.getThresholdEvent() == THRESHOLD_EVENT_BATTERY_LOW;
    }

    private final NetworkPacket batteryInfo = new NetworkPacket(PACKET_TYPE_BATTERY);

    @Nullable
    private DeviceBatteryInfo remoteBatteryInfo;

    @Override
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_battery);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_battery_desc);
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {

        boolean wasLowBattery = false; // will trigger a low battery notification when the device is connected

        @Override
        public void onReceive(Context context, Intent batteryIntent) {

            int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 1);
            int plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);

            int currentCharge = (level == -1) ? batteryInfo.getInt("currentCharge") : level * 100 / scale;
            boolean isCharging = (plugged == -1) ? batteryInfo.getBoolean("isCharging") : (0 != plugged);

            int thresholdEvent = THRESHOLD_EVENT_NONE;
            if (Intent.ACTION_BATTERY_OKAY.equals(batteryIntent.getAction())) {
                wasLowBattery = false;
            } else if (Intent.ACTION_BATTERY_LOW.equals(batteryIntent.getAction())) {
                if (!wasLowBattery && !isCharging) {
                    thresholdEvent = THRESHOLD_EVENT_BATTERY_LOW;
                }
                wasLowBattery = true;
            }

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
        intentFilter.addAction(Intent.ACTION_BATTERY_OKAY);
        Intent currentState = context.registerReceiver(receiver, intentFilter);
        receiver.onReceive(context, currentState);

        // Request new battery info from the linked device
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_BATTERY_REQUEST);
        np.set("request", true);
        device.sendPacket(np);
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

        if (PACKET_TYPE_BATTERY.equals(np.getType())) {
            remoteBatteryInfo = new DeviceBatteryInfo(np);
            device.onPluginsChanged();
        }

        return true;
    }

    /**
     * The latest battery information about the linked device. Will be null if the linked device
     * has not sent us any such information yet.
     * <p>
     * See {@link DeviceBatteryInfo} for info on which fields we expect to find.
     * </p>
     *
     * @return the most recent packet received from the remote device. May be null
     */
    @Nullable
    public DeviceBatteryInfo getRemoteBatteryInfo() {
        return remoteBatteryInfo;
    }

    @Override
    public String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_BATTERY_REQUEST, PACKET_TYPE_BATTERY};
    }

    @Override
    public String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_BATTERY_REQUEST, PACKET_TYPE_BATTERY};
    }

}
