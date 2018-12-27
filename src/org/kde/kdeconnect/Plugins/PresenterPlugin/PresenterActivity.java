/*
 * Copyright 2014 Ahmed I. Khalil <ahmedibrahimkhali@gmail.com>
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

package org.kde.kdeconnect.Plugins.PresenterPlugin;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.UserInterface.ThemeUtil;
import org.kde.kdeconnect_tp.R;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media.VolumeProviderCompat;

public class PresenterActivity extends AppCompatActivity {

    private MediaSessionCompat mMediaSession;

    private PresenterPlugin plugin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtil.setUserPreferredTheme(this);

        setContentView(R.layout.activity_presenter);

        final String deviceId = getIntent().getStringExtra("deviceId");

        BackgroundService.runWithPlugin(this, deviceId, PresenterPlugin.class, plugin -> runOnUiThread(() -> {
            this.plugin = plugin;
            findViewById(R.id.next_button).setOnClickListener(v -> plugin.sendNext());
            findViewById(R.id.previous_button).setOnClickListener(v -> plugin.sendPrevious());
        }));
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_presenter, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.fullscreen:
                plugin.sendFullscreen();
                return true;
            case R.id.exit_presentation:
                plugin.sendEsc();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        BackgroundService.addGuiInUseCounter(this);
        if (mMediaSession != null) {
            mMediaSession.setActive(true);
            return;
        }
        createMediaSession(); //Mediasession will keep
    }

    @Override
    protected void onStop() {
        super.onStop();
        BackgroundService.removeGuiInUseCounter(this);

        if (mMediaSession != null) {
            PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
            boolean screenOn;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                screenOn = pm.isInteractive();
            } else {
                screenOn = pm.isScreenOn();
            }
            if (screenOn) {
                mMediaSession.release();
            } // else we are in the lockscreen, keep the mediasession
        }
    }

    private void createMediaSession() {
        mMediaSession = new MediaSessionCompat(this, "kdeconnect");

        mMediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mMediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, 0, 0)
                .build());
        mMediaSession.setPlaybackToRemote(getVolumeProvider());
        mMediaSession.setActive(true);
    }

    private VolumeProviderCompat getVolumeProvider() {
        final int VOLUME_UP = 1;
        final int VOLUME_DOWN = -1;
        return new VolumeProviderCompat(VolumeProviderCompat.VOLUME_CONTROL_RELATIVE, 1, 0) {
            @Override
            public void onAdjustVolume(int direction) {
                if (direction == VOLUME_UP) {
                    plugin.sendNext();
                }
                else if (direction == VOLUME_DOWN) {
                    plugin.sendPrevious();
                }
            }
        };
    }

}

