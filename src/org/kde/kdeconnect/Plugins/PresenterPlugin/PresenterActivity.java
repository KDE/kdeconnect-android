/*
 * SPDX-FileCopyrightText: 2014 Ahmed I. Khalil <ahmedibrahimkhali@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Plugins.PresenterPlugin;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
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

import java.util.Objects;

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

        setSupportActionBar(binding.toolbarLayout.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

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
        int id = item.getItemId();
        if (id == R.id.fullscreen) {
            plugin.sendFullscreen();
            return true;
        } else if (id == R.id.exit_presentation) {
            plugin.sendEsc();
            return true;
        } else {
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
            boolean screenOn = pm.isInteractive();
            if (screenOn) {
                mMediaSession.release();
            } // else we are in the lockscreen, keep the mediasession
        }
    }

    private void createMediaSession() {
        mMediaSession = new MediaSessionCompat(this, "kdeconnect");

        // Deprecated flags not required in Build.VERSION_CODES.O and later
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

    @Override
    public boolean onSupportNavigateUp() {
        super.onBackPressed();
        return true;
    }
}

