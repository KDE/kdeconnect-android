/*
 * SPDX-FileCopyrightText: 2021 Philip Cohn-Cort <cliabhach@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.plugins.battery

import org.kde.kdeconnect.NetworkPacket

/**
 * Specialised data representation of the packets received by [BatteryPlugin].
 *
 * Constants for [thresholdEvent] may be found in [BatteryPlugin].
 *
 * @param currentCharge the amount of charge in the device's battery
 * @param isCharging whether the device is charging
 * @param thresholdEvent status classifier (used to indicate low battery, etc.)
 * @see BatteryPlugin.isLowBattery
 */
data class DeviceBatteryInfo(
    val currentCharge: Int,
    val isCharging: Boolean,
    val thresholdEvent: Int,
) {
    companion object {
        /**
         * For use with packets of type [BatteryPlugin.PACKET_TYPE_BATTERY].
         *
         * @throws IllegalArgumentException if the packet type is not [BatteryPlugin.PACKET_TYPE_BATTERY].
         */
        fun fromPacket(np: NetworkPacket): DeviceBatteryInfo {
            require(np.type == BatteryPlugin.PACKET_TYPE_BATTERY) {
                "Packet type must be PACKET_TYPE_BATTERY"
            }
            return DeviceBatteryInfo(
                np.getInt("currentCharge"),
                np.getBoolean("isCharging"),
                np.getInt("thresholdEvent", 0)
            )
        }
    }
}
