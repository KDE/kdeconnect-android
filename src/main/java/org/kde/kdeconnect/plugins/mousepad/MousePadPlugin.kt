/*
 * SPDX-FileCopyrightText: 2014 Ahmed I. Khalil <ahmedibrahimkhali@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.mousepad

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.view.KeyEvent
import androidx.preference.PreferenceManager
import org.kde.kdeconnect.DeviceType
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginFactory.LoadablePlugin
import org.kde.kdeconnect.ui.PluginSettingsFragment
import org.kde.kdeconnect.ui.PluginSettingsFragment.Companion.newInstance
import org.kde.kdeconnect_tp.R

@LoadablePlugin
class MousePadPlugin : Plugin() {
    var isKeyboardEnabled: Boolean = true
        private set

    override fun onPacketReceived(np: NetworkPacket): Boolean {
        this.isKeyboardEnabled = np.getBoolean("state", true)
        return true
    }

    override val displayName: String
        get() = context.getString(R.string.pref_plugin_mousepad)

    override fun getUiButtons(): List<PluginUiButton> {
        val mouseAndKeyboardInput = PluginUiButton(
            context.getString(R.string.open_mousepad),
            R.drawable.touchpad_plugin_action_24dp
        ) { parentActivity ->
            val intent = Intent(parentActivity, MousePadActivity::class.java)
            intent.putExtra("deviceId", device.deviceId)
            parentActivity.startActivity(intent)
        }
        return if (device.deviceType == DeviceType.TV) {
            val tvInput = PluginUiButton(
                context.getString(R.string.open_mousepad_tv),
                R.drawable.tv_remote_24px
            ) { parentActivity ->
                val intent = Intent(parentActivity, BigscreenActivity::class.java)
                intent.putExtra("deviceId", device.deviceId)
                parentActivity.startActivity(intent)
            }
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            if (prefs.getBoolean(context.getString(R.string.pref_bigscreen_hide_mouse_input), false)) {
                listOf(tvInput)
            } else {
                listOf(mouseAndKeyboardInput, tvInput)
            }
        } else {
            listOf(mouseAndKeyboardInput)
        }
    }


    override val description: String
        get() = context.getString(R.string.pref_plugin_mousepad_desc_nontv)

    override fun hasSettings(): Boolean = true

    override fun getSettingsFragment(activity: Activity): PluginSettingsFragment? {
        return if (device.deviceType == DeviceType.TV) {
            newInstance(pluginKey, R.xml.mousepadplugin_preferences, R.xml.mousepadplugin_preferences_tv)
        } else {
            newInstance(pluginKey, R.xml.mousepadplugin_preferences)
        }
    }

    fun sendMouseDelta(dx: Float, dy: Float) {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["dx"] = dx.toDouble()
        np["dy"] = dy.toDouble()
        sendPacket(np)
    }

    fun hasMicPermission(): Boolean {
        return isPermissionGranted(Manifest.permission.RECORD_AUDIO)
    }

    fun sendLeftClick() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["singleclick"] = true
        sendPacket(np)
    }

    fun sendDoubleClick() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["doubleclick"] = true
        sendPacket(np)
    }

    fun sendMiddleClick() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["middleclick"] = true
        sendPacket(np)
    }

    fun sendRightClick() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["rightclick"] = true
        sendPacket(np)
    }

    fun sendSingleHold() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["singlehold"] = true
        sendPacket(np)
    }

    fun sendSingleRelease() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["singlerelease"] = true
        sendPacket(np)
    }

    fun sendScroll(dx: Float, dy: Float) {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["scroll"] = true
        np["dx"] = dx.toDouble()
        np["dy"] = dy.toDouble()
        sendPacket(np)
    }

    fun sendLeft() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["specialKey"] = KeyListenerView.SpecialKeysMap.get(KeyEvent.KEYCODE_DPAD_LEFT)
        sendPacket(np)
    }

    fun sendRight() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["specialKey"] = KeyListenerView.SpecialKeysMap.get(KeyEvent.KEYCODE_DPAD_RIGHT)
        sendPacket(np)
    }

    fun sendUp() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["specialKey"] = KeyListenerView.SpecialKeysMap.get(KeyEvent.KEYCODE_DPAD_UP)
        sendPacket(np)
    }

    fun sendDown() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["specialKey"] = KeyListenerView.SpecialKeysMap.get(KeyEvent.KEYCODE_DPAD_DOWN)
        sendPacket(np)
    }

    fun sendSelect() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["specialKey"] = KeyListenerView.SpecialKeysMap.get(KeyEvent.KEYCODE_ENTER)
        sendPacket(np)
    }

    fun sendHome() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["alt"] = true
        np["specialKey"] = KeyListenerView.SpecialKeysMap.get(KeyEvent.KEYCODE_F4)
        device.sendPacket(np)
    }

    fun sendBack() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["specialKey"] = KeyListenerView.SpecialKeysMap.get(KeyEvent.KEYCODE_ESCAPE)
        device.sendPacket(np)
    }

    fun sendText(content: String) {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["key"] = content
        sendPacket(np)
    }

    fun sendPacket(np: NetworkPacket) {
        device.sendPacket(np)
    }

    override val supportedPacketTypes = arrayOf(PACKET_TYPE_MOUSEPAD_KEYBOARDSTATE)
    override val outgoingPacketTypes = arrayOf(PACKET_TYPE_MOUSEPAD_REQUEST)

    companion object {
        const val PACKET_TYPE_MOUSEPAD_REQUEST: String = "kdeconnect.mousepad.request"
        private const val PACKET_TYPE_MOUSEPAD_KEYBOARDSTATE = "kdeconnect.mousepad.keyboardstate"
    }
}
