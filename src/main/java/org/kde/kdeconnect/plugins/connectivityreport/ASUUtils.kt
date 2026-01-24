/*
 * SPDX-FileCopyrightText: 2025 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.plugins.connectivityreport

import android.telephony.SignalStrength
import android.telephony.TelephonyManager

object ASUUtils {

    fun signalStrengthToLevel(signalStrength: SignalStrength?): Int {
        if (signalStrength == null) return 0
        return signalStrength.level
    }

    fun networkTypeToString(networkType: Int): String {
        return when (networkType) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
            TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "CDMA"
            TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
            TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
            TelephonyManager.NETWORK_TYPE_GSM -> "GSM"
            TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSUPA -> "HSPA"
            TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
            TelephonyManager.NETWORK_TYPE_EHRPD, TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyManager.NETWORK_TYPE_1xRTT -> "CDMA2000"
            TelephonyManager.NETWORK_TYPE_IDEN -> "iDEN"
            TelephonyManager.NETWORK_TYPE_IWLAN, TelephonyManager.NETWORK_TYPE_UNKNOWN -> "Unknown"
            else -> "Unknown"
        }
    }
}
