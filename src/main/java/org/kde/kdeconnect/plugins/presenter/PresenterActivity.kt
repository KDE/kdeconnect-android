/*
 * SPDX-FileCopyrightText: 2023 Dmitry Yudin <dgyudin@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.presenter

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.media.VolumeProviderCompat
import androidx.preference.PreferenceManager
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.ui.compose.KdeButton
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.compose.KdeTopAppBar
import org.kde.kdeconnect.extensions.safeDrawPadding
import org.kde.kdeconnect_tp.R

private const val VOLUME_UP = 1
private const val VOLUME_DOWN = -1

class PresenterActivity : AppCompatActivity(), SensorEventListener, OnSharedPreferenceChangeListener {

    private val offScreenControlsSupported = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA
    private val mediaSession by lazy {
        if (offScreenControlsSupported) MediaSessionCompat(this, "kdeconnect") else null
    }
    private lateinit var plugin : PresenterPlugin
    private var prefsApplied = false
    private var volumeKeys = false
    private var prefs: SharedPreferences? = null
    private var sensitivity = 0.03f

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

        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs!!.registerOnSharedPreferenceChangeListener(this)
        applyPrefs()

        plugin = KdeConnect.getInstance().getDevicePlugin(intent.getStringExtra("deviceId"), PresenterPlugin::class.java)
            ?: run {
                finish()
                return
            }
        setContent { PresenterScreen() }
        createMediaSession()
    }

    override fun onDestroy() {
        mediaSession?.release()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        //Only override dispatchKeyEvent if offScreenControls is not supported
        if (offScreenControlsSupported){
            return super.dispatchKeyEvent(event)
        }

        val keyCode = event.keyCode
        val action = event.action
        if (volumeKeys) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && action == KeyEvent.ACTION_UP) {
                plugin.sendPrevious()
            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && action == KeyEvent.ACTION_UP) {
                plugin.sendNext()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun createMediaSession() {
        if (volumeKeys){
            mediaSession?.setPlaybackState(
                PlaybackStateCompat.Builder().setState(PlaybackStateCompat.STATE_PLAYING, 0, 0f).build()
            )
            mediaSession?.setPlaybackToRemote(volumeProvider)
            mediaSession?.setActive(true)
        }
    }

    private val volumeProvider = object : VolumeProviderCompat(VOLUME_CONTROL_RELATIVE, 0, 0) {
        override fun onAdjustVolume(direction: Int) {
            if (direction == VOLUME_UP) {
                plugin.sendNext()
            } else if (direction == VOLUME_DOWN) {
                plugin.sendPrevious()
            }
        }
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences?,
        key: String?
    ) {
        if (prefsApplied) prefsApplied = false
    }

    private fun applyPrefs() {
        if (prefsApplied) return

        var scrollSensitivity = prefs!!.getInt(getString(R.string.pref_presenter_sensitivity), 50)
        scrollSensitivity += 10 // Do not allow near-zero sensitivity
        sensitivity = ((scrollSensitivity / 100f)/10f)*(6f/10f)

        volumeKeys =
            prefs!!.getBoolean(getString(R.string.pref_presenter_enable_volume_keys), true)

        prefsApplied = true
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Preview
    @Composable
    private fun PresenterScreen() {

        val sensorManager = LocalContext.current.getSystemService(SENSOR_SERVICE) as? SensorManager

        KdeTheme(this) {
            Scaffold(
                modifier = Modifier.safeDrawPadding(),
                topBar = { PresenterAppBar() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(it)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    if (volumeKeys) Text(
                        text = stringResource(if (offScreenControlsSupported) R.string.presenter_volume_keys_tip else R.string.presenter_volume_keys_foreground_tip),
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    @Suppress("DEPRECATION") // we explicitly want the non-mirrored version of the icons
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(3f),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        KdeButton(
                            onClick = { plugin.sendPrevious() },
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            contentDescription = getString(R.string.mpris_previous),
                            icon = Icons.Default.ArrowBack,
                        )
                        KdeButton(
                            onClick = { plugin.sendNext() },
                            contentDescription = getString(R.string.mpris_next),
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            icon = Icons.Default.ArrowForward,
                        )
                    }
                    if (sensorManager != null) KdeButton(
                        onClick = {},
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .pointerInteropFilter { event ->
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
