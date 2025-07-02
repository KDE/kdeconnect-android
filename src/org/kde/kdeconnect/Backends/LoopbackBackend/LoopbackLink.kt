/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.Backends.LoopbackBackend

import android.content.Context
import androidx.annotation.WorkerThread
import org.kde.kdeconnect.Backends.BaseLink
import org.kde.kdeconnect.Backends.BaseLinkProvider
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceInfo
import org.kde.kdeconnect.Helpers.DeviceHelper.getDeviceInfo
import org.kde.kdeconnect.NetworkPacket
import java.net.InetAddress

class LoopbackLink : BaseLink {
    constructor(context: Context, linkProvider: BaseLinkProvider) : super(context, linkProvider)

    override fun getName(): String = "LoopbackLink"
    override fun getDeviceInfo(): DeviceInfo = getDeviceInfo(context)
    override fun getDeviceIp(): InetAddress {
        return InetAddress.getLoopbackAddress()
    }

    @WorkerThread
    override fun sendPacket(packet: NetworkPacket, callback: Device.SendPacketStatusCallback, sendPayloadFromSameThread: Boolean): Boolean {
        packetReceived(packet)
        if (packet.hasPayload()) {
            callback.onPayloadProgressChanged(0)
            packet.payload = packet.payload // this triggers logic in the setter
            callback.onPayloadProgressChanged(100)
        }
        callback.onSuccess()
        return true
    }
}
