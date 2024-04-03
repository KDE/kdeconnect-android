/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.Plugins.BatteryPlugin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.Plugins.Plugin
import org.kde.kdeconnect.Plugins.PluginFactory.LoadablePlugin
import org.kde.kdeconnect_tp.R

@LoadablePlugin
class BatteryPlugin : Plugin() {
    private val batteryInfo = NetworkPacket(PACKET_TYPE_BATTERY)

    /**
     * The latest battery information about the linked device. Will be null if the linked device
     * has not sent us any such information yet.
     *
     *
     * See [DeviceBatteryInfo] for info on which fields we expect to find.
     *
     *
     * @return the most recent packet received from the remote device. May be null
     */
    var remoteBatteryInfo: DeviceBatteryInfo? = null
        private set

    override val displayName: String
        get() = context.resources.getString(R.string.pref_plugin_battery)

    override val description: String
        get() = context.resources.getString(R.string.pref_plugin_battery_desc)

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        var wasLowBattery: Boolean = false // will trigger a low battery notification when the device is connected

        override fun onReceive(context: Context, batteryIntent: Intent) {
            val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 1)
            val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)

            val currentCharge = if (level == -1) batteryInfo.getInt("currentCharge") else level * 100 / scale
            val isCharging = if (plugged == -1) batteryInfo.getBoolean("isCharging") else 0 != plugged

            val thresholdEvent = when (batteryIntent.action) {
                Intent.ACTION_BATTERY_OKAY -> THRESHOLD_EVENT_NONE
                Intent.ACTION_BATTERY_LOW -> if (!wasLowBattery && !isCharging) {
                    THRESHOLD_EVENT_BATTERY_LOW
                } else {
                    THRESHOLD_EVENT_NONE
                }
                else -> THRESHOLD_EVENT_NONE
            }

            wasLowBattery = when (batteryIntent.action) {
                Intent.ACTION_BATTERY_OKAY -> false
                Intent.ACTION_BATTERY_LOW -> true
                else -> wasLowBattery
            }

            if (isCharging != batteryInfo.getBoolean("isCharging") || currentCharge != batteryInfo.getInt("currentCharge") || thresholdEvent != batteryInfo.getInt(
                    "thresholdEvent"
                )
            ) {
                batteryInfo["currentCharge"] = currentCharge
                batteryInfo["isCharging"] = isCharging
                batteryInfo["thresholdEvent"] = thresholdEvent
                device.sendPacket(batteryInfo)
            }
        }
    }

    override fun onCreate(): Boolean {
        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
        }
        val currentState = context.registerReceiver(receiver, intentFilter)
        receiver.onReceive(context, currentState)
        return true
    }

    override fun onDestroy() {
        // It's okay to call this only once, even though we registered it for two filters
        context.unregisterReceiver(receiver)
    }

    override fun onPacketReceived(np: NetworkPacket): Boolean {
        if (PACKET_TYPE_BATTERY != np.type) {
            return false
        }
        remoteBatteryInfo = DeviceBatteryInfo.fromPacket(np)
        device.onPluginsChanged()
        return true
    }

    override val supportedPacketTypes: Array<String>
        get() = arrayOf(PACKET_TYPE_BATTERY)

    override val outgoingPacketTypes: Array<String>
        get() = arrayOf(PACKET_TYPE_BATTERY)

    companion object {
        const val PACKET_TYPE_BATTERY = "kdeconnect.battery"

        // keep these fields in sync with kdeconnect-kded:BatteryPlugin.h:ThresholdBatteryEvent
        private const val THRESHOLD_EVENT_NONE = 0
        private const val THRESHOLD_EVENT_BATTERY_LOW = 1

        fun isLowBattery(info: DeviceBatteryInfo): Boolean {
            return info.thresholdEvent == THRESHOLD_EVENT_BATTERY_LOW
        }
    }
}
