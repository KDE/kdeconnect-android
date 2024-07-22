/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.Plugins.MprisPlugin

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import org.kde.kdeconnect.Helpers.NotificationHelper
import org.kde.kdeconnect.Helpers.VideoUrlsHelper
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.Plugins.MprisPlugin.AlbumArtCache.deregisterPlugin
import org.kde.kdeconnect.Plugins.MprisPlugin.AlbumArtCache.getAlbumArt
import org.kde.kdeconnect.Plugins.MprisPlugin.AlbumArtCache.initializeDiskCache
import org.kde.kdeconnect.Plugins.MprisPlugin.AlbumArtCache.payloadToDiskCache
import org.kde.kdeconnect.Plugins.MprisPlugin.AlbumArtCache.registerPlugin
import org.kde.kdeconnect.Plugins.Plugin
import org.kde.kdeconnect.Plugins.PluginFactory.LoadablePlugin
import org.kde.kdeconnect.UserInterface.PluginSettingsFragment
import org.kde.kdeconnect_tp.R
import java.net.MalformedURLException
import java.util.concurrent.ConcurrentHashMap

@LoadablePlugin
class MprisPlugin : Plugin() {
    inner class MprisPlayer internal constructor() {
        var playerName: String = ""
            internal set
        var isPlaying: Boolean = false
            internal set
        var title: String = ""
            internal set
        var artist: String = ""
            internal set
        var album: String = ""
            internal set
        var albumArtUrl: String = ""
            internal set

        // @NonNull
        var url: String = ""
            internal set
        var loopStatus: String = ""
            internal set
        var isLoopStatusAllowed: Boolean = false
            internal set
        var shuffle: Boolean = false
            internal set
        var isShuffleAllowed: Boolean = false
            internal set
        var volume: Int = 50
            internal set
        var length: Long = -1
            internal set
        var lastPosition: Long = 0
            internal set
        var lastPositionTime: Long
            internal set
        var isPlayAllowed: Boolean = true
            internal set
        var isPauseAllowed: Boolean = true
            internal set
        var isGoNextAllowed: Boolean = true
            internal set
        var isGoPreviousAllowed: Boolean = true
            internal set
        var seekAllowed: Boolean = true
            internal set

        init {
            lastPositionTime = System.currentTimeMillis()
        }

        val isSpotify: Boolean
            get() = playerName.equals("spotify", ignoreCase = true)

        val isSeekAllowed: Boolean
            get() = seekAllowed && length >= 0 && position >= 0

        val hasAlbumArt: Boolean
            get() = albumArtUrl.isNotEmpty()

        /**
         * Returns the album art (if available). Note that this can return null even if hasAlbumArt() returns true.
         *
         * @return The album art, or null if not available
         */
        fun getAlbumArt(): Bitmap? {
            return getAlbumArt(albumArtUrl, this@MprisPlugin, playerName)
        }

        val isSetVolumeAllowed: Boolean
            get() = volume > -1

        val position: Long
            get() = if (isPlaying) {
                lastPosition + (System.currentTimeMillis() - lastPositionTime)
            } else {
                lastPosition
            }

        fun sendPlayPause() {
            if (isPauseAllowed || isPlayAllowed) {
                sendCommand(playerName, "action", "PlayPause")
            }
        }

        fun sendPlay() {
            if (isPlayAllowed) {
                sendCommand(playerName, "action", "Play")
            }
        }

        fun sendPause() {
            if (isPauseAllowed) {
                sendCommand(playerName, "action", "Pause")
            }
        }

        fun sendStop() {
            sendCommand(playerName, "action", "Stop")
        }

        fun sendPrevious() {
            if (isGoPreviousAllowed) {
                sendCommand(playerName, "action", "Previous")
            }
        }

        fun sendNext() {
            if (isGoNextAllowed) {
                sendCommand(playerName, "action", "Next")
            }
        }

        fun sendSetLoopStatus(loopStatus: String) {
            sendCommand(playerName, "setLoopStatus", loopStatus)
        }

        fun sendSetShuffle(shuffle: Boolean) {
            sendCommand(playerName, "setShuffle", shuffle)
        }

        fun sendSetVolume(volume: Int) {
            if (isSetVolumeAllowed) {
                sendCommand(playerName, "setVolume", volume)
            }
        }

        fun sendSetPosition(position: Int) {
            if (isSeekAllowed) {
                sendCommand(playerName, "SetPosition", position)

                lastPosition = position.toLong()
                lastPositionTime = System.currentTimeMillis()
            }
        }

        fun sendSeek(offset: Int) {
            if (isSeekAllowed) {
                sendCommand(playerName, "Seek", offset)
            }
        }
    }

