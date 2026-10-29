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

class ShareBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            SharePlugin.ACTION_CANCEL_SHARE -> cancelShare(intent)
            else -> Log.d("ShareBroadcastReceiver", "Unhandled Action received: ${intent.action}")
        }
    }

    private fun cancelShare(intent: Intent) {
        if (!intent.hasExtra(SharePlugin.CANCEL_SHARE_BACKGROUND_JOB_ID_EXTRA)) {
            Log.e("ShareBroadcastReceiver", "cancelShare() - job id missing. Ignoring this cancel intent")
            return
        }

        ShareJob.cancel(intent.getLongExtra(SharePlugin.CANCEL_SHARE_BACKGROUND_JOB_ID_EXTRA, -1))
    }
}
