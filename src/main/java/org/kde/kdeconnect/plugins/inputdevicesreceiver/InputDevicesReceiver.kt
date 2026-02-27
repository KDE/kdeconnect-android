/*
 * SPDX-FileCopyrightText: 2026 Youssef Al-Bor3y <youssefelbor3y@protonmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.inputdevicesreceiver

import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginFactory.LoadablePlugin
import org.kde.kdeconnect_tp.R

@LoadablePlugin
class InputDevicesReceiverPlugin : Plugin() {
    override val displayName: String
        get() = context.resources.getString(R.string.pref_plugin_inputdevicesreceiver)

    override val description: String
        get() = context.resources.getString(R.string.pref_plugin_inputdevicesreceiver_desc)

    object Cursor {
        var enterEdge = 0.toDouble()

        // Trying to track cursor position here leads to unexpected behavior that makes our values don't match the actual position,
        // so as a workaround, we let `MouseReceiverPlugin` set them for us.
        var x = 0.toDouble()
        var y = 0.toDouble()
    }

    private val metrics = DisplayMetrics()
    private lateinit var displayManager: DisplayManager
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) { resolveDisplaySize() }
    }

    override fun onCreate(): Boolean {
        // We need to listen to display rotation changes, otherwise unintended behavior will happen
        // if the display rotates while we have the cursor.
        displayManager = context.getSystemService(DisplayManager::class.java)
        resolveDisplaySize()
        displayManager.registerDisplayListener(displayListener, null)
        return true
    }

    override fun onDestroy() {
        displayManager.unregisterDisplayListener(displayListener)
    }

    private fun resolveDisplaySize() {
        @Suppress("DEPRECATION")
        displayManager.getDisplay(Display.DEFAULT_DISPLAY)
            .getRealMetrics(metrics)
    }

    private fun release(dx: Double, dy: Double) {
        val np = NetworkPacket(PACKET_TYPE_SHAREINPUTDEVICES)

        np["releaseDeltax"] = dx
        np["releaseDeltay"] = dy

        device.sendPacket(np)
        Cursor.enterEdge = 0.toDouble()
    }

    override fun onPacketReceived(np: NetworkPacket): Boolean {
        val mouseReceiverPlugin = device.getPlugin("MouseReceiverPlugin")

        // If we do not have the permission or the MouseReceiverPlugin is disabled (either from the beginning or the user disabled it while the cursor was in our possession),
        // we must hand over control to the other end.
        if (mouseReceiverPlugin == null) {
            Cursor.x = np.getDouble("deltax", Cursor.x)
            Cursor.y = np.getDouble("deltay", Cursor.y)

            release(Cursor.x, Cursor.y)
            return false
        }

        if (np.type == PACKET_TYPE_SHAREINPUTDEVICES_REQUEST) {
            Cursor.enterEdge = np.getDouble("startEdge")
            val dx = np.getDouble("deltax")
            val dy = np.getDouble("deltay")

            val packet = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)

            when (Cursor.enterEdge) {
                TOP_EDGE -> {
                    packet["x"] = dx
                    packet["y"] = 1
                }
                LEFT_EDGE -> {
                    packet["x"] = 1
                    packet["y"] = dy
                }
                RIGHT_EDGE -> {
                    packet["x"] = metrics.widthPixels - 1
                    packet["y"] = dy
                }
                BOTTOM_EDGE -> {
                    packet["x"] = dx
                    packet["y"] = metrics.heightPixels - 1
                }
            }
            mouseReceiverPlugin.onPacketReceived(packet)
        } else if (np.type == PACKET_TYPE_MOUSEPAD_REQUEST && np.has("dx") && Cursor.enterEdge != 0.toDouble()) {
            when (Cursor.enterEdge) {
                TOP_EDGE -> if (Cursor.y == 0.toDouble()) release(Cursor.x, 0.toDouble())
                LEFT_EDGE -> if (Cursor.x == 0.toDouble()) release(0.toDouble(), Cursor.y)
                RIGHT_EDGE -> if (Cursor.x == metrics.widthPixels.toDouble()) release(0.toDouble(), Cursor.y)
                BOTTOM_EDGE -> if (Cursor.y == metrics.heightPixels.toDouble()) release(Cursor.x, 0.toDouble())
            }
        }
        return true
    }

    override val supportedPacketTypes: Array<String> = arrayOf(PACKET_TYPE_MOUSEPAD_REQUEST, PACKET_TYPE_SHAREINPUTDEVICES_REQUEST)
    override val outgoingPacketTypes: Array<String> = arrayOf(PACKET_TYPE_SHAREINPUTDEVICES)

    companion object {
        // Those are Qt edges but inverted, so TOP_EDGE is actually Qt::BottomEdge.
        private const val TOP_EDGE = 0x00008.toDouble()
        private const val LEFT_EDGE = 0x00004.toDouble()
        private const val RIGHT_EDGE = 0x00002.toDouble()
        private const val BOTTOM_EDGE = 0x00001.toDouble()

        private const val PACKET_TYPE_MOUSEPAD_REQUEST = "kdeconnect.mousepad.request"
        private const val PACKET_TYPE_SHAREINPUTDEVICES = "kdeconnect.shareinputdevices"
        private const val PACKET_TYPE_SHAREINPUTDEVICES_REQUEST = "kdeconnect.shareinputdevices.request"
    }
}
