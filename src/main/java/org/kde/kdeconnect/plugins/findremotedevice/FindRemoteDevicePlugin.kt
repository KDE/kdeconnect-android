/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.findremotedevice

import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.plugins.findmyphone.FindMyPhonePlugin
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginFactory.LoadablePlugin
import org.kde.kdeconnect_tp.R

@LoadablePlugin
class FindRemoteDevicePlugin : Plugin() {
    override val displayName: String
        get() = context.resources.getString(R.string.pref_plugin_findremotedevice)

    override val description: String
        get() = context.resources.getString(R.string.pref_plugin_findremotedevice_desc)

    override fun onPacketReceived(np: NetworkPacket): Boolean = true

    override fun getUiMenuEntries(): List<PluginUiMenuEntry> = listOf(
        PluginUiMenuEntry(context.getString(R.string.ring)) { parentActivity ->
            device.sendPacket(NetworkPacket(FindMyPhonePlugin.PACKET_TYPE_FINDMYPHONE_REQUEST))
        }
    )

    override val supportedPacketTypes: Array<String> = emptyArray()

    override val outgoingPacketTypes: Array<String> = arrayOf(FindMyPhonePlugin.PACKET_TYPE_FINDMYPHONE_REQUEST)
}
