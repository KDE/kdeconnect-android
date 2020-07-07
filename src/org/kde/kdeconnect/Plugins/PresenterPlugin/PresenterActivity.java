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

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.media.VolumeProviderCompat;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.UserInterface.ThemeUtil;
import org.kde.kdeconnect_tp.R;
import org.kde.kdeconnect_tp.databinding.ActivityPresenterBinding;

public class PresenterActivity extends AppCompatActivity implements SensorEventListener {
    private ActivityPresenterBinding binding;

    private MediaSessionCompat mMediaSession;

    private PresenterPlugin plugin;

    private SensorManager sensorManager;

    static final float SENSITIVITY = 0.03f; //TODO: Make configurable?

    public void gyroscopeEvent(SensorEvent event) {
        float xPos = -event.values[2] * SENSITIVITY;
        float yPos = -event.values[0] * SENSITIVITY;

        plugin.sendPointer(xPos, yPos);
    }

    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            gyroscopeEvent(event);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //Ignored
    }

    void enablePointer() {
        if (sensorManager != null) {
            return; //Already enabled
        }
        sensorManager = ContextCompat.getSystemService(this, SensorManager.class);
        binding.pointerButton.setVisibility(View.VISIBLE);
        binding.pointerButton.setOnTouchListener((v, event) -> {
            if(event.getAction() == MotionEvent.ACTION_DOWN){
                sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_GAME);
                v.performClick(); // The linter complains if this is not called
            }
            else if (event.getAction() == MotionEvent.ACTION_UP) {
                sensorManager.unregisterListener(this);
                plugin.stopPointer();
            }
            return true;
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtil.setUserPreferredTheme(this);

        binding = ActivityPresenterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        final String deviceId = getIntent().getStringExtra("deviceId");

        BackgroundService.RunWithPlugin(this, deviceId, PresenterPlugin.class, plugin -> runOnUiThread(() -> {
            this.plugin = plugin;
            binding.nextButton.setOnClickListener(v -> plugin.sendNext());
            binding.previousButton.setOnClickListener(v -> plugin.sendPrevious());
            if (plugin.isPointerSupported()) {
                enablePointer();
            }
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
        if (mMediaSession != null) {
            mMediaSession.setActive(true);
            return;
        }
        createMediaSession();
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (sensorManager != null) {
            // Make sure we don't leave the listener on
            sensorManager.unregisterListener(this);
        }

        if (mMediaSession != null) {
            PowerManager pm = ContextCompat.getSystemService(this, PowerManager.class);
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

