/*
 * SPDX-FileCopyrightText: 2018 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.share

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.kde.kdeconnect.KdeConnect

class ShareBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            SharePlugin.ACTION_CANCEL_SHARE -> cancelShare(context, intent)
            else -> Log.d("ShareBroadcastReceiver", "Unhandled Action received: ${intent.action}")
        }
    }

    private fun cancelShare(context: Context, intent: Intent) {
        if (!intent.hasExtra(SharePlugin.CANCEL_SHARE_BACKGROUND_JOB_ID_EXTRA) ||
            !intent.hasExtra(SharePlugin.CANCEL_SHARE_DEVICE_ID_EXTRA)
        ) {
            Log.e(
                "ShareBroadcastReceiver",
                "cancelShare() - not all expected extra's are present. Ignoring this cancel intent"
            )
            return
        }

        val jobId = intent.getLongExtra(SharePlugin.CANCEL_SHARE_BACKGROUND_JOB_ID_EXTRA, -1)
        val deviceId = intent.getStringExtra(SharePlugin.CANCEL_SHARE_DEVICE_ID_EXTRA) ?: return

        val plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, SharePlugin::class.java) ?: return
        plugin.cancelJob(jobId)
    }
}
