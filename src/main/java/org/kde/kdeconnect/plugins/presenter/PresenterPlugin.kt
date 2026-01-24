/*
 * SPDX-FileCopyrightText: 2014 Ahmed I. Khalil <ahmedibrahimkhali@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins.presenter

import android.app.Activity
import android.content.Intent
import android.view.KeyEvent
import org.kde.kdeconnect.DeviceType
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.plugins.mousepad.KeyListenerView
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginFactory.LoadablePlugin
import org.kde.kdeconnect.ui.PluginSettingsFragment
import org.kde.kdeconnect.ui.PluginSettingsFragment.Companion.newInstance
import org.kde.kdeconnect_tp.R


@LoadablePlugin
class PresenterPlugin : Plugin() {

    override val displayName: String
        get() = context.getString(R.string.pref_plugin_presenter)

    override val isCompatible: Boolean
        get() = device.deviceType != DeviceType.PHONE && super.isCompatible

    override val description: String
        get() = context.getString(R.string.pref_plugin_presenter_desc)

    override fun hasSettings(): Boolean = true

    override fun getSettingsFragment(activity: Activity): PluginSettingsFragment {
        return newInstance(pluginKey, R.xml.presenterplugin_preferences)
    }

    override fun getUiButtons(): List<PluginUiButton> = listOf(
        PluginUiButton(
            context.getString(R.string.pref_plugin_presenter),
            R.drawable.ic_presenter_24dp
        ) { parentActivity ->
            val intent = Intent(parentActivity, PresenterActivity::class.java)
            intent.putExtra("deviceId", device.deviceId)
            parentActivity.startActivity(intent)
        })

    override val supportedPacketTypes: Array<String> = emptyArray()

    override val outgoingPacketTypes: Array<String> = arrayOf(PACKET_TYPE_MOUSEPAD_REQUEST, PACKET_TYPE_PRESENTER)

    fun sendNext() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["specialKey"] = KeyListenerView.SpecialKeysMap.get(KeyEvent.KEYCODE_PAGE_DOWN)
        device.sendPacket(np)
    }

    fun sendPrevious() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["specialKey"] = KeyListenerView.SpecialKeysMap.get(KeyEvent.KEYCODE_PAGE_UP)
        device.sendPacket(np)
    }

    fun sendFullscreen() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["specialKey"] = KeyListenerView.SpecialKeysMap.get(KeyEvent.KEYCODE_F5)
        device.sendPacket(np)
    }

    fun sendEsc() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["specialKey"] = KeyListenerView.SpecialKeysMap.get(KeyEvent.KEYCODE_ESCAPE)
        device.sendPacket(np)
    }

    fun sendPointer(xDelta: Float, yDelta: Float) {
        val np = NetworkPacket(PACKET_TYPE_PRESENTER)
        np["dx"] = xDelta.toDouble()
        np["dy"] = yDelta.toDouble()
        device.sendPacket(np)
    }

    fun stopPointer() {
        val np = NetworkPacket(PACKET_TYPE_PRESENTER)
        np["stop"] = true
        device.sendPacket(np)
    }

    companion object {
        private const val PACKET_TYPE_PRESENTER = "kdeconnect.presenter"
        private const val PACKET_TYPE_MOUSEPAD_REQUEST = "kdeconnect.mousepad.request"
    }
}
