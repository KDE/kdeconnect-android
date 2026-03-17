/*
 * SPDX-FileCopyrightText: 2021 SohnyBohny <sohny.bean@streber24.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.mousereceiver

import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.fragment.app.DialogFragment
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginFactory.LoadablePlugin
import org.kde.kdeconnect.plugins.remotekeyboard.RemoteKeyboardPlugin
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect.ui.StartActivityAlertDialogFragment
import org.kde.kdeconnect_tp.R
import kotlin.math.ceil
import kotlin.math.floor

@LoadablePlugin
@RequiresApi(api = Build.VERSION_CODES.N)
class MouseReceiverPlugin : Plugin() {
    override fun checkRequiredPermissions(): Boolean {
        return MouseReceiverService.instance != null
    }

    override val permissionExplanationDialog: DialogFragment
        get() = StartActivityAlertDialogFragment.Builder()
            .setTitle(R.string.mouse_receiver_plugin_description)
            .setMessage(R.string.mouse_receiver_no_permissions)
            .setPositiveButton(R.string.open_settings)
            .setNegativeButton(R.string.cancel)
            .setIntentAction(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .setStartForResult(true)
            .setRequestCode(MainActivity.RESULT_NEEDS_RELOAD)
            .create()

    override fun onPacketReceived(np: NetworkPacket): Boolean {
        if (np.type != PACKET_TYPE_MOUSEPAD_REQUEST) {
            Log.e("MouseReceiverPlugin", "Invalid packet type for MouseReceiverPlugin: ${np.type}")
            return false
        }

        if (RemoteKeyboardPlugin.getMousePadPacketType(np) != RemoteKeyboardPlugin.MousePadPacketType.Mouse) {
            return false // This packet will be handled by the remotekeyboard instead, silently ignore
        }

        val dx = np.getDouble("dx", 0.toDouble()).let { if (it < 0) floor(it) else ceil(it) }.toInt()
        val dy = np.getDouble("dy", 0.toDouble()).let { if (it < 0) floor(it) else ceil(it) }.toInt()
        val x = np.getInt("x", 0)
        val y = np.getInt("y", 0)

        val isSingleClick = np.getBoolean("singleclick", false)
        val isDoubleClick = np.getBoolean("doubleclick", false)
        val isMiddleClick = np.getBoolean("middleclick", false)
        val isForwardClick = np.getBoolean("forwardclick", false)
        val isBackClick = np.getBoolean("backclick", false)

        val isRightClick = np.getBoolean("rightclick", false)
        val isSingleHold = np.getBoolean("singlehold", false)
        val isSingleRelease = np.getBoolean("singlerelease", false)
        val isScroll = np.getBoolean("scroll", false)

        if (isSingleClick || isDoubleClick || isMiddleClick || isRightClick || isSingleHold || isSingleRelease || isScroll || isForwardClick || isBackClick) {
            // Perform click
            when {
                isSingleClick -> {
                    // Log.i("MouseReceiverPlugin", "singleClick")
                    return MouseReceiverService.click()
                }
                isDoubleClick -> { // left & right
                    // Log.i("MouseReceiverPlugin", "doubleClick")
                    return MouseReceiverService.recentButton()
                }
                isMiddleClick -> {
                    // Log.i("MouseReceiverPlugin", "middleClick")
                    return MouseReceiverService.homeButton()
                }
                isRightClick -> {
                    // TODO right-click menu emulation
                    return MouseReceiverService.backButton()
                }
                isForwardClick -> {
                    return MouseReceiverService.recentButton()
                }
                isBackClick -> {
                    return MouseReceiverService.backButton()
                }
                isSingleHold -> {
                    // For drag'n drop
                    // Log.i("MouseReceiverPlugin", "singleHold")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        return MouseReceiverService.longClickSwipe()
                    } else {
                        return MouseReceiverService.longClick()
                    }
                }
                isSingleRelease -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        return MouseReceiverService.instance.stopSwipe()
                    }
                }
                isScroll -> {
                    // Log.i("MouseReceiverPlugin", "scroll dx: $dx dy: $dy")
                    return MouseReceiverService.scroll(dx, dy) // dx is always 0
                }
            }
        } else {
            // Mouse Move
            if (dx != 0 || dy != 0) {
                // Log.i("MouseReceiverPlugin", "move Mouse dx: $dx dy: $dy")
                return MouseReceiverService.move(dx, dy)
            } else if (x != 0 || y != 0) {
                return MouseReceiverService.setPos(x, y)
            } else {
                // To hide the cursor once it crosses the barrier.
                MouseReceiverService.instance.hide(0)
            }
        }

        return super.onPacketReceived(np)
    }

    override val minSdk: Int
        get() = Build.VERSION_CODES.N

    override val displayName: String
        get() = context.getString(R.string.mouse_receiver_plugin_name)

    override val description: String
        get() = context.getString(R.string.mouse_receiver_plugin_description)

    override val supportedPacketTypes: Array<String> = arrayOf(PACKET_TYPE_MOUSEPAD_REQUEST)

    override val outgoingPacketTypes: Array<String> = emptyArray()

    companion object {
        private const val PACKET_TYPE_MOUSEPAD_REQUEST = "kdeconnect.mousepad.request"
    }
}