    fun interface Callback {
        fun callback()
    }

    private val players = ConcurrentHashMap<String, MprisPlayer>()
    private var supportAlbumArtPayload = false
    private val playerStatusUpdated = ConcurrentHashMap<String, Callback>()
    private val playerListUpdated = ConcurrentHashMap<String, Callback>()
    override val displayName: String
        get() = context.resources.getString(R.string.pref_plugin_mpris)

    override val description: String
        get() = context.resources.getString(R.string.pref_plugin_mpris_desc)

    @DrawableRes
    override val icon: Int = R.drawable.mpris_plugin_action_24dp

    override fun hasSettings(): Boolean = true

    override fun getSettingsFragment(activity: Activity): PluginSettingsFragment {
        return PluginSettingsFragment.newInstance(pluginKey, R.xml.mprisplugin_preferences)
    }

    override fun onCreate(): Boolean {
        MprisMediaSession.instance.onCreate(context.applicationContext, this, device.deviceId)

        // Always request the player list so the data is up-to-date
        requestPlayerList()

        initializeDiskCache(context)
        registerPlugin(this)

        return true
    }

    override fun onDestroy() {
        players.clear()
        deregisterPlugin(this)
        MprisMediaSession.instance.onDestroy(this, device.deviceId)
    }

    private fun sendCommand(player: String, method: String, value: String) {
        val np = NetworkPacket(PACKET_TYPE_MPRIS_REQUEST).apply {
            this["player"] = player
            this[method] = value
        }
        device.sendPacket(np)
    }

    private fun sendCommand(player: String, method: String, value: Boolean) {
        val np = NetworkPacket(PACKET_TYPE_MPRIS_REQUEST).apply {
            this["player"] = player
            this[method] = value
        }
        device.sendPacket(np)
    }

    private fun sendCommand(player: String, method: String, value: Int) {
        val np = NetworkPacket(PACKET_TYPE_MPRIS_REQUEST).apply {
            this["player"] = player
            this[method] = value
        }
        device.sendPacket(np)
    }

