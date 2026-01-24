/*
 * SPDX-FileCopyrightText: 2025 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.connectivityreport

import android.Manifest
import org.json.JSONException
import org.json.JSONObject
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.plugins.connectivityreport.ConnectivityListener.Companion.getInstance
import org.kde.kdeconnect.plugins.connectivityreport.ConnectivityListener.SubscriptionState
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginFactory.LoadablePlugin
import org.kde.kdeconnect_tp.R

@LoadablePlugin
class ConnectivityReportPlugin : Plugin() {

    override val displayName: String
        get() = context.resources.getString(R.string.pref_plugin_connectivity_report)

    override val description: String
        get() = context.resources.getString(R.string.pref_plugin_connectivity_report_desc)

    /**
     * Packet used to report the current connectivity state
     *
     * The body should contain a key "signalStrengths" which has a dict that maps
     * a SubscriptionID (opaque value) to a dict with the connection info (See below)
     *
     * For example:
     * {
     *     "signalStrengths": {
     *         "6": {
     *             "networkType": "4G",
     *             "signalStrength": 3
     *         },
     *         "17": {
     *             "networkType": "HSPA",
     *             "signalStrength": 2
     *         },
     *         ...
     *     }
     * }
     */
    private val connectivityInfo = NetworkPacket(PACKET_TYPE_CONNECTIVITY_REPORT)

    var listener = object : ConnectivityListener.StateCallback {
        override fun statesChanged(states : Map<Int, SubscriptionState>) {
            if (states.isEmpty()) {
                return
            }
            val signalStrengths = JSONObject()
            states.forEach { (subID: Int, subscriptionState: SubscriptionState) ->
                try {
                    val subInfo = JSONObject()
                    subInfo.put("networkType", subscriptionState.networkType)
                    subInfo.put("signalStrength", subscriptionState.signalStrength)
                    signalStrengths.put(subID.toString(), subInfo)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
            connectivityInfo["signalStrengths"] = signalStrengths
            device.sendPacket(connectivityInfo)
        }
    }

    override fun onCreate(): Boolean {
        getInstance(context).listenStateChanges(listener)
        return true
    }

    override fun onDestroy() {
        getInstance(context).cancelActiveListener(listener)
    }

    override fun onPacketReceived(np: NetworkPacket): Boolean {
        return false
    }

    override val supportedPacketTypes: Array<String> = emptyArray()

    override val outgoingPacketTypes: Array<String> = arrayOf(PACKET_TYPE_CONNECTIVITY_REPORT)

    override val requiredPermissions: Array<String> = arrayOf(Manifest.permission.READ_PHONE_STATE)

    companion object {
        private const val PACKET_TYPE_CONNECTIVITY_REPORT = "kdeconnect.connectivity_report"
    }
}
