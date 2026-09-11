/*
 * SPDX-FileCopyrightText: 2017 Matthijs Tijink <matthijstijink@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.mpris

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
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.helpers.NotificationHelper
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.plugins.mpris.MprisPlugin.MprisPlayer
import org.kde.kdeconnect.plugins.notifications.NotificationReceiver
import org.kde.kdeconnect.plugins.systemvolume.SystemVolumePlugin
import org.kde.kdeconnect.plugins.systemvolume.SystemVolumeProvider
import org.kde.kdeconnect.plugins.systemvolume.SystemVolumeProvider.ProviderStateListener
import org.kde.kdeconnect_tp.R

/**
 * Controls the mpris media control notification
 *
 * There are two parts to this:
 * - The notification (with buttons etc.)
 * - The media session (via MediaSessionCompat; for lock screen control on
 * older Android version. And in the future for lock screen album covers)
 */
class MprisMediaSession : OnSharedPreferenceChangeListener, NotificationReceiver.NotificationListener,
    ProviderStateListener {
    private var spotifyRunning = false

    // Holds the player displayed in the notification
    private var currentShowingPlayer: MprisPlayer? = null

    // Keep the device preference when the notification and current player are cleared
    private var lastShowingDeviceId: String? = null

    // Holds the device ids for which we can display a notification
    private val mprisDevices = HashSet<String>()

    private lateinit var context: Context
    private var mediaSession: MediaSessionCompat? = null

    // Callback for control via the media session API
    private val mediaSessionCallback: MediaSessionCompat.Callback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            currentShowingPlayer?.sendPlay()
        }

        override fun onPause() {
            currentShowingPlayer?.sendPause()
        }

        override fun onSkipToNext() {
            currentShowingPlayer?.sendNext()
        }

        override fun onSkipToPrevious() {
            currentShowingPlayer?.sendPrevious()
        }

        override fun onSeekTo(pos: Long) {
            currentShowingPlayer?.sendSetPosition(pos.toInt())
        }
    }

    /**
     * Called by the mpris plugin when it wants media control notifications for its device
     *
     * Can be called multiple times, once for each device
     *
     * @param context The context
     * @param plugin  The mpris plugin
     * @param device  The device id
     */
    fun addDevice(context: Context, plugin: MprisPlugin, device: String) {
        this.context = context

        val wasEmpty = mprisDevices.isEmpty()
        mprisDevices.add(device)
        if (wasEmpty) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            prefs.registerOnSharedPreferenceChangeListener(this)

            NotificationReceiver.RunCommand(context) { service: NotificationReceiver ->
                service.addListener(this@MprisMediaSession)
                if (service.isConnected) {
                    onListenerConnected(service)
                }
            }
        }

        plugin.setPlayerListUpdatedHandler("media_notification", ::updateMediaNotification)
        plugin.setPlayerStatusUpdatedHandler("media_notification", ::updateMediaNotification)
    }

    /**
     * Called when a device disconnects/does not want notifications anymore
     *
     * Can be called multiple times, once for each device
     *
     * @param plugin  The mpris plugin
     * @param device The device id
     */
    fun removeDevice(plugin: MprisPlugin, device: String) {
        mprisDevices.remove(device)
        plugin.removePlayerStatusUpdatedHandler("media_notification")
        plugin.removePlayerListUpdatedHandler("media_notification")
        updateMediaNotification()

        val systemVolumeProvider = SystemVolumeProvider.getInstance()
        systemVolumeProvider.setPlugin(null)
        systemVolumeProvider.removeStateListener( this)

        if (mprisDevices.isEmpty()) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            prefs.unregisterOnSharedPreferenceChangeListener(this)

            NotificationReceiver.RunCommand(context) { service ->
                service.removeListener(this@MprisMediaSession)
            }
        }
    }

    /**
     * Finds which device+player we're going to use in the notification
     *
     * Prefers playing devices/mpris players, but tries to keep displaying the same
     * player and device, while possible.
     */
    private fun findPlayer(): MprisPlayer? {
        val currentPlayer = currentShowingPlayer
        val currentDevice = currentPlayer?.device ?: lastShowingDeviceId
            ?.takeIf(mprisDevices::contains)
            ?.let(KdeConnect.getInstance()::getDevice)

        // First try the previously displayed player (if still playing) or the previous displayed device (otherwise)
        if (currentDevice != null) {
            val playingPlayer = currentPlayer?.takeIf { it.isPlaying }
            val player = getPlayerFromDevice(currentDevice, playingPlayer)
            if (player != null) {
                return player
            }
        }

        // Try a different player from another device
        for (otherDevice in KdeConnect.getInstance().devices.values) {
            val player = getPlayerFromDevice(otherDevice, null)
            if (player != null) {
                return player
            }
        }

        // So no player is playing. Try the previously displayed player again
        //  This will succeed if it's paused:
        //  that allows pausing and subsequently resuming via the notification
        if (currentDevice != null) {
            val player = getPlayerFromDevice(currentDevice, currentPlayer)
            if (player != null) {
                return player
            }
        }

        return null
    }

    private fun getPlayerFromDevice(device: Device, preferredPlayer: MprisPlayer?): MprisPlayer? {
        if (!mprisDevices.contains(device.deviceId)) return null

        val plugin = device.getPlugin(MprisPlugin::class.java) ?: return null
        // First try the preferred player, if supplied & available, otherwise, accept any playing player
        val player = preferredPlayer?.takeIf(plugin::hasPlayer)
            ?: plugin.playingPlayer
            ?: return null
        return player.takeIf(::shouldShowPlayer)
    }

    private fun shouldShowPlayer(player: MprisPlayer): Boolean {
        return !(player.isSpotify && spotifyRunning)
    }

    private fun updateRemoteDeviceVolumeControl(device: Device) {
        val plugin = device.getPlugin(SystemVolumePlugin::class.java)
            ?: return
        val systemVolumeProvider = SystemVolumeProvider.getInstance()
        systemVolumeProvider.setPlugin(plugin)
        systemVolumeProvider.addStateListener( this)
    }

    /**
     * Update the media control notification
     */
    private fun updateMediaNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionResult = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            if (permissionResult != PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "No permission to post notifications, closed.")
                closeMediaNotification()
                return
            }
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (!prefs.getBoolean(context.getString(R.string.mpris_notification_key), true)) {
            // If the user disabled the media notification, do not show it
            closeMediaNotification()
            return
        }

        val player = findPlayer()
        if (player == null) {
            // If the player disappeared (and no other playing one found), just remove the notification
            closeMediaNotification()
            return
        }

        val device = player.device
        val deviceId = device.deviceId

        updateRemoteDeviceVolumeControl(device)

        val metadata = MediaMetadataCompat.Builder()

        metadata.putString(MediaMetadataCompat.METADATA_KEY_TITLE, player.title)

        if (player.artist.isNotEmpty()) {
            metadata.putString(MediaMetadataCompat.METADATA_KEY_AUTHOR, player.artist)
            metadata.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, player.artist)
        }
        if (player.album.isNotEmpty()) {
            metadata.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, player.album)
        }
        if (player.length > 0) {
            metadata.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, player.length)
        }

        val albumArt = player.getAlbumArt()
        if (albumArt != null) {
            metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
        }

        val playbackState = PlaybackStateCompat.Builder()

        if (player.isPlaying) {
            playbackState.setState(PlaybackStateCompat.STATE_PLAYING, player.position, 1.0f)
        } else {
            playbackState.setState(PlaybackStateCompat.STATE_PAUSED, player.position, 0.0f)
        }

        // Create all actions (previous/play/pause/next)
        val iPlay = Intent(context, MprisMediaNotificationReceiver::class.java).apply {
            setAction(MprisMediaNotificationReceiver.ACTION_PLAY)
            putExtra(MprisMediaNotificationReceiver.EXTRA_DEVICE_ID, deviceId)
            putExtra(MprisMediaNotificationReceiver.EXTRA_MPRIS_PLAYER, player.playerName)
        }
        val piPlay = PendingIntent.getBroadcast(
            context,
            0,
            iPlay,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val aPlay = NotificationCompat.Action.Builder(
            R.drawable.ic_play_white, context.getString(R.string.mpris_play), piPlay
        )

        val iPause = Intent(context, MprisMediaNotificationReceiver::class.java).apply {
            setAction(MprisMediaNotificationReceiver.ACTION_PAUSE)
            putExtra(MprisMediaNotificationReceiver.EXTRA_DEVICE_ID, deviceId)
            putExtra(MprisMediaNotificationReceiver.EXTRA_MPRIS_PLAYER, player.playerName)
        }
        val piPause = PendingIntent.getBroadcast(
            context,
            0,
            iPause,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val aPause = NotificationCompat.Action.Builder(
            R.drawable.ic_pause_white, context.getString(R.string.mpris_pause), piPause
        )

        val iPrevious = Intent(context, MprisMediaNotificationReceiver::class.java).apply {
            setAction(MprisMediaNotificationReceiver.ACTION_PREVIOUS)
            putExtra(MprisMediaNotificationReceiver.EXTRA_DEVICE_ID, deviceId)
            putExtra(MprisMediaNotificationReceiver.EXTRA_MPRIS_PLAYER, player.playerName)
        }
        val piPrevious = PendingIntent.getBroadcast(
            context,
            0,
            iPrevious,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val aPrevious = NotificationCompat.Action.Builder(
            R.drawable.ic_previous_white, context.getString(R.string.mpris_previous), piPrevious
        )

        val iNext = Intent(context, MprisMediaNotificationReceiver::class.java).apply {
            setAction(MprisMediaNotificationReceiver.ACTION_NEXT)
            putExtra(MprisMediaNotificationReceiver.EXTRA_DEVICE_ID, deviceId)
            putExtra(MprisMediaNotificationReceiver.EXTRA_MPRIS_PLAYER, player.playerName)
        }
        val piNext = PendingIntent.getBroadcast(
            context,
            0,
            iNext,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val aNext = NotificationCompat.Action.Builder(
            R.drawable.ic_next_white, context.getString(R.string.mpris_next), piNext
        )

        val iOpenActivity = Intent(context, MprisActivity::class.java).apply {
            putExtra("deviceId", deviceId)
            putExtra("player", player.playerName)
        }

        val piOpenActivity = TaskStackBuilder.create(context)
            .addNextIntentWithParentStack(iOpenActivity)
            .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, NotificationHelper.Channels.MEDIA_CONTROL)

        notification
            .setAutoCancel(false)
            .setContentIntent(piOpenActivity)
            .setSmallIcon(R.drawable.ic_play_white)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSubText(device.name)

        notification.setContentTitle(player.title)

        // Only set the notification body text if we have an author and/or album
        if (player.artist.isNotEmpty() && player.album.isNotEmpty()) {
            notification.setContentText(player.artist + " - " + player.album + " (" + player.playerName + ")")
        } else if (player.artist.isNotEmpty()) {
            notification.setContentText(player.artist + " (" + player.playerName + ")")
        } else if (player.album.isNotEmpty()) {
            notification.setContentText(player.album + " (" + player.playerName + ")")
        } else {
            notification.setContentText(player.playerName)
        }

        if (albumArt != null) {
            notification.setLargeIcon(albumArt)
        }

        if (!player.isPlaying) {
            val iCloseNotification = Intent(context, MprisMediaNotificationReceiver::class.java)
            iCloseNotification.setAction(MprisMediaNotificationReceiver.ACTION_CLOSE_NOTIFICATION)
            iCloseNotification.putExtra(MprisMediaNotificationReceiver.EXTRA_DEVICE_ID, deviceId)
            iCloseNotification.putExtra(MprisMediaNotificationReceiver.EXTRA_MPRIS_PLAYER, player.playerName)
            val piCloseNotification = PendingIntent.getBroadcast(
                context,
                0,
                iCloseNotification,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            notification.setDeleteIntent(piCloseNotification)
        }

        // Add media control actions
        var numActions = 0
        var playbackActions: Long = 0
        if (player.isGoPreviousAllowed) {
            notification.addAction(aPrevious.build())
            playbackActions = playbackActions or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            ++numActions
        }
        if (player.isPlaying && player.isPauseAllowed) {
            notification.addAction(aPause.build())
            playbackActions = playbackActions or PlaybackStateCompat.ACTION_PAUSE
            ++numActions
        }
        if (!player.isPlaying && player.isPlayAllowed) {
            notification.addAction(aPlay.build())
            playbackActions = playbackActions or PlaybackStateCompat.ACTION_PLAY
            ++numActions
        }
        if (player.isGoNextAllowed) {
            notification.addAction(aNext.build())
            playbackActions = playbackActions or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            ++numActions
        }
        // Documentation says that this was added in Lollipop (21) but it seems to cause crashes on < Pie (28)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (player.isSeekAllowed) {
                playbackActions = playbackActions or PlaybackStateCompat.ACTION_SEEK_TO
            }
        }
        playbackState.setActions(playbackActions)

        // Only allow deletion if no music is player
        notification.setOngoing(player.isPlaying)

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
            val mediaSession = mediaSession ?: MediaSessionCompat(context, MPRIS_MEDIA_SESSION_TAG).apply {
                setCallback(mediaSessionCallback, Handler(context.mainLooper))
            }
            mediaSession.setMetadata(metadata.build())
            mediaSession.setPlaybackState(playbackState.build())
            mediaStyle.setMediaSession(mediaSession.sessionToken)
            notification.setStyle(mediaStyle)
            mediaSession.isActive = true
            if (this.mediaSession == null) {
                this.mediaSession = mediaSession
            }
            ContextCompat.getSystemService(context, NotificationManager::class.java)
                ?.notify(MPRIS_MEDIA_NOTIFICATION_ID, notification.build())
        }

        currentShowingPlayer = player
        lastShowingDeviceId = deviceId
    }

    fun closeMediaNotification() {
        // Remove the notification
        ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?.cancel(MPRIS_MEDIA_NOTIFICATION_ID)

        // Clear the current player and media session
        currentShowingPlayer = null
        synchronized(instance) {
            mediaSession?.apply {
                setPlaybackState(PlaybackStateCompat.Builder().build())
                setMetadata(MediaMetadataCompat.Builder().build())
                isActive = false
                release()
            }
            mediaSession = null
            SystemVolumeProvider.currentProvider?.release()
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        updateMediaNotification()
    }

    fun playerSelected(player: MprisPlayer?) {
        currentShowingPlayer = player
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
            spotifyRunning = service.activeNotifications.any { n -> n.isSpotify() }
            updateMediaNotification()
        } catch (e: SecurityException) {
            Log.w(TAG, "Failed to get active notifications", e)
        }
    }

    override fun onProviderStateChanged(systemVolumeProvider: SystemVolumeProvider, isActive: Boolean) {
        val mediaSession = mediaSession ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val allowVolumeKeys = prefs.getBoolean(context.getString(R.string.pref_mpris_volume_control_key), true)

        if (isActive && allowVolumeKeys) {
            mediaSession.setPlaybackToRemote(systemVolumeProvider)
        } else {
            mediaSession.setPlaybackToLocal(AudioManager.STREAM_MUSIC)
        }
    }

    private fun StatusBarNotification.isSpotify(): Boolean =
        this.packageName == SPOTIFY_PACKAGE_NAME

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
