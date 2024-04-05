/*
 * SPDX-FileCopyrightText: 2017 Matthijs Tijink <matthijstijink@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.Plugins.MprisPlugin

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.preference.PreferenceManager
import android.service.notification.StatusBarNotification
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.util.Pair
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.Helpers.NotificationHelper
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.Plugins.MprisPlugin.MprisPlugin.MprisPlayer
import org.kde.kdeconnect.Plugins.NotificationsPlugin.NotificationReceiver
import org.kde.kdeconnect.Plugins.SystemVolumePlugin.SystemVolumePlugin
import org.kde.kdeconnect.Plugins.SystemVolumePlugin.SystemVolumeProvider
import org.kde.kdeconnect.Plugins.SystemVolumePlugin.SystemVolumeProvider.Companion.currentProvider
import org.kde.kdeconnect.Plugins.SystemVolumePlugin.SystemVolumeProvider.Companion.fromPlugin
import org.kde.kdeconnect.Plugins.SystemVolumePlugin.SystemVolumeProvider.ProviderStateListener
import org.kde.kdeconnect_tp.R

/**
 * Controls the mpris media control notification
 *
 *
 * There are two parts to this:
 * - The notification (with buttons etc.)
 * - The media session (via MediaSessionCompat; for lock screen control on
 * older Android version. And in the future for lock screen album covers)
 */
