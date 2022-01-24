/*
 * SPDX-FileCopyrightText: 2021 Maxim Leshchenko <cnmaks90@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.ClibpoardPlugin

import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.Device

@RequiresApi(Build.VERSION_CODES.N)
class ClipboardTileService : TileService() {
    override fun onClick() {
        super.onClick()

        startActivityAndCollapse(Intent(this, ClipboardFloatingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            var ids : List<String> = emptyList()
            val service = BackgroundService.getInstance()
            if (service != null) {
                ids = service.devices.values
                    .filter { it.isReachable && it.isPaired }
                    .map { it.deviceId }
            }
            putExtra("connectedDeviceIds", ArrayList(ids))
        })
    }
}
