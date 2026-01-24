/*
 * SPDX-FileCopyrightText: 2024 TPJ Schikhof <kde@schikhof.eu>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.helpers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import android.preference.PreferenceManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit

class TrustedNetworkHelper(private val context: Context) {

    var trustedNetworks: List<String>
        get() {
            val serializedNetworks = PreferenceManager.getDefaultSharedPreferences(context).getString(KEY_CUSTOM_TRUSTED_NETWORKS, "") ?: ""
            return serializedNetworks.split(NETWORK_SSID_DELIMITER).filter { it.isNotEmpty() }
        }
        set(value) {
            PreferenceManager.getDefaultSharedPreferences(context).edit {
                    putString(
                        KEY_CUSTOM_TRUSTED_NETWORKS,
                        value.joinToString(NETWORK_SSID_DELIMITER)
                    )
                }
        }

    var allNetworksAllowed: Boolean
        get() = !hasPermissions || PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY_CUSTOM_TRUST_ALL_NETWORKS, true)
        set(value) = PreferenceManager.getDefaultSharedPreferences(context).edit {
                putBoolean(KEY_CUSTOM_TRUST_ALL_NETWORKS, value)
            }

    val hasPermissions: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** @return The current SSID or null if it's not available for any reason */
    val currentSSID: String?
        get() {
            val wifiManager = ContextCompat.getSystemService(context.applicationContext, WifiManager::class.java) ?: return null
            val wifiInfo = wifiManager.connectionInfo
            if (wifiInfo.supplicantState != SupplicantState.COMPLETED) return null
            val ssid = wifiInfo.ssid
            return when {
                ssid.equals(NOT_AVAILABLE_SSID_RESULT, ignoreCase = true) -> {
                    Log.d("TrustedNetworkHelper", "Current SSID is unknown")
                    null
                }
                ssid.isBlank() -> null
                else -> ssid
            }
        }

    val isTrustedNetwork: Boolean
        get() = this.allNetworksAllowed || this.currentSSID in this.trustedNetworks

    companion object {
        private const val KEY_CUSTOM_TRUSTED_NETWORKS = "trusted_network_preference"
        private const val KEY_CUSTOM_TRUST_ALL_NETWORKS = "trust_all_network_preference"
        private const val NETWORK_SSID_DELIMITER = "\u0000"
        private const val NOT_AVAILABLE_SSID_RESULT = "<unknown ssid>"

        @JvmStatic
        fun isTrustedNetwork(context: Context): Boolean = TrustedNetworkHelper(context).isTrustedNetwork
    }
}
