/*
 * SPDX-FileCopyrightText: 2017 Matthijs Tijink <matthijstijink@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins.mpris

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.extensions.getParcelableCompat

/**
 * Called when the mpris media notification's buttons are pressed
 */
class MprisMediaNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // First case: buttons send by other applications via the media session APIs. They don't target a specific device.
        if (Intent.ACTION_MEDIA_BUTTON == intent.action) {
            // Route these buttons to the media session, which will handle them
            val mediaSession = MprisMediaSession.getMediaSession() ?: return
            mediaSession.controller.dispatchMediaButtonEvent(intent.getParcelableCompat(Intent.EXTRA_KEY_EVENT))
        } else {
            // Second case: buttons on the notification, which we created ourselves
            // Get the correct device, the mpris plugin and the mpris player

            val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)
            val plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, MprisPlugin::class.java) ?: return
            val player = plugin.getPlayerStatus(intent.getStringExtra(EXTRA_MPRIS_PLAYER))
                ?: return

            when (intent.action) {
                ACTION_PLAY -> player.sendPlay()
                ACTION_PAUSE -> player.sendPause()
                ACTION_PREVIOUS -> player.sendPrevious()
                ACTION_NEXT -> player.sendNext()
                ACTION_CLOSE_NOTIFICATION ->                     //The user dismissed the notification: actually handle its removal correctly
                    MprisMediaSession.instance.closeMediaNotification()
                else -> {
                    Log.w(TAG, "Unknown action: ${intent.action}, ignore.")
                }
            }
        }
    }

    companion object {
        const val TAG: String = "M.M.N.Receiver"

        const val ACTION_PLAY: String = "ACTION_PLAY"
        const val ACTION_PAUSE: String = "ACTION_PAUSE"
        const val ACTION_PREVIOUS: String = "ACTION_PREVIOUS"
        const val ACTION_NEXT: String = "ACTION_NEXT"
        const val ACTION_CLOSE_NOTIFICATION: String = "ACTION_CLOSE_NOTIFICATION"

        const val EXTRA_DEVICE_ID: String = "deviceId"
        const val EXTRA_MPRIS_PLAYER: String = "player"
    }
}
