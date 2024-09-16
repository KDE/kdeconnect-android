/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.Plugins.FindRemoteDevicePlugin

import android.app.Activity
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.Plugins.FindMyPhonePlugin.FindMyPhonePlugin
import org.kde.kdeconnect.Plugins.Plugin
import org.kde.kdeconnect.Plugins.PluginFactory.LoadablePlugin
import org.kde.kdeconnect_tp.R

@LoadablePlugin
class FindRemoteDevicePlugin : Plugin() {
    override val displayName: String
        get() = context.resources.getString(R.string.pref_plugin_findremotedevice)

    override val description: String
        get() = context.resources.getString(R.string.pref_plugin_findremotedevice_desc)

    override fun onPacketReceived(np: NetworkPacket): Boolean = true

    override val actionName: String
        get() = context.getString(R.string.ring)

    override fun startMainActivity(activity: Activity) {
        if (isDeviceInitialized) {
            device.sendPacket(NetworkPacket(FindMyPhonePlugin.PACKET_TYPE_FINDMYPHONE_REQUEST))
        }
    }

    override fun displayInContextMenu(): Boolean = true

    override val supportedPacketTypes: Array<String>
        get() = arrayOf()

    override val outgoingPacketTypes: Array<String>
        get() = arrayOf(FindMyPhonePlugin.PACKET_TYPE_FINDMYPHONE_REQUEST)
}