class MprisMediaSession : OnSharedPreferenceChangeListener, NotificationReceiver.NotificationListener,
    ProviderStateListener {
    private var spotifyRunning = false

    // Holds the device and player displayed in the notification
    private var notificationDevice: String? = null
    private var notificationPlayer: MprisPlayer? = null

    // Holds the device ids for which we can display a notification
    private val mprisDevices = HashSet<String>()

    private var context: Context? = null
    private var mediaSession: MediaSessionCompat? = null

    // Callback for control via the media session API
    private val mediaSessionCallback: MediaSessionCompat.Callback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            notificationPlayer!!.sendPlay()
        }

        override fun onPause() {
            notificationPlayer!!.sendPause()
        }

        override fun onSkipToNext() {
            notificationPlayer!!.sendNext()
        }

        override fun onSkipToPrevious() {
            notificationPlayer!!.sendPrevious()
        }

        override fun onStop() {
            if (notificationPlayer != null) {
                notificationPlayer!!.sendStop()
            }
        }

        override fun onSeekTo(pos: Long) {
            notificationPlayer!!.sendSetPosition(pos.toInt())
        }
    }

    /**
     * Called by the mpris plugin when it wants media control notifications for its device
     *
     *
     * Can be called multiple times, once for each device
     *
     * @param context The context
     * @param plugin  The mpris plugin
     * @param device  The device id
     */
    fun onCreate(context: Context?, plugin: MprisPlugin, device: String) {
        if (mprisDevices.isEmpty()) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            prefs.registerOnSharedPreferenceChangeListener(this)
        }
        this.context = context
        mprisDevices.add(device)

        plugin.setPlayerListUpdatedHandler(
            "media_notification"
        ) { this.updateMediaNotification() }
        plugin.setPlayerStatusUpdatedHandler(
            "media_notification"
        ) { this.updateMediaNotification() }

        NotificationReceiver.RunCommand(context) { service: NotificationReceiver ->
            service.addListener(this@MprisMediaSession)
            val serviceReady = service.isConnected
            if (serviceReady) {
                onListenerConnected(service)
            }
        }
    }

    /**
     * Called when a device disconnects/does not want notifications anymore
     *
     *
     * Can be called multiple times, once for each device
     *
     * @param plugin  The mpris plugin
     * @param device The device id
     */
    fun onDestroy(plugin: MprisPlugin, device: String) {
        mprisDevices.remove(device)
        plugin.removePlayerStatusUpdatedHandler("media_notification")
        plugin.removePlayerListUpdatedHandler("media_notification")
        updateMediaNotification()

        if (mprisDevices.isEmpty()) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            prefs.unregisterOnSharedPreferenceChangeListener(this)
        }
    }

    /**
     * Updates which device+player we're going to use in the notification
     *
     *
     * Prefers playing devices/mpris players, but tries to keep displaying the same
     * player and device, while possible.
     */
    private fun updateCurrentPlayer(): MprisPlayer? {
        val player = findPlayer()

        // Update the last-displayed device and player
        notificationDevice = if (player.first == null) null else player.first!!.deviceId
        notificationPlayer = player.second
        return notificationPlayer
    }

    private fun findPlayer(): Pair<Device?, MprisPlayer?> {
        // First try the previously displayed player (if still playing) or the previous displayed device (otherwise)
        if (notificationDevice != null && mprisDevices.contains(notificationDevice)) {
            val device = KdeConnect.getInstance().getDevice(notificationDevice)
            val player = if (notificationPlayer != null && notificationPlayer!!.isPlaying) {
                getPlayerFromDevice(device, notificationPlayer)
            } else {
                getPlayerFromDevice(device, null)
            }
            if (player != null) {
                return Pair(device, player)
            }
        }

        // Try a different player from another device
        for (otherDevice in KdeConnect.getInstance().devices.values) {
            val player = getPlayerFromDevice(otherDevice, null)
            if (player != null) {
                return Pair(otherDevice, player)
            }
        }

        // So no player is playing. Try the previously displayed player again
        //  This will succeed if it's paused:
        //  that allows pausing and subsequently resuming via the notification
        if (notificationDevice != null && mprisDevices.contains(notificationDevice)) {
            val device = KdeConnect.getInstance().getDevice(notificationDevice)

            val player = getPlayerFromDevice(device, notificationPlayer)
            if (player != null) {
                return Pair(device, player)
            }
        }
        return Pair(null, null)
    }

    private fun getPlayerFromDevice(device: Device?, preferredPlayer: MprisPlayer?): MprisPlayer? {
        if (device == null || !mprisDevices.contains(device.deviceId)) return null

        val plugin = device.getPlugin(MprisPlugin::class.java) ?: return null

        // First try the preferred player, if supplied
        if (plugin.hasPlayer(preferredPlayer) && shouldShowPlayer(preferredPlayer)) {
            return preferredPlayer
        }

        // Otherwise, accept any playing player
        val player = plugin.playingPlayer
        if (shouldShowPlayer(player)) {
            return player
        }

        return null
    }

    private fun shouldShowPlayer(player: MprisPlayer?): Boolean {
        return player != null && !(player.isSpotify && spotifyRunning)
    }

    private fun updateRemoteDeviceVolumeControl() {
        val plugin = KdeConnect.getInstance().getDevicePlugin(notificationDevice, SystemVolumePlugin::class.java)
            ?: return
        val systemVolumeProvider = fromPlugin(plugin)
        systemVolumeProvider.addStateListener(this)
    }

    /**
     * Update the media control notification
     */
    private fun updateMediaNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionResult = ContextCompat.checkSelfPermission(context!!, Manifest.permission.POST_NOTIFICATIONS)
            if (permissionResult != PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "No permission to post notifications, closed.")
                closeMediaNotification()
                return
            }
        }

        // If the user disabled the media notification, do not show it
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (!prefs.getBoolean(context!!.getString(R.string.mpris_notification_key), true)) {
            closeMediaNotification()
            return
        }

        // Make sure our information is up-to-date
        val currentPlayer = updateCurrentPlayer()

        val device = KdeConnect.getInstance().getDevice(notificationDevice)
        if (device == null) {
            closeMediaNotification()
            return
        }

        // If the player disappeared (and no other playing one found), just remove the notification
        if (currentPlayer == null) {
            closeMediaNotification()
            return
        }

        updateRemoteDeviceVolumeControl()

        val metadata = MediaMetadataCompat.Builder()

        metadata.putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentPlayer.title)

        if (currentPlayer.artist.isNotEmpty()) {
            metadata.putString(MediaMetadataCompat.METADATA_KEY_AUTHOR, currentPlayer.artist)
            metadata.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentPlayer.artist)
        }
        if (currentPlayer.album.isNotEmpty()) {
            metadata.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, currentPlayer.album)
        }
        if (currentPlayer.length > 0) {
            metadata.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, currentPlayer.length)
        }

        val albumArt = currentPlayer.getAlbumArt()
        if (albumArt != null) {
            metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
        }

        val playbackState = PlaybackStateCompat.Builder()

        if (currentPlayer.isPlaying) {
            playbackState.setState(PlaybackStateCompat.STATE_PLAYING, currentPlayer.position, 1.0f)
        } else {
            playbackState.setState(PlaybackStateCompat.STATE_PAUSED, currentPlayer.position, 0.0f)
        }

        // Create all actions (previous/play/pause/next)
        val iPlay = Intent(context, MprisMediaNotificationReceiver::class.java).apply {
            setAction(MprisMediaNotificationReceiver.ACTION_PLAY)
            putExtra(MprisMediaNotificationReceiver.EXTRA_DEVICE_ID, notificationDevice)
            putExtra(MprisMediaNotificationReceiver.EXTRA_MPRIS_PLAYER, currentPlayer.playerName)
        }
        val piPlay = PendingIntent.getBroadcast(
            context,
            0,
            iPlay,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val aPlay = NotificationCompat.Action.Builder(
            R.drawable.ic_play_white, context!!.getString(R.string.mpris_play), piPlay
        )

        val iPause = Intent(context, MprisMediaNotificationReceiver::class.java).apply {
            setAction(MprisMediaNotificationReceiver.ACTION_PAUSE)
            putExtra(MprisMediaNotificationReceiver.EXTRA_DEVICE_ID, notificationDevice)
            putExtra(MprisMediaNotificationReceiver.EXTRA_MPRIS_PLAYER, currentPlayer.playerName)
        }
        val piPause = PendingIntent.getBroadcast(
            context,
            0,
            iPause,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val aPause = NotificationCompat.Action.Builder(
            R.drawable.ic_pause_white, context!!.getString(R.string.mpris_pause), piPause
        )

        val iPrevious = Intent(context, MprisMediaNotificationReceiver::class.java).apply {
            setAction(MprisMediaNotificationReceiver.ACTION_PREVIOUS)
            putExtra(MprisMediaNotificationReceiver.EXTRA_DEVICE_ID, notificationDevice)
            putExtra(MprisMediaNotificationReceiver.EXTRA_MPRIS_PLAYER, currentPlayer.playerName)
        }
        val piPrevious = PendingIntent.getBroadcast(
            context,
            0,
            iPrevious,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val aPrevious = NotificationCompat.Action.Builder(
            R.drawable.ic_previous_white, context!!.getString(R.string.mpris_previous), piPrevious
        )

        val iNext = Intent(context, MprisMediaNotificationReceiver::class.java).apply {
            setAction(MprisMediaNotificationReceiver.ACTION_NEXT)
            putExtra(MprisMediaNotificationReceiver.EXTRA_DEVICE_ID, notificationDevice)
            putExtra(MprisMediaNotificationReceiver.EXTRA_MPRIS_PLAYER, currentPlayer.playerName)
        }
        val piNext = PendingIntent.getBroadcast(
            context,
            0,
            iNext,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val aNext = NotificationCompat.Action.Builder(
            R.drawable.ic_next_white, context!!.getString(R.string.mpris_next), piNext
        )

        val iOpenActivity = Intent(context, MprisActivity::class.java).apply {
            putExtra("deviceId", notificationDevice)
            putExtra("player", currentPlayer.playerName)
        }

        val piOpenActivity = TaskStackBuilder.create(context!!)
            .addNextIntentWithParentStack(iOpenActivity)
            .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)

        val notification = NotificationCompat.Builder(context!!, NotificationHelper.Channels.MEDIA_CONTROL)

        notification
            .setAutoCancel(false)
            .setContentIntent(piOpenActivity)
            .setSmallIcon(R.drawable.ic_play_white)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSubText(device.name)

        notification.setContentTitle(currentPlayer.title)

        // Only set the notification body text if we have an author and/or album
        if (currentPlayer.artist.isNotEmpty() && currentPlayer.album.isNotEmpty()) {
            notification.setContentText(currentPlayer.artist + " - " + currentPlayer.album + " (" + currentPlayer.playerName + ")")
        } else if (currentPlayer.artist.isNotEmpty()) {
            notification.setContentText(currentPlayer.artist + " (" + currentPlayer.playerName + ")")
        } else if (currentPlayer.album.isNotEmpty()) {
            notification.setContentText(currentPlayer.album + " (" + currentPlayer.playerName + ")")
        } else {
            notification.setContentText(currentPlayer.playerName)
        }

        if (albumArt != null) {
            notification.setLargeIcon(albumArt)
        }

        if (!currentPlayer.isPlaying) {
            val iCloseNotification = Intent(context, MprisMediaNotificationReceiver::class.java)
            iCloseNotification.setAction(MprisMediaNotificationReceiver.ACTION_CLOSE_NOTIFICATION)
            iCloseNotification.putExtra(MprisMediaNotificationReceiver.EXTRA_DEVICE_ID, notificationDevice)
            iCloseNotification.putExtra(MprisMediaNotificationReceiver.EXTRA_MPRIS_PLAYER, currentPlayer.playerName)
            val piCloseNotification = PendingIntent.getBroadcast(
                context,
                0,
                iCloseNotification,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            notification.setDeleteIntent(piCloseNotification)
        }

        // Add media control actions
        var numActions = 0
        var playbackActions: Long = 0
        if (currentPlayer.isGoPreviousAllowed) {
            notification.addAction(aPrevious.build())
            playbackActions = playbackActions or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            ++numActions
        }
        if (currentPlayer.isPlaying && currentPlayer.isPauseAllowed) {
            notification.addAction(aPause.build())
            playbackActions = playbackActions or PlaybackStateCompat.ACTION_PAUSE
            ++numActions
        }
        if (!currentPlayer.isPlaying && currentPlayer.isPlayAllowed) {
            notification.addAction(aPlay.build())
            playbackActions = playbackActions or PlaybackStateCompat.ACTION_PLAY
            ++numActions
        }
        if (currentPlayer.isGoNextAllowed) {
            notification.addAction(aNext.build())
            playbackActions = playbackActions or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            ++numActions
        }
        // Documentation says that this was added in Lollipop (21) but it seems to cause crashes on < Pie (28)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (currentPlayer.isSeekAllowed) {
                playbackActions = playbackActions or PlaybackStateCompat.ACTION_SEEK_TO
            }
        }
        playbackState.setActions(playbackActions)

        // Only allow deletion if no music is currentPlayer
        notification.setOngoing(currentPlayer.isPlaying)

        // Use the MediaStyle notification, so it feels like other media players. That also allows adding actions
        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
        if (numActions == 1) {
            mediaStyle.setShowActionsInCompactView(0)
        } else if (numActions == 2) {
            mediaStyle.setShowActionsInCompactView(0, 1)
        } else if (numActions >= 3) {
            mediaStyle.setShowActionsInCompactView(0, 1, 2)
        }
        notification.setGroup("MprisMediaSession")

        // Display the notification
        synchronized(instance) {
            if (mediaSession == null) {
                mediaSession = MediaSessionCompat(context!!, MPRIS_MEDIA_SESSION_TAG)
                mediaSession!!.setCallback(mediaSessionCallback, Handler(context!!.mainLooper))
            }
            mediaSession!!.setMetadata(metadata.build())
            mediaSession!!.setPlaybackState(playbackState.build())
            mediaStyle.setMediaSession(mediaSession!!.sessionToken)
            notification.setStyle(mediaStyle)
            mediaSession!!.isActive = true
            val nm = ContextCompat.getSystemService(context!!, NotificationManager::class.java)
            nm!!.notify(MPRIS_MEDIA_NOTIFICATION_ID, notification.build())
        }
    }

    fun closeMediaNotification() {
        // Remove the notification
        val nm = ContextCompat.getSystemService(context!!, NotificationManager::class.java)
        nm!!.cancel(MPRIS_MEDIA_NOTIFICATION_ID)

        // Clear the current player and media session
        notificationPlayer = null
        synchronized(instance) {
            if (mediaSession != null) {
                mediaSession!!.setPlaybackState(PlaybackStateCompat.Builder().build())
                mediaSession!!.setMetadata(MediaMetadataCompat.Builder().build())
                mediaSession!!.isActive = false
                mediaSession!!.release()
                mediaSession = null

                val currentProvider = currentProvider
                currentProvider?.release()
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        updateMediaNotification()
    }

    fun playerSelected(player: MprisPlayer?) {
        notificationPlayer = player
        updateMediaNotification()
    }

    override fun onNotificationPosted(n: StatusBarNotification) {
        if (n.isSpotify()) {
            spotifyRunning = true
            updateMediaNotification()
        }
    }

    override fun onNotificationRemoved(n: StatusBarNotification) {
        if (n.isSpotify()) {
            spotifyRunning = false
            updateMediaNotification()
        }
    }

    override fun onListenerConnected(service: NotificationReceiver) {
        try {
            service.activeNotifications.find { n -> n.isSpotify() }?.let {
                spotifyRunning = true
                updateMediaNotification()
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Failed to get active notifications", e)
        }
    }

    override fun onProviderStateChanged(volumeProvider: SystemVolumeProvider, isActive: Boolean) {
        val mediaSession = mediaSession ?: return

        if (isActive) {
            mediaSession.setPlaybackToRemote(volumeProvider)
        } else {
            mediaSession.setPlaybackToLocal(AudioManager.STREAM_MUSIC)
        }
    }

    private fun StatusBarNotification?.isSpotify(): Boolean =
        this?.packageName == SPOTIFY_PACKAGE_NAME

    companion object {
        const val TAG = "MprisMediaSession"

        private const val MPRIS_MEDIA_NOTIFICATION_ID =
            0x91b70463.toInt() // echo MprisNotification | md5sum | head -c 8
        private const val MPRIS_MEDIA_SESSION_TAG = "org.kde.kdeconnect_tp.media_session"

        private const val SPOTIFY_PACKAGE_NAME = "com.spotify.music"

        val instance: MprisMediaSession by lazy { MprisMediaSession() }

        fun getMediaSession(): MediaSessionCompat? {
            return instance.mediaSession
        }
    }
}
