/*
 * SPDX-FileCopyrightText: 2015 David Edmundson <david@davidedmundson.co.uk>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Plugins.FindMyPhonePlugin;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.apache.commons.lang3.ArrayUtils;
import org.kde.kdeconnect.Helpers.DeviceHelper;
import org.kde.kdeconnect.Helpers.NotificationHelper;
import org.kde.kdeconnect.MyApplication;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect.UserInterface.PluginSettingsFragment;
import org.kde.kdeconnect_tp.R;

import java.io.IOException;

@PluginFactory.LoadablePlugin
public class FindMyPhonePlugin extends Plugin {
    public final static String PACKET_TYPE_FINDMYPHONE_REQUEST = "kdeconnect.findmyphone.request";

    private NotificationManager notificationManager;
    private int notificationId;
    private AudioManager audioManager;
    private MediaPlayer mediaPlayer;
    private int previousVolume = -1;
    private PowerManager powerManager;

    @Override
    public String getDisplayName() {
        switch (DeviceHelper.getDeviceType(context)) {
            case Tv:
                return context.getString(R.string.findmyphone_title_tv);
            case Tablet:
                return context.getString(R.string.findmyphone_title_tablet);
            case Phone:
            default:
                return context.getString(R.string.findmyphone_title);
        }
    }

    @Override
    public String getDescription() {
        return context.getString(R.string.findmyphone_description);
    }

    @Override
    public boolean onCreate() {
        notificationManager = ContextCompat.getSystemService(context, NotificationManager.class);
        notificationId = (int) System.currentTimeMillis();
        audioManager = ContextCompat.getSystemService(context, AudioManager.class);
        powerManager = ContextCompat.getSystemService(context, PowerManager.class);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Uri ringtone;
        String ringtoneString = prefs.getString(context.getString(R.string.findmyphone_preference_key_ringtone), "");
        if (ringtoneString.isEmpty()) {
            ringtone = Settings.System.DEFAULT_RINGTONE_URI;
        } else {
            ringtone = Uri.parse(ringtoneString);
        }

        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(context, ringtone);
            //TODO: Deprecated use setAudioAttributes for API > 21
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
        } catch (Exception e) {
            Log.e("FindMyPhoneActivity", "Exception", e);
            return false;
        }

        return true;
    }

    @Override
    public void onDestroy() {
        if (mediaPlayer.isPlaying()) {
            stopPlaying();
        }
        audioManager = null;
        mediaPlayer.release();
        mediaPlayer = null;
    }

    @Override
    public boolean onPacketReceived(NetworkPacket np) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || MyApplication.isInForeground()) {
            Intent intent = new Intent(context, FindMyPhoneActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(FindMyPhoneActivity.EXTRA_DEVICE_ID, device.getDeviceId());
            context.startActivity(intent);
        } else {
            if (powerManager.isInteractive()) {
                startPlaying();
                showBroadcastNotification();
            } else {
                showActivityNotification();
            }
        }

        return true;
    }

    private void showBroadcastNotification() {
        Intent intent = new Intent(context, FindMyPhoneReceiver.class);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        intent.setAction(FindMyPhoneReceiver.ACTION_FOUND_IT);
        intent.putExtra(FindMyPhoneReceiver.EXTRA_DEVICE_ID, device.getDeviceId());

        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

        createNotification(pendingIntent);
    }

    private void showActivityNotification() {
        Intent intent = new Intent(context, FindMyPhoneActivity.class);
        intent.putExtra(FindMyPhoneActivity.EXTRA_DEVICE_ID, device.getDeviceId());

        PendingIntent pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        createNotification(pi);
    }

    private void createNotification(PendingIntent pendingIntent) {
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, NotificationHelper.Channels.HIGHPRIORITY);
        notification
                .setSmallIcon(R.drawable.ic_notification)
                .setOngoing(false)
                .setFullScreenIntent(pendingIntent, true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentTitle(context.getString(R.string.findmyphone_found));
        notification.setGroup("BackgroundService");

        notificationManager.notify(notificationId, notification.build());
    }

    void startPlaying() {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            // Make sure we are heard even when the phone is silent, restore original volume later
            previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);

            mediaPlayer.start();
        }
    }

    void hideNotification() {
        notificationManager.cancel(notificationId);
    }

    void stopPlaying() {
        if (audioManager == null) {
            // The Plugin was destroyed (probably the device disconnected)
            return;
        }

        if (previousVolume != -1) {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, previousVolume, 0);
        }
        mediaPlayer.stop();
        try {
            mediaPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    boolean isPlaying() {
        return mediaPlayer.isPlaying();
    }

    @Override
    public String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_FINDMYPHONE_REQUEST};
    }

    @Override
    public String[] getOutgoingPacketTypes() {
        return ArrayUtils.EMPTY_STRING_ARRAY;
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public PluginSettingsFragment getSettingsFragment(Activity activity) {
        return FindMyPhoneSettingsFragment.newInstance(getPluginKey(), R.xml.findmyphoneplugin_preferences);
    }
}
