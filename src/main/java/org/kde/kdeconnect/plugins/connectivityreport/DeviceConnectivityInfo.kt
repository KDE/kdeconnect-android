/*
 * SPDX-FileCopyrightText: 2026 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.connectivityreport

import org.json.JSONObject
import org.kde.kdeconnect.NetworkPacket

/**
 * Representation of remote subscription state received by [ConnectivityReportPlugin].
 *
 * @param networkType Cellular technology type (e.g., "5G", "4G", "HSPA", etc.)
 * @param signalStrength Signal level from 0 to 4 (or ASU level)
 */
data class DeviceConnectivityInfo(
    val networkType: String,
    val signalStrength: Int,
) {
    companion object {
        /**
         * Parses the "signalStrengths" map from a [ConnectivityReportPlugin.PACKET_TYPE_CONNECTIVITY_REPORT] packet.
         *
         * @return Map of subscriptionId string to [DeviceConnectivityInfo]
         */
        fun fromPacket(np: NetworkPacket): Map<String, DeviceConnectivityInfo> {
            require(np.type == ConnectivityReportPlugin.PACKET_TYPE_CONNECTIVITY_REPORT) {
                "Packet type must be PACKET_TYPE_CONNECTIVITY_REPORT"
            }
            val signalStrengths = np.getJSONObject("signalStrengths") ?: return emptyMap()
            val result = mutableMapOf<String, DeviceConnectivityInfo>()
            for (key in signalStrengths.keys()) {
                val subObj = signalStrengths.optJSONObject(key) ?: continue
                val networkType = subObj.optString("networkType", "")
                val signalStrength = subObj.optInt("signalStrength", 0)
                result[key] = DeviceConnectivityInfo(networkType, signalStrength)
            }
            return result
        }
    }
}
