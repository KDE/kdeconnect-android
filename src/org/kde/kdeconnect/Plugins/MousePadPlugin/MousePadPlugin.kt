/*
 * SPDX-FileCopyrightText: 2014 Ahmed I. Khalil <ahmedibrahimkhali@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.Plugins.MousePadPlugin

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.annotation.DrawableRes
import org.kde.kdeconnect.DeviceType
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.Plugins.Plugin
import org.kde.kdeconnect.Plugins.PluginFactory.LoadablePlugin
import org.kde.kdeconnect.UserInterface.PluginSettingsFragment
import org.kde.kdeconnect.UserInterface.PluginSettingsFragment.Companion.newInstance
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

    override val actionName: String
        get() = context.getString(R.string.open_mousepad)

    override val description: String
        get() = context.getString(R.string.pref_plugin_mousepad_desc_nontv)

    @get:DrawableRes
    override val icon: Int = R.drawable.touchpad_plugin_action_24dp

    override fun displayAsButton(context: Context): Boolean = true

    override fun hasSettings(): Boolean = true

    override fun getSettingsFragment(activity: Activity): PluginSettingsFragment? {
        return if (device.deviceType == DeviceType.TV) {
            newInstance(pluginKey, R.xml.mousepadplugin_preferences, R.xml.mousepadplugin_preferences_tv)
        } else {
            newInstance(pluginKey, R.xml.mousepadplugin_preferences)
        }
    }

    override fun startMainActivity(parentActivity: Activity) {
        val intent = if (device.deviceType == DeviceType.TV) {
            Intent(parentActivity, BigscreenActivity::class.java)
        } else {
            Intent(parentActivity, MousePadActivity::class.java)
        }
        intent.putExtra("deviceId", device.deviceId)
        parentActivity.startActivity(intent)
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
        android.util.Log.d("MousePadPlugin", "sendText called with length: ${content.length}")
        android.util.Log.d("MousePadPlugin", "Contains newlines: ${content.contains('\n')}")
        android.util.Log.d("MousePadPlugin", "Text preview: ${content.take(100)}")
        
        // For multi-line text, send line by line with Enter key events
        if (content.contains('\n')) {
            val lines = content.split('\n')
            android.util.Log.d("MousePadPlugin", "Sending ${lines.size} lines")
            
            for (i in lines.indices) {
                val line = lines[i]
                
                // Send the line text (even if empty to preserve blank lines)
                if (line.isNotEmpty()) {
                    val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
                    np["key"] = line
                    sendPacket(np)
                }
                
                // After each line except the last one
                if (i < lines.size - 1) {
                    // Press Enter to go to next line
                    val enterNp = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
                    enterNp["specialKey"] = 12 // Enter key code
                    sendPacket(enterNp)
                    
                    // Small delay to let IDE process the Enter
                    Thread.sleep(10)
                    
                    // Select all content on the current line (Ctrl+Shift+End then Backspace)
                    // This clears any auto-completion or auto-indentation the IDE added
                    
                    // Shift+End: Select to end of line
                    val shiftEndNp = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
                    shiftEndNp["specialKey"] = 11 // End key
                    shiftEndNp["shift"] = true
                    sendPacket(shiftEndNp)
                    
                    Thread.sleep(5)
                    
                    // Backspace to delete selected content
                    val backspaceNp = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
                    backspaceNp["specialKey"] = 1 // Backspace key
                    sendPacket(backspaceNp)
                    
                    Thread.sleep(5)
                    
                    // Also clear the next line (down arrow, select line, delete)
                    val downNp = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
                    downNp["specialKey"] = 7 // Down arrow
                    sendPacket(downNp)
                    
                    Thread.sleep(5)
                    
                    // Select entire next line (Ctrl+Shift+End or just Shift+End)
                    val shiftEndNp2 = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
                    shiftEndNp2["specialKey"] = 11 // End key
                    shiftEndNp2["shift"] = true
                    sendPacket(shiftEndNp2)
                    
                    Thread.sleep(5)
                    
                    // Delete selected content
                    val deleteNp = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
                    deleteNp["specialKey"] = 13 // Delete key
                    sendPacket(deleteNp)
                    
                    Thread.sleep(5)
                    
                    // Go back up to our line
                    val upNp = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
                    upNp["specialKey"] = 5 // Up arrow
                    sendPacket(upNp)
                    
                    Thread.sleep(10)
                }
            }
        } else {
            // Single line text - send as before
            val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
            np["key"] = content
            sendPacket(np)
        }
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
