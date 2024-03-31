/*
 * SPDX-FileCopyrightText: 2023 Dmitry Yudin <dgyudin@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.PresenterPlugin

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.media.VolumeProviderCompat
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.UserInterface.compose.KdeButton
import org.kde.kdeconnect.UserInterface.compose.KdeTheme
import org.kde.kdeconnect.UserInterface.compose.KdeTopAppBar
import org.kde.kdeconnect_tp.R

private const val VOLUME_UP = 1
private const val VOLUME_DOWN = -1

class PresenterActivity : AppCompatActivity(), SensorEventListener {

    private val offScreenControlsSupported = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
    private val mediaSession by lazy {
        if (offScreenControlsSupported) MediaSessionCompat(this, "kdeconnect") else null
    }
    private val powerManager by lazy { getSystemService(POWER_SERVICE) as PowerManager }
    private val plugin: PresenterPlugin by lazy {
        KdeConnect.getInstance().getDevicePlugin(intent.getStringExtra("deviceId"), PresenterPlugin::class.java)!!
    }

    //TODO: make configurable
    private val sensitivity = 0.03f
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_GYROSCOPE) {
            val xPos = -event.values[2] * sensitivity
            val yPos = -event.values[0] * sensitivity

            plugin.sendPointer(xPos, yPos)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        //ignored
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PresenterScreen() }
        createMediaSession()
    }

    override fun onResume() {
        super.onResume()
        mediaSession?.setActive(false)
    }

    override fun onPause() {
        super.onPause()
        //fixme watch for isInteractive in background
        mediaSession?.setActive(!powerManager.isInteractive)
    }

    override fun onDestroy() {
        mediaSession?.release()
        super.onDestroy()
    }

    private fun createMediaSession() {
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder().setState(PlaybackStateCompat.STATE_PLAYING, 0, 0f).build()
        )
        mediaSession?.setPlaybackToRemote(volumeProvider)

    }

    private val volumeProvider = object : VolumeProviderCompat(VOLUME_CONTROL_RELATIVE, 1, 0) {
        override fun onAdjustVolume(direction: Int) {
            if (direction == VOLUME_UP) {
                plugin.sendNext()
            } else if (direction == VOLUME_DOWN) {
                plugin.sendPrevious()
            }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Preview
    @Composable
    private fun PresenterScreen() {

        val sensorManager = LocalContext.current.getSystemService(SENSOR_SERVICE) as? SensorManager

        KdeTheme(this) {
            Scaffold(topBar = { PresenterAppBar() }) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(it).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    if (offScreenControlsSupported) Text(
                        text = stringResource(R.string.presenter_lock_tip),
                        modifier = Modifier.padding(bottom = 8.dp).padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Row(
                        modifier = Modifier.fillMaxSize().weight(3f),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        KdeButton(
                            onClick = { plugin.sendPrevious() },
                            modifier = Modifier.fillMaxSize().weight(1f),
                            contentDescription = getString(R.string.mpris_previous),
                            icon = Icons.Default.ArrowBack,
                        )
                        KdeButton(
                            onClick = { plugin.sendNext() },
                            contentDescription = getString(R.string.mpris_next),
                            modifier = Modifier.fillMaxSize().weight(1f),
                            icon = Icons.Default.ArrowForward,
                        )
                    }
                    if (sensorManager != null) KdeButton(
                        onClick = {},
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        modifier = Modifier.fillMaxSize().weight(1f).pointerInteropFilter { event ->
                            when (event.action) {
                                MotionEvent.ACTION_DOWN -> {
                                    sensorManager.registerListener(
                                        this@PresenterActivity,
                                        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                                        SensorManager.SENSOR_DELAY_GAME
                                    )
                                }

                                MotionEvent.ACTION_UP -> {
                                    sensorManager.unregisterListener(this@PresenterActivity)
                                    plugin.stopPointer()
                                    false
                                }

                                else -> false
                            }
                        },
                        text = stringResource(R.string.presenter_pointer),
                    )
                }
            }
        }
    }

    @Preview
    @Composable
    private fun PresenterAppBar() {

        var dropdownShownState by remember { mutableStateOf(false) }

        KdeTopAppBar(
            title = stringResource(R.string.pref_plugin_presenter),
            navIconOnClick = { onBackPressedDispatcher.onBackPressed() },
            navIconDescription = getString(androidx.appcompat.R.string.abc_action_bar_up_description),
            actions = {
                IconButton(onClick = { dropdownShownState = true }) {
                    Icon(Icons.Default.MoreVert, stringResource(R.string.extra_options))
                }
                DropdownMenu(expanded = dropdownShownState, onDismissRequest = { dropdownShownState = false }) {
                    DropdownMenuItem(
                        onClick = { plugin.sendFullscreen() },
                        text = { Text(stringResource(R.string.presenter_fullscreen)) },
                    )
                    DropdownMenuItem(
                        onClick = { plugin.sendEsc() },
                        text = { Text(stringResource(R.string.presenter_exit)) },
                    )
                }
            }
        )
    }
}