    override fun onPacketReceived(np: NetworkPacket): Boolean {
        if (np.getBoolean("transferringAlbumArt", false)) {
            payloadToDiskCache(np.getString("albumArtUrl"), np.payload)
            return true
        }

        if (np.has("player")) {
            val playerStatus = players[np.getString("player")]
            if (playerStatus != null) {
                val wasPlaying = playerStatus.isPlaying
                //Note: title, artist and album will not be available for all desktop clients
                playerStatus.title = np.getString("title", playerStatus.title)
                playerStatus.artist = np.getString("artist", playerStatus.artist)
                playerStatus.album = np.getString("album", playerStatus.album)
                playerStatus.url = np.getString("url", playerStatus.url)
                if (np.has("loopStatus")) {
                    playerStatus.loopStatus = np.getString("loopStatus", playerStatus.loopStatus)
                    playerStatus.isLoopStatusAllowed = true
                }
                if (np.has("shuffle")) {
                    playerStatus.shuffle = np.getBoolean("shuffle", playerStatus.shuffle)
                    playerStatus.isShuffleAllowed = true
                }
                playerStatus.volume = np.getInt("volume", playerStatus.volume)
                playerStatus.length = np.getLong("length", playerStatus.length)
                if (np.has("pos")) {
                    playerStatus.lastPosition = np.getLong("pos", playerStatus.lastPosition)
                    playerStatus.lastPositionTime = System.currentTimeMillis()
                }
                playerStatus.isPlaying = np.getBoolean("isPlaying", playerStatus.isPlaying)
                playerStatus.isPlayAllowed = np.getBoolean("canPlay", playerStatus.isPlayAllowed)
                playerStatus.isPauseAllowed = np.getBoolean("canPause", playerStatus.isPauseAllowed)
                playerStatus.isGoNextAllowed = np.getBoolean("canGoNext", playerStatus.isGoNextAllowed)
                playerStatus.isGoPreviousAllowed = np.getBoolean("canGoPrevious", playerStatus.isGoPreviousAllowed)
                playerStatus.seekAllowed = np.getBoolean("canSeek", playerStatus.seekAllowed)
                val newAlbumArtUrlString = np.getString("albumArtUrl", playerStatus.albumArtUrl)
                val newAlbumArtUrl = Uri.parse(newAlbumArtUrlString)
                if (newAlbumArtUrl.scheme in AlbumArtCache.ALLOWED_SCHEMES) {
                    playerStatus.albumArtUrl = newAlbumArtUrl.toString()
                } else {
                    Log.w("MprisControl", "Invalid album art URL: $newAlbumArtUrlString")
                    playerStatus.albumArtUrl = ""
                }

                for (key in playerStatusUpdated.keys) {
                    try {
                        playerStatusUpdated[key]!!.callback()
                    } catch (e: Exception) {
                        Log.e("MprisControl", "Exception", e)
                        playerStatusUpdated.remove(key)
                    }
                }

                // Check to see if a stream has stopped playing and we should deliver a notification
                if (np.has("isPlaying") && !playerStatus.isPlaying && wasPlaying) {
                    showContinueWatchingNotification(playerStatus)
                }
            }
        }

        // Remember if the connected device support album art payloads
        supportAlbumArtPayload = np.getBoolean("supportAlbumArtPayload", supportAlbumArtPayload)

        val newPlayerList = np.getStringList("playerList")
        if (newPlayerList != null) {
            var equals = true
            newPlayerList.stream().filter { !players.containsKey(it) }.forEach { newPlayer ->
                equals = false
                val player = MprisPlayer().apply {
                    playerName = newPlayer
                }
                players[newPlayer] = player
                // Immediately ask for the data of this player
                requestPlayerStatus(newPlayer)
            }
            val iter = players.entries.iterator()
            iter.forEach {
                val oldPlayer = it.key
                val found = newPlayerList.stream().anyMatch { newPlayer -> newPlayer == oldPlayer }
                if (!found) {
                    // Player got removed
                    equals = false
                    iter.remove()
                    val playerStatus = it.value
                    if (playerStatus.isPlaying) {
                        showContinueWatchingNotification(playerStatus)
                    }
                }
            }
            if (!equals) {
                playerListUpdated.forEach { (key, callback) ->
                    runCatching {
                        callback.callback()
                    }.onFailure {
                        Log.e("MprisControl", "Exception", it)
                        playerListUpdated.remove(key)
                    }
                }
            }
        }

        return true
    }

