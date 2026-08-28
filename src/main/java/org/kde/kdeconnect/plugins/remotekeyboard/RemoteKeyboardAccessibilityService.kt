/*
 * SPDX-FileCopyrightText: 2026 Johann Specht <sajeg.dev@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.remotekeyboard

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import org.kde.kdeconnect.KdeConnect.Companion.getInstance

class RemoteKeyboardAccessibilityService {

    fun onAccessibilityEvent(rootInActiveWindow: AccessibilityNodeInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        RemoteKeyboardInputService.window = rootInActiveWindow
        val devices = getInstance().devices
        for (device in devices.values) {
            if (device.isReachable && device.isPaired) {
                val plugin = device.getPlugin(RemoteKeyboardPlugin::class.java)
                plugin?.sendState()
            }
        }
    }

}

object RemoteKeyboardInputService {
    var window: AccessibilityNodeInfo? = null
}
