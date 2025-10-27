/*
 * SPDX-FileCopyrightText: 2014 Ahmed I. Khalil <ahmedibrahimkhali@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.Plugins.PresenterPlugin

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.annotation.DrawableRes
import org.kde.kdeconnect.DeviceType
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.Plugins.MousePadPlugin.KeyListenerView
import org.kde.kdeconnect.Plugins.Plugin
import org.kde.kdeconnect.Plugins.PluginFactory.LoadablePlugin
import org.kde.kdeconnect_tp.R

@LoadablePlugin
class PresenterPlugin : Plugin() {

    override val displayName: String
        get() = context.getString(R.string.pref_plugin_presenter)

    override val isCompatible: Boolean
        get() = device.deviceType != DeviceType.PHONE && super.isCompatible

    override val description: String
        get() = context.getString(R.string.pref_plugin_presenter_desc)

    @get:DrawableRes
    override val icon: Int = R.drawable.ic_presenter_24dp

    override fun hasSettings(): Boolean = false

    override fun displayAsButton(context: Context): Boolean = true

    override fun startMainActivity(parentActivity: Activity) {
        val intent = Intent(parentActivity, PresenterActivity::class.java)
        intent.putExtra("deviceId", device.deviceId)
        parentActivity.startActivity(intent)
    }

    override val supportedPacketTypes: Array<String> = emptyArray()

    override val outgoingPacketTypes: Array<String> = arrayOf(PACKET_TYPE_MOUSEPAD_REQUEST, PACKET_TYPE_PRESENTER)

    override val actionName: String
        get() = context.getString(R.string.pref_plugin_presenter)

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
