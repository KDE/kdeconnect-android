/*
 * SPDX-FileCopyrightText: 2025 Aleix Pol i Gonzalez <aleixpol@kde.org>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.Plugins.VirtualMonitorPlugin

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import org.json.JSONArray
import org.json.JSONObject
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.Plugins.Plugin
import org.kde.kdeconnect.Plugins.PluginFactory.LoadablePlugin
import org.kde.kdeconnect_tp.R

const val PACKET_TYPE_VIRTUALMONITOR: String = "kdeconnect.virtualmonitor"
const val PACKET_TYPE_VIRTUALMONITOR_REQUEST: String = "kdeconnect.virtualmonitor.request"

@LoadablePlugin
class VirtualMonitorPlugin : Plugin() {

    override val displayName: String
        get() = context.resources.getString(R.string.pref_plugin_virtualmonitor)

    override val description: String
        get() = context.resources.getString(R.string.pref_plugin_virtualmonitor_desc)

    private fun openUrlExternally(url: Uri): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, url)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            return true
        } else {
            return false
        }
    }

    override fun onPacketReceived(np: NetworkPacket): Boolean {
        if (np.type == PACKET_TYPE_VIRTUALMONITOR_REQUEST) {
            // At least a password is necessary, we have defaults for all other parameters
            if (!np.has("password")) {
                Log.e("KDE/VirtualMonitor", "Request invalid, missing password")
                return false
            }
            val addr = device.ipAddress()?.hostAddress
            if (addr == null) {
                Log.e("KDE/VirtualMonitor", "Request invalid, no address")
                    return false
            }
            val protocol = np.getString("protocol")
            val username = np.getString("username")
            val password = np.getString("password")
            val port = np.getInt("port", -1)

            val url = "$protocol://$username:$password@$addr:$port".toUri()

            Log.i("KDE/VirtualMonitor", "Received request, try connecting to $url")

            if (!openUrlExternally(url)) {
                Toast.makeText(context, R.string.virtualmonitor_rdp_client_not_installed, Toast.LENGTH_LONG).show()
                openUrlExternally("https://f-droid.org/en/packages/com.freerdp.afreerdp/".toUri())
                val failure = NetworkPacket(PACKET_TYPE_VIRTUALMONITOR).apply {
                    this["failed"] = 0
                }
                device.sendPacket(failure)
            }
        }
        return true
    }

    override fun onCreate() : Boolean
    {
        if (device.ipAddress() == null) {
            Log.e("KDE/VirtualMonitor", "No IP address for device, pass.")
            return false
        }

        val metrics = context.resources.displayMetrics
        val np = NetworkPacket(PACKET_TYPE_VIRTUALMONITOR).apply {
            this["resolutions"] = JSONArray().apply {
                put(JSONObject().apply {
                    put("resolution", "${metrics.widthPixels}x${metrics.heightPixels}")
                    put("scale", metrics.density)
                })
            }
            this["supports_rdp"] = true
            this["supports_virt_mon"] = false
        }

        device.sendPacket(np)
        return true
    }

    override val supportedPacketTypes: Array<String>
        get() = arrayOf(PACKET_TYPE_VIRTUALMONITOR, PACKET_TYPE_VIRTUALMONITOR_REQUEST)

    override val outgoingPacketTypes: Array<String>
        get() = arrayOf(PACKET_TYPE_VIRTUALMONITOR)
}
