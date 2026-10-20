/*
 * SPDX-FileCopyrightText: 2026 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.connectivityreport

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
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
            val signalStrengths = np.getJsonObject("signalStrengths") ?: return emptyMap()
            val result = mutableMapOf<String, DeviceConnectivityInfo>()
            for ((key, signalStrength) in signalStrengths) {
                signalStrength as? JsonObject ?: continue
                val networkType = (signalStrength["networkType"] as? JsonPrimitive)?.contentOrNull ?: continue
                val signalStrength = (signalStrength["signalStrength"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: continue
                result[key] = DeviceConnectivityInfo(networkType, signalStrength)
            }
            return result
        }
    }
}
