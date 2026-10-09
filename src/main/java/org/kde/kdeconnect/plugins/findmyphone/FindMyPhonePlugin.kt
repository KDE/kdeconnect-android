/*
 * SPDX-FileCopyrightText: 2015 David Edmundson <david@davidedmundson.co.uk>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.findmyphone

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import org.apache.commons.lang3.ArrayUtils
import org.kde.kdeconnect.DeviceType
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.helpers.LifecycleHelper
import org.kde.kdeconnect.helpers.NotificationHelper
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginFactory.LoadablePlugin
import org.kde.kdeconnect.ui.PluginSettingsFragment
import org.kde.kdeconnect_tp.R
import java.io.IOException

@LoadablePlugin
class FindMyPhonePlugin : Plugin() {

    private var notificationManager: NotificationManager? = null
    private var notificationId: Int = 0
    private var audioManager: AudioManager? = null
    private var mediaPlayer: MediaPlayer? = null
    private var previousVolume: Int = -1
    private var powerManager: PowerManager? = null
    private var flashlightManager: FlashlightManager? = null

    override val displayName: String
        get() = when (DeviceHelper.deviceType) {
            DeviceType.TV -> context.getString(R.string.findmyphone_title_tv)
            DeviceType.TABLET -> context.getString(R.string.findmyphone_title_tablet)
            DeviceType.PHONE -> context.getString(R.string.findmyphone_title)
            else -> context.getString(R.string.findmyphone_title)
        }

    override val description: String
        get() = context.getString(R.string.findmyphone_description)

    override fun onCreate() {
        notificationManager = ContextCompat.getSystemService(context, NotificationManager::class.java)
        notificationId = System.currentTimeMillis().toInt()
        audioManager = ContextCompat.getSystemService(context, AudioManager::class.java)
        powerManager = ContextCompat.getSystemService(context, PowerManager::class.java)
        flashlightManager = FlashlightManager(context)

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val ringtoneString = prefs.getString(context.getString(R.string.findmyphone_preference_key_ringtone), "")
        val ringtone: Uri = if (ringtoneString.isNullOrEmpty()) {
            Settings.System.DEFAULT_RINGTONE_URI
        } else {
            ringtoneString.toUri()
        }

        mediaPlayer = MediaPlayer()
        mediaPlayer?.apply {
            setDataSource(context, ringtone)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
                .build()
            @Suppress("DEPRECATION")
            setWakeMode(context, PowerManager.SCREEN_DIM_WAKE_LOCK)
            setAudioAttributes(audioAttributes)
            isLooping = true
            prepare()
        }
    }

    override fun onDestroy() {
        if (mediaPlayer?.isPlaying == true) {
            stopPlaying()
        }
        audioManager = null
        mediaPlayer?.release()
        mediaPlayer = null
        flashlightManager = null
    }

    override fun onPacketReceived(np: NetworkPacket): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || LifecycleHelper.isInForeground) {
            val intent = Intent(context, FindMyPhoneActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(FindMyPhoneActivity.EXTRA_DEVICE_ID, device.deviceId)
            }
            context.startActivity(intent)
        } else {
            if (powerManager?.isInteractive == true) {
                startPlaying()
                startFlashing()
                showBroadcastNotification()
            } else {
                showActivityNotification()
            }
        }
        return true
    }

    private fun showBroadcastNotification() {
        val intent = Intent(context, FindMyPhoneReceiver::class.java).apply {
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            action = FindMyPhoneReceiver.ACTION_FOUND_IT
            putExtra(FindMyPhoneReceiver.EXTRA_DEVICE_ID, device.deviceId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        createNotification(pendingIntent)
    }

    private fun showActivityNotification() {
        val intent = Intent(context, FindMyPhoneActivity::class.java).apply {
            putExtra(FindMyPhoneActivity.EXTRA_DEVICE_ID, device.deviceId)
        }

        val pi = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        createNotification(pi)
    }

    private fun createNotification(pendingIntent: PendingIntent) {
        val notification = NotificationCompat.Builder(context, NotificationHelper.Channels.HIGHPRIORITY)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setFullScreenIntent(pendingIntent, true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentTitle(context.getString(R.string.findmyphone_found))
            .setGroup("BackgroundService")

        notificationManager?.notify(notificationId, notification.build())
    }

    fun startPlaying() {
        val player = mediaPlayer ?: return
        val audio = audioManager ?: return
        if (!player.isPlaying) {
            previousVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
            player.start()
        }
    }

    fun startFlashing() {
        if (isFlashlightEnabledInSettings() && isPermissionGranted(Manifest.permission.CAMERA)) {
            flashlightManager?.startFlashing()
        }
    }

    fun hideNotification() {
        notificationManager?.cancel(notificationId)
    }

    fun stopPlaying() {
        val audio = audioManager ?: return
        if (previousVolume != -1) {
            audio.setStreamVolume(AudioManager.STREAM_ALARM, previousVolume, 0)
        }
        mediaPlayer?.let { player ->
            player.stop()
            try {
                player.prepare()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun stopFlashing() {
        flashlightManager?.stopFlashing()
    }

    override val supportedPacketTypes: Array<String> = arrayOf(PACKET_TYPE_FINDMYPHONE_REQUEST)

    override val outgoingPacketTypes: Array<String> = emptyArray()

    override fun hasSettings(): Boolean = true

    override fun getSettingsFragment(activity: Activity): PluginSettingsFragment =
        FindMyPhoneSettingsFragment.newInstance(pluginKey, device.deviceId, R.xml.findmyphoneplugin_preferences)

    override val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }

    override val permissionExplanation: Int = R.string.findmyphone_notifications_explanation

    private fun isFlashlightEnabledInSettings(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getBoolean(context.getString(R.string.findmyphone_preference_key_flashlight), false)
    }

    companion object {
        const val PACKET_TYPE_FINDMYPHONE_REQUEST: String = "kdeconnect.findmyphone.request"
    }
}
