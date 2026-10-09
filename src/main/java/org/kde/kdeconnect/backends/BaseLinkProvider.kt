/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.backends

import android.net.Network
import androidx.annotation.WorkerThread
import org.kde.kdeconnect.DeviceInfo
import java.util.concurrent.CopyOnWriteArrayList

abstract class BaseLinkProvider {

    interface ConnectionReceiver {
        @WorkerThread
        fun onConnectionReceived(link: BaseLink)

        @WorkerThread
        fun onDeviceInfoUpdated(deviceInfo: DeviceInfo)

        @WorkerThread
        fun onConnectionLost(link: BaseLink)
    }

    private val connectionReceivers = CopyOnWriteArrayList<ConnectionReceiver>()

    fun addConnectionReceiver(cr: ConnectionReceiver) {
        connectionReceivers.add(cr)
    }

    fun removeConnectionReceiver(cr: ConnectionReceiver): Boolean {
        return connectionReceivers.remove(cr)
    }

    /**
     * To be called from the child classes when a link to a new device is established
     */
    @WorkerThread
    protected open fun onConnectionReceived(link: BaseLink) {
        // Log.i("KDE/LinkProvider", "onConnectionReceived")
        for (cr in connectionReceivers) {
            cr.onConnectionReceived(link)
        }
    }

    /**
     * To be called from the child classes when a link to an existing device is disconnected
     */
    @WorkerThread
    open fun onConnectionLost(link: BaseLink) {
        // Log.i("KDE/LinkProvider", "connectionLost")
        for (cr in connectionReceivers) {
            cr.onConnectionLost(link)
        }
    }

    /**
     * To be called from the child classes when we discover new DeviceInfo for an already linked device.
     */
    @WorkerThread
    protected open fun onDeviceInfoUpdated(deviceInfo: DeviceInfo) {
        for (cr in connectionReceivers) {
            cr.onDeviceInfoUpdated(deviceInfo)
        }
    }

    abstract fun onStart()
    abstract fun onStop()
    abstract fun onNetworkChange(network: Network?)
    abstract val name: String
    abstract val priority: Int
}
