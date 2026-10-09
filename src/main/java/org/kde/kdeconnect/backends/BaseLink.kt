/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.backends

import android.content.Context
import androidx.annotation.WorkerThread
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceInfo
import org.kde.kdeconnect.NetworkPacket
import java.io.IOException
import java.util.ArrayList

abstract class BaseLink(
    @JvmField protected val context: Context,
    open val linkProvider: BaseLinkProvider,
) {

    fun interface PacketReceiver {
        fun onPacketReceived(np: NetworkPacket)
    }

    private val receivers = ArrayList<PacketReceiver>()

    /* To be implemented by each link for pairing handlers */
    abstract val name: String

    abstract val deviceInfo: DeviceInfo

    val deviceId: String
        get() = deviceInfo.id

    fun addPacketReceiver(pr: PacketReceiver) {
        receivers.add(pr)
    }

    fun removePacketReceiver(pr: PacketReceiver) {
        receivers.remove(pr)
    }

    // Should be called from a background thread listening for packets
    fun packetReceived(np: NetworkPacket) {
        for (pr in receivers) {
            pr.onPacketReceived(np)
        }
    }

    open fun disconnect() {
        linkProvider.onConnectionLost(this)
    }

    // TO OVERRIDE, should be sync. If sendPayloadFromSameThread is false, it should only block to send the packet but start a separate thread to send the payload.
    @WorkerThread
    @Throws(IOException::class)
    abstract fun sendPacket(np: NetworkPacket, callback: Device.SendPacketStatusCallback, sendPayloadFromSameThread: Boolean): Boolean
}
