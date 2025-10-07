/*
 * SPDX-FileCopyrightText: 2025 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Plugins.ConnectivityReportPlugin

import android.os.Build
import android.telephony.CellInfo
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import kotlin.math.max
import kotlin.math.min

object ASUUtils {

    fun signalStrengthToLevel(signalStrength: SignalStrength?): Int {
        if (signalStrength == null) return 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return signalStrength.level
        } else {
            // Should work on all supported versions, uses copied functions from modern SDKs
            // Needs testing

            var gsmLevel = signalStrength.gsmSignalStrength
            if (gsmLevel >= 0 && gsmLevel <= 31) {
                // Convert getGsmSignalStrength range (0..31) to getLevel range (0..4)
                gsmLevel = gsmLevel * 4 / 31
            } else {
                gsmLevel = 0
            }

            val cdmaLevel = getCdmaLevel(signalStrength.cdmaDbm, signalStrength.cdmaEcio)
            val evdoLevel = getEvdoLevel(signalStrength.evdoDbm, signalStrength.evdoSnr)

            return max(gsmLevel, max(cdmaLevel, evdoLevel))
        }
    }

    /**
     * Get cdma as level 0..4
     */
    private fun getCdmaLevel(cdmaDbm: Int, cdmaEcio: Int): Int {
        val levelDbm: Int = if (cdmaDbm == CellInfo.UNAVAILABLE) 0
        else if (cdmaDbm >= -75) 4
        else if (cdmaDbm >= -85) 3
        else if (cdmaDbm >= -95) 2
        else if (cdmaDbm >= -100) 1
        else 0

        // Ec/Io are in dB*10
        val levelEcio: Int = if (cdmaEcio == CellInfo.UNAVAILABLE) 0
        else if (cdmaEcio >= -90) 4
        else if (cdmaEcio >= -110) 3
        else if (cdmaEcio >= -130) 2
        else if (cdmaEcio >= -150) 1
        else 0

        return min(levelDbm, levelEcio)
    }

    /**
     * Get Evdo as level 0..4
     */
    private fun getEvdoLevel(evdoDbm: Int, evdoSnr: Int): Int {
        val levelEvdoDbm: Int = if (evdoDbm == CellInfo.UNAVAILABLE) 0
        else if (evdoDbm >= -65) 4
        else if (evdoDbm >= -75) 3
        else if (evdoDbm >= -90) 2
        else if (evdoDbm >= -105) 1
        else 0

        val levelEvdoSnr: Int = if (evdoSnr == CellInfo.UNAVAILABLE) 0
        else if (evdoSnr >= 7) 4
        else if (evdoSnr >= 5) 3
        else if (evdoSnr >= 3) 2
        else if (evdoSnr >= 1) 1
        else 0

        return min(levelEvdoDbm, levelEvdoSnr)
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
