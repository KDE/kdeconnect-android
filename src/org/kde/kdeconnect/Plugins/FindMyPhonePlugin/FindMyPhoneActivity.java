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
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import org.kde.kdeconnect.UserInterface.ThemeUtil;
import org.kde.kdeconnect_tp.R;

public class FindMyPhoneActivity extends Activity {

    private MediaPlayer mediaPlayer;
    private int previousVolume;
    private AudioManager audioManager;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (mediaPlayer != null) {
            // If this activity was already open and we received the ring packet again, just finish it
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

        Window window = this.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        findViewById(R.id.bFindMyPhone).setOnClickListener(view -> finish());
    }

    @Override
    protected void onStart() {
        super.onStart();

        try {
            // Make sure we are heard even when the phone is silent, restore original volume later
            previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            Uri ringtone;
            String ringtoneString = prefs.getString(getString(R.string.findmyphone_preference_key_ringtone), "");
            if (ringtoneString.isEmpty()) {
                ringtone = Settings.System.DEFAULT_RINGTONE_URI;
            } else {
                ringtone = Uri.parse(ringtoneString);
            }

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, ringtone);
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();

        } catch (Exception e) {
            Log.e("FindMyPhoneActivity", "Exception", e);
        }

    }

    @Override
    protected void onStop() {
        super.onStop();

        if (mediaPlayer != null) {
            mediaPlayer.stop();
        }
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, previousVolume, 0);
    }

}
