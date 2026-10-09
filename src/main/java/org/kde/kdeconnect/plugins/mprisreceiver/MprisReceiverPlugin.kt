/*
 * SPDX-FileCopyrightText: 2018 Nicolas Fella <nicolas.fella@gmx.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.mprisreceiver

import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.MediaSessionManager.OnActiveSessionsChangedListener
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.getSystemService
import androidx.fragment.app.DialogFragment
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.helpers.AppsHelper.appNameLookup
import org.kde.kdeconnect.helpers.ThreadHelper
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginFactory.LoadablePlugin
import org.kde.kdeconnect.plugins.notifications.NotificationReceiver
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect.ui.StartActivityAlertDialogFragment
import org.kde.kdeconnect_tp.R
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.MutableList

@LoadablePlugin
class MprisReceiverPlugin : Plugin() {
    private val players = ConcurrentHashMap<String, MprisReceiverPlayer>()

    private var mediaSessionChangeListener = MediaSessionChangeListener()

    override fun onCreate() {
        val manager = context.getSystemService<MediaSessionManager>()!!
        manager.addOnActiveSessionsChangedListener(
            mediaSessionChangeListener,
            ComponentName(context, NotificationReceiver::class.java),
            Handler(Looper.getMainLooper())
        )

        createPlayers(
            manager.getActiveSessions(
                ComponentName(context, NotificationReceiver::class.java)
            )
        )
        sendPlayerList()
    }

    override fun onDestroy() {
        val manager = context.getSystemService<MediaSessionManager>()!!
        manager.removeOnActiveSessionsChangedListener(mediaSessionChangeListener)
        releasePlayers()
    }

    private fun createPlayers(sessions: MutableList<MediaController>) {
        for (controller in sessions) {
            createPlayer(controller)
        }
    }

    override val displayName: String
        get() = context.getString(R.string.pref_plugin_mprisreceiver)

    override val description: String
        get() = context.getString(R.string.pref_plugin_mprisreceiver_desc)

    override fun onPacketReceived(np: NetworkPacket): Boolean {
        if (np.getBoolean("requestPlayerList")) {
            sendPlayerList()
            return true
        }

        val playerName = np.getStringOrNull("player")
            ?: return false
        val player = players[playerName]
            ?: return false

        val artUrl = np.getString("albumArtUrl", "")
        if (!artUrl.isEmpty()) {
            // run it on a different thread to avoid blocking
            ThreadHelper.execute { sendAlbumArt(player, artUrl) }
            return true
        }

        if (np.getBoolean("requestNowPlaying", false)) {
            sendMetadata(player)
            return true
        }

        if (np.has("SetPosition")) {
            val position = np.getLong("SetPosition", 0)
            player.position = position
        }

        if (np.has("setVolume")) {
            val volume = np.getInt("setVolume", 100)
            player.volume = volume
            // Setting volume doesn't seem to always trigger the callback
            sendMetadata(player)
        }

        if (np.has("action")) {
            val action = np.getString("action")
            when (action) {
                "Play" -> player.play()
                "Pause" -> player.pause()
                "PlayPause" -> player.playPause()
                "Next" -> player.next()
                "Previous" -> player.previous()
                "Stop" -> player.stop()
            }
        }

        return true
    }

    override val supportedPacketTypes = arrayOf(PACKET_TYPE_MPRIS_REQUEST)

    override val outgoingPacketTypes = arrayOf(PACKET_TYPE_MPRIS)

    private inner class MediaSessionChangeListener : OnActiveSessionsChangedListener {
        override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
            if (null == controllers) {
                return
            }

            releasePlayers()
            createPlayers(controllers)
            sendPlayerList()
        }
    }

    private fun createPlayer(controller: MediaController) {
        // Skip the media session we created ourselves as KDE Connect
        if (controller.getPackageName() == context.packageName) return

        val playerName = appNameLookup(context, controller.getPackageName())
        val player = MprisReceiverPlayer(controller, playerName, ::sendMetadata)
        // Release the player we replace, if any, so its callback doesn't stay registered
        players.put(player.name, player)?.release()
    }

    private fun releasePlayers() {
        // Remove one by one instead of clear(), so we never drop a player added concurrently without releasing it
        for (name in players.keys) {
            players.remove(name)?.release()
        }
    }

    private fun sendPlayerList() {
        val np = NetworkPacket(PACKET_TYPE_MPRIS)
        np["playerList"] = players.keys
        np["supportAlbumArtPayload"] = true
        device.sendPacket(np)
    }

    private fun sendAlbumArt(player: MprisReceiverPlayer, requestedUrl: String?) {
        // NOTE: It is possible that the player gets killed or changes track in the middle of this method.
        // We read the art snapshot only once, so the url and bitmap we send always match, even if the art became outdated in the meantime.
        val art = player.art
        if (art == null) {
            Log.w(TAG, "art not found!")
            return
        }
        if (requestedUrl != null && requestedUrl != art.url) {
            Log.w(TAG, "sendAlbumArt: Doesn't match current url")
            Log.d(TAG, "current:   ${art.url}")
            Log.d(TAG, "requested: $requestedUrl")
            return
        }
        val np = NetworkPacket(PACKET_TYPE_MPRIS)
        np.payload = NetworkPacket.Payload(art.toJpeg())
        np["player"] = player.name
        np["transferringAlbumArt"] = true
        np["albumArtUrl"] = art.url
        device.sendPacket(np)
    }

    private fun sendMetadata(player: MprisReceiverPlayer) {
        val np = NetworkPacket(PACKET_TYPE_MPRIS)
        np["player"] = player.name
        np["title"] = player.title
        np["artist"] = player.artist
        np["nowPlaying"] = player.title // GSConnect 50 (so, Ubuntu 22.04) needs this
        np["album"] = player.album
        np["isPlaying"] = player.isPlaying()
        np["pos"] = player.position
        np["length"] = player.length
        np["canPlay"] = player.canPlay()
        np["canPause"] = player.canPause()
        np["canGoPrevious"] = player.canGoPrevious()
        np["canGoNext"] = player.canGoNext()
        np["canSeek"] = player.canSeek()
        np["volume"] = player.volume
        np["albumArtUrl"] = player.art?.url ?: ""
        device.sendPacket(np)
    }

    override fun checkRequiredPermissions(): Boolean {
        return NotificationReceiver.hasReadNotificationsPermission(context)
    }

    override val permissionExplanationDialog: DialogFragment
        get() = StartActivityAlertDialogFragment.Builder()
            .setTitle(R.string.pref_plugin_mpris)
            .setMessage(R.string.no_permission_mprisreceiver)
            .setPositiveButton(R.string.open_settings)
            .setNegativeButton(R.string.cancel)
            .setIntentAction("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            .setStartForResult(true)
            .setRequestCode(MainActivity.RESULT_NEEDS_RELOAD)
            .create()

    companion object {
        private const val PACKET_TYPE_MPRIS = "kdeconnect.mpris"
        private const val PACKET_TYPE_MPRIS_REQUEST = "kdeconnect.mpris.request"

        private const val TAG = "MprisReceiver"
    }
}
