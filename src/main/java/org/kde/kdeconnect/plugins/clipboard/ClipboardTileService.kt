/*
 * SPDX-FileCopyrightText: 2021 Maxim Leshchenko <cnmaks90@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.clipboard

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.core.service.quicksettings.PendingIntentActivityWrapper
import androidx.core.service.quicksettings.TileServiceCompat
import org.kde.kdeconnect.KdeConnect

@RequiresApi(Build.VERSION_CODES.N)
class ClipboardTileService : TileService() {
    override fun onClick() {
        super.onClick()

        TileServiceCompat.startActivityAndCollapse(this, PendingIntentActivityWrapper(
            this, 0, Intent(this, ClipboardFloatingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                val ids = KdeConnect.getInstance().devices.values
                    .asSequence()
                    .filter { it.isReachable && it.isPaired }
                    .map { it.deviceId }
                    .toCollection(ArrayList())
                putExtra("connectedDeviceIds", ids)
            }, PendingIntent.FLAG_ONE_SHOT, true
        ))
    }
}
