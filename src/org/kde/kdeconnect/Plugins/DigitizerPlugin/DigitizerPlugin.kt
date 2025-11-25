/*
 * SPDX-FileCopyrightText: 2025 Martin Sh <hemisputnik@proton.me>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.DigitizerPlugin

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.util.Log
import androidx.preference.PreferenceManager
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.Plugins.Plugin
import org.kde.kdeconnect.Plugins.PluginFactory
import org.kde.kdeconnect.UserInterface.PluginSettingsFragment
import org.kde.kdeconnect_tp.R

@PluginFactory.LoadablePlugin
class DigitizerPlugin : Plugin() {
    override val displayName: String
        get() = context.resources.getString(R.string.pref_plugin_digitizer)

    override val description: String
        get() = context.resources.getString(R.string.pref_plugin_digitizer_desc)

    override val icon: Int
        get() = R.drawable.ic_draw_24dp

    override fun onPacketReceived(np: NetworkPacket): Boolean {
        Log.e(TAG, "The drawing tablet plugin should not be able to receive any packets!")
        return false
    }

    override val actionName: String
        get() = context.getString(R.string.use_digitizer)

    override fun displayAsButton(context: Context): Boolean = shouldDisplayAsBigButton(context)

    override fun startMainActivity(parentActivity: Activity) {
        parentActivity.startActivity(Intent(parentActivity, DigitizerActivity::class.java).apply {
            putExtra("deviceId", device.deviceId)
        })
    }

    fun startSession(width: Int, height: Int, resolutionX: Int, resolutionY: Int) {
        val np = NetworkPacket(PACKET_TYPE_DIGITIZER_SESSION).apply {
            set("action", "start")
            set("width", width)
            set("height", height)
            set("resolutionX", resolutionX)
            set("resolutionY", resolutionY)
        }
        device.sendPacket(np)
    }

    fun endSession() {
        val np = NetworkPacket(PACKET_TYPE_DIGITIZER_SESSION).apply {
            set("action", "end")
        }
        device.sendPacket(np)
    }

    fun reportEvent(event: ToolEvent) {
        Log.d(TAG, "reportEvent: $event")

        val np = NetworkPacket(PACKET_TYPE_DIGITIZER).also { packet ->
            event.active?.let { packet["active"] = it }
            event.touching?.let { packet["touching"] = it }
            event.tool?.let { packet["tool"] = it.name }
            event.x?.let { packet["x"] = it }
            event.y?.let { packet["y"] = it }
            event.pressure?.let { packet["pressure"] = it }
        }
        device.sendPacket(np)
    }

    override fun hasSettings(): Boolean = true
    override fun getSettingsFragment(activity: Activity): PluginSettingsFragment =
        PluginSettingsFragment.newInstance(pluginKey, R.xml.digitizer_preferences)

    override val supportedPacketTypes: Array<String>
        get() = arrayOf()

    override val outgoingPacketTypes: Array<String>
        get() = arrayOf(
            PACKET_TYPE_DIGITIZER_SESSION,
            PACKET_TYPE_DIGITIZER,
        )

    companion object {
        private const val PACKET_TYPE_DIGITIZER_SESSION = "kdeconnect.digitizer.session"
        private const val PACKET_TYPE_DIGITIZER = "kdeconnect.digitizer"

        private const val TAG = "DigitizerPlugin"

        @JvmStatic
        fun shouldDisplayAsBigButton(context: Context): Boolean =
            context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK >= Configuration.SCREENLAYOUT_SIZE_LARGE
    }
}