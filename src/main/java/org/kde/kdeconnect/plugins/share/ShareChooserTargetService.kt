/*
 * SPDX-FileCopyrightText: 2017 Nicolas Fella <nicolas.fella@gmx.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins.share

import android.content.ComponentName
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Bundle
import android.service.chooser.ChooserTarget
import android.service.chooser.ChooserTargetService
import android.util.Log
import org.kde.kdeconnect.KdeConnect.Companion.getInstance
import org.kde.kdeconnect_tp.R

class ShareChooserTargetService : ChooserTargetService() {
    override fun onGetChooserTargets(targetActivityName: ComponentName?, matchedFilter: IntentFilter?): MutableList<ChooserTarget> {
        Log.d("DirectShare", "invoked")
        return getInstance().devices.values
            .filter { d -> d.isReachable && d.isPaired }
            .onEach { d -> Log.d("DirectShare", d.name) }
            .map { d ->
                val targetName = d.name
                val targetIcon = Icon.createWithResource(this, R.drawable.icon)
                val targetRanking = 1f
                val targetComponentName = ComponentName(packageName, ShareActivity::class.java.canonicalName!!)
                val targetExtras = Bundle().apply {
                    putString(ShareActivity.EXTRA_DEVICE_ID, d.deviceId)
                }
                return@map ChooserTarget(targetName, targetIcon, targetRanking, targetComponentName, targetExtras)
            }
            .toMutableList()
    }
}
