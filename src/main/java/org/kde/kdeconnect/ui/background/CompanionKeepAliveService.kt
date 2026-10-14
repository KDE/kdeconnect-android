/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui.background

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.helpers.TrustedDevices
import org.kde.kdeconnect.plugins.clipboard.ClipboardSyncManager

/** Android binds this service when the associated Bluetooth companion is present. */
class CompanionKeepAliveService : CompanionDeviceService() {
    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        if (event.event == DevicePresenceEvent.EVENT_BT_CONNECTED || event.event == DevicePresenceEvent.EVENT_BLE_APPEARED) {
            recover(event.associationId)
        }
    }

    @Suppress("DEPRECATION")
    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        recover(associationInfo.id)
    }

    private fun recover(associationId: Int) {
        val id = CompanionSupport.deviceId(this, associationId) ?: return
        val device = KdeConnect.getInstance().getDevice(id) ?: return
        if (!device.isPaired || !TrustedDevices.isTrustedDevice(this, id)) return
        BackgroundService.ForceRefreshConnections(this)
        ClipboardSyncManager.refresh()
    }
}
