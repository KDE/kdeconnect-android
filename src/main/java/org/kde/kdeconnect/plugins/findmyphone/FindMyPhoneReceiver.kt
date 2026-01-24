/*
 * SPDX-FileCopyrightText: 2015 David Edmundson <kde@davidedmundson.co.uk>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins.findmyphone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.kde.kdeconnect.KdeConnect

class FindMyPhoneReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_FOUND_IT: String = "org.kde.kdeconnect.plugins.findmyphone.foundIt"
        const val EXTRA_DEVICE_ID: String = "deviceId"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FOUND_IT -> foundIt(context, intent)
            else -> Log.d("ShareBroadcastReceiver", "Unhandled Action received: ${intent.action}")
        }
    }

    private fun foundIt(context: Context, intent: Intent) {
        if (!intent.hasExtra(EXTRA_DEVICE_ID)) {
            Log.e("FindMyPhoneReceiver", "foundIt() - deviceId extra is not present, ignoring")
            return
        }
        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)
        val plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, FindMyPhonePlugin::class.java) ?: return
        plugin.stopPlaying()
    }
}
