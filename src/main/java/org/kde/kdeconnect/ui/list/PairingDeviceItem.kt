/*
 * SPDX-FileCopyrightText: 2025 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.ui.list

import android.view.LayoutInflater
import android.view.View
import org.kde.kdeconnect.Device
import org.kde.kdeconnect_tp.R

class PairingDeviceItem(
    device: Device,
    callback: ((d: Device) -> Unit)
) : DeviceItem(device, callback) {

    override fun toString(): String {
        return "PairingDeviceItem(device=$device)"
    }

    override fun inflateView(layoutInflater: LayoutInflater): View {
        return super.inflateView(layoutInflater).also {
            if (device.compareProtocolVersion() > 0) {
                binding.listItemEntrySummary.setText(R.string.protocol_version_newer)
                binding.listItemEntrySummary.visibility = View.VISIBLE
            }
        }
    }
}
