/* Copyright 2018 Nicolas Fella <nicolas.fella@gmx.de>
 * Copyright 2015 David Edmundson <david@davidedmundson.co.uk>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License or (at your option) version 3 or any later version
 * accepted by the membership of KDE e.V. (or its successor approved
 * by the membership of KDE e.V.), which shall act as a proxy
 * defined in Section 14 of version 3 of the license.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.kde.kdeconnect.Plugins.FindMyPhonePlugin;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.RequiresApi;

import org.kde.kdeconnect.UserInterface.ThemeUtil;
import org.kde.kdeconnect_tp.R;

import static android.os.Build.VERSION_CODES.O;


public class FindMyPhoneActivity extends Activity implements MediaPlayer.OnPreparedListener,
        AudioManager.OnAudioFocusChangeListener {

    private final Object focusLock = new Object();
    private int previousVolume = -1;
    private AudioManager audioManager;

    private AudioAttributes audioAttributes;

    private MediaPlayer mediaPlayer;
    private boolean playerPrepared = false;

    private AudioFocusRequest focusRequest;
    private boolean playbackDelayed;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (mediaPlayer != null) {
            // If this activity was already open and we received the ring packet again, just finish it
            mediaPlayer.release();
            finish();
        }
        // otherwise the activity will become active again
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtil.setUserPreferredTheme(this);
        setContentView(R.layout.activity_find_my_phone);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // Prepare audio attributes for media requests
        audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                .build();

        // Prepare request for temporary sole audio focus
        if (Build.VERSION.SDK_INT >= O) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(audioAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener(this)
                    .build();
        }

        Window window = this.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        findViewById(R.id.bFindMyPhone).setOnClickListener(view -> finish());
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onStart() {
        super.onStart();

        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            Uri ringtone;
            String ringtoneString = prefs.getString(getString(R.string.findmyphone_preference_key_ringtone), "");
            if (ringtoneString.isEmpty()) {
                ringtone = Settings.System.DEFAULT_RINGTONE_URI;
            } else {
                ringtone = Uri.parse(ringtoneString);
            }

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(audioAttributes);

            // Prevent screen/CPU sleep during playback -- requries WAKE_LOCK permission!!
            mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);

            mediaPlayer.setDataSource(this, ringtone);
            mediaPlayer.setLooping(true);

            // Fire off async prepare, onPrepared() will pick up when ready
            mediaPlayer.setOnPreparedListener(this);
            mediaPlayer.prepareAsync();

        } catch (Exception e) {
            Log.e("FindMyPhoneActivity", "Exception", e);
        }

    }

    // implementation of the OnAudioFocusChangeListener

    protected void playNow() {
        if (mediaPlayer != null && playerPrepared) {
            try {
                // Make sure we are heard even when the phone is silent, restore original volume later
                previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);

                // Finally start playback
                mediaPlayer.start();

            } catch (Exception e) {
                Log.e("FindMyPhoneActivity", "Exception", e);
            }
        }
    }

    protected void endPlayback() {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }
            if (previousVolume >= 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, previousVolume, 0);
            }
        } catch (Exception e) {
            Log.e("FindMyPhoneActivity", "Exception", e);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            int res = audioManager.abandonAudioFocusRequest(focusRequest);
        }
        synchronized (focusLock) {
            playbackDelayed = false;
        }
        endPlayback();
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                if (playbackDelayed) {
                    synchronized (focusLock) {
                        playbackDelayed = false;
                    }
                    playNow();
                    break;
                }
                synchronized (focusLock) {
                    playbackDelayed = false;
                }
                endPlayback();
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // If we lose focus once gained, stop playback
                synchronized (focusLock) {
                    playbackDelayed = false;
                }
                endPlayback();
                break;
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        playerPrepared = true;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            // Attempt to gain sole audio focus (temporarily)
            int res = audioManager.requestAudioFocus(focusRequest);
            synchronized (focusLock) {
                if (res == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
                    playbackDelayed = false;
                } else if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    playbackDelayed = false;
                    playNow();
                } else if (res == AudioManager.AUDIOFOCUS_REQUEST_DELAYED) {
                    playbackDelayed = true;
                }
            }
        } else {
            playNow();
        }
    }
}