    private fun showContinueWatchingNotification(playerStatus: MprisPlayer) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (prefs.getBoolean(context.getString(R.string.mpris_keepwatching_key), true) &&
            (playerStatus.url.startsWith("http://") || playerStatus.url.startsWith("https://"))
        ) {
            try {
                val url = VideoUrlsHelper.formatUriWithSeek(playerStatus.url, playerStatus.position).toString()
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                val pendingIntent = PendingIntent.getActivity(context, 0, browserIntent, PendingIntent.FLAG_IMMUTABLE)

                val notificationManager = ContextCompat.getSystemService(context, NotificationManager::class.java)
                val builder = NotificationCompat.Builder(context, NotificationHelper.Channels.CONTINUEWATCHING)
                    .setContentTitle(context.resources.getString(R.string.kde_connect))
                    .setSmallIcon(R.drawable.ic_play_white)
                    .setTimeoutAfter(3000)
                    .setContentIntent(pendingIntent)
                    .setContentText(context.resources.getString(R.string.mpris_keepwatching) + " " + playerStatus.title)
                NotificationHelper.notifyCompat(
                    notificationManager,
                    System.currentTimeMillis().toInt(),
                    builder.build()
                )
            } catch (e: MalformedURLException) {
                e.printStackTrace();
            }
        }
    }

    override val supportedPacketTypes: Array<String> = arrayOf(PACKET_TYPE_MPRIS)

    override val outgoingPacketTypes: Array<String> = arrayOf(PACKET_TYPE_MPRIS_REQUEST)

    fun setPlayerStatusUpdatedHandler(id: String, h: Callback) {
        playerStatusUpdated[id] = h
        h.callback()
    }

    fun removePlayerStatusUpdatedHandler(id: String) {
        playerStatusUpdated.remove(id)
    }

    fun setPlayerListUpdatedHandler(id: String, h: Callback) {
        playerListUpdated[id] = h

        h.callback()
    }

    fun removePlayerListUpdatedHandler(id: String) {
        playerListUpdated.remove(id)
    }

    val playerList: List<String>
        get() = players.keys.sorted()

    fun getPlayerStatus(player: String?): MprisPlayer? = if (player == null) {
        null
    } else players[player]

    fun getEmptyPlayer(): MprisPlayer = MprisPlayer()

    val playingPlayer: MprisPlayer?
        /**
         * Returns a playing mpris player if any exist
         *
         * @return null if no players are playing, a playing player otherwise
         */
        get() = players.values.stream().filter(MprisPlayer::isPlaying).findFirst().orElse(null)

    fun hasPlayer(player: MprisPlayer?): Boolean = player != null && players.containsValue(player)

    private fun requestPlayerList() {
        val np = NetworkPacket(PACKET_TYPE_MPRIS_REQUEST).apply {
            this["requestPlayerList"] = true
        }
        device.sendPacket(np)
    }

    private fun requestPlayerStatus(player: String) {
        val np = NetworkPacket(PACKET_TYPE_MPRIS_REQUEST).apply {
            this["player"] = player
            this["requestNowPlaying"] = true
            this["requestVolume"] = true
        }
        device.sendPacket(np)
    }

    override fun displayAsButton(context: Context): Boolean = true

    override fun startMainActivity(parentActivity: Activity) {
        val intent = Intent(parentActivity, MprisActivity::class.java).apply {
            putExtra(DEVICE_ID_KEY, device.deviceId)
        }
        parentActivity.startActivity(intent)
    }

    override val actionName: String
        get() = context.getString(R.string.open_mpris_controls)

    fun fetchedAlbumArt(url: String) {
        if (players.values.stream().anyMatch { player -> url == player.albumArtUrl }) {
            playerStatusUpdated.forEach { (key, callback) ->
                runCatching {
                    callback.callback()
                }.onFailure {
                    Log.e("MprisControl", "Exception", it)
                    playerStatusUpdated.remove(key)
                }
            }
        }
    }

    fun askTransferAlbumArt(url: String, playerName: String?): Boolean {
        // First check if the remote supports transferring album art
        if (!supportAlbumArtPayload) return false
        if (url.isEmpty()) return false

        val player = getPlayerStatus(playerName) ?: return false

        if (player.albumArtUrl == url) {
            val np = NetworkPacket(PACKET_TYPE_MPRIS_REQUEST)
            np["player"] = player.playerName
            np["albumArtUrl"] = url
            device.sendPacket(np)
            return true
        }
        return false
    }

    override val optionalPermissions: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf()
    }

    override val optionalPermissionExplanation: Int = R.string.mpris_notifications_explanation

    companion object {
        const val DEVICE_ID_KEY: String = "deviceId"
        private const val PACKET_TYPE_MPRIS = "kdeconnect.mpris"
        private const val PACKET_TYPE_MPRIS_REQUEST = "kdeconnect.mpris.request"
    }
}
