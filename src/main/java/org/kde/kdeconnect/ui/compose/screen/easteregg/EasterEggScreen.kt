/*
 * SPDX-FileCopyrightText: 2026 Tanish Ranjan <tanishranjan4@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.screen.easteregg

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.kde.kdeconnect_tp.R
import kotlin.math.PI
import kotlin.math.atan2

private val KDE_ICON_BACKGROUND_COLOR = Color(29, 153, 243)
private val KONQI_BACKGROUND_COLOR = Color(191, 255, 0)

@Composable
fun EasterEggScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    val accelerometer = remember { sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }

    var currentAngle by remember { mutableFloatStateOf(0f) }
    var currentIcon by remember { mutableIntStateOf(R.drawable.ic_kde_48dp) }
    var backgroundColor by remember { mutableStateOf(KDE_ICON_BACKGROUND_COLOR) }

    val animatedRotation by animateFloatAsState(
        targetValue = currentAngle,
        animationSpec = tween(durationMillis = 300),
        label = "LogoRotation"
    )

    val icons = remember {
        intArrayOf(
            R.drawable.ic_action_keyboard_24dp, R.drawable.ic_action_refresh_24dp,
            R.drawable.ic_action_image_edit_24dp, R.drawable.ic_arrow_upward_black_24dp,
            R.drawable.ic_baseline_attach_money_24, R.drawable.ic_baseline_bug_report_24,
            R.drawable.ic_baseline_code_24, R.drawable.ic_baseline_gavel_24,
            R.drawable.ic_baseline_info_24, R.drawable.ic_baseline_web_24,
            R.drawable.ic_baseline_send_24, R.drawable.ic_baseline_sms_24,
            R.drawable.ic_accept_pairing_24dp, R.drawable.ic_share_white,
            R.drawable.ic_delete,
            R.drawable.ic_device_laptop_32dp, R.drawable.ic_device_phone_32dp,
            R.drawable.ic_device_tablet_32dp, R.drawable.ic_device_tv_32dp,
            R.drawable.ic_delete, R.drawable.ic_warning,
            R.drawable.ic_volume, R.drawable.ic_wifi,
            R.drawable.ic_add, R.drawable.touchpad_plugin_action_24dp,
            R.drawable.konqi, R.drawable.run_command_plugin_icon_24dp,
            R.drawable.ic_phonelink_36dp, R.drawable.ic_phonelink_off_36dp,
            R.drawable.ic_error_outline_48dp, R.drawable.ic_home_black_24dp,
            R.drawable.ic_settings_white_32dp, R.drawable.ic_stop,
            R.drawable.ic_rewind_black, R.drawable.ic_play_black,
            R.drawable.ic_mic_black, R.drawable.ic_pause_black,
            R.drawable.ic_volume_mute, R.drawable.ic_arrow_upward_black_24dp,
            R.drawable.ic_next_black, R.drawable.ic_previous_black,
            R.drawable.ic_presenter_24dp, R.drawable.ic_key,
            R.drawable.ic_keyboard_return_black_24dp, R.drawable.ic_keyboard_hide_36dp,
            R.drawable.ic_kde_24dp, R.drawable.ic_album_art_placeholder,
            R.drawable.ic_arrow_back_black_24dp, R.drawable.share_plugin_action_24dp
        )
    }

    DisposableEffect(lifecycleOwner, accelerometer) {
        if (accelerometer == null || sensorManager == null) return@DisposableEffect onDispose {}

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val axisX = event.values[0]
                val axisY = event.values[1]
                currentAngle = (atan2(axisX, axisY) / (PI / 180)).toFloat()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                sensorManager.registerListener(
                    listener,
                    accelerometer,
                    SensorManager.SENSOR_DELAY_UI
                )
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                sensorManager.unregisterListener(listener)
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sensorManager.unregisterListener(listener)
        }
    }

    val activity = context as? ComponentActivity
    LaunchedEffect(backgroundColor) {
        val isDarkBackground = backgroundColor == KDE_ICON_BACKGROUND_COLOR
        val transparentArgb = Color.Transparent.toArgb()

        val barStyle = if (isDarkBackground) {
            SystemBarStyle.dark(transparentArgb)
        } else {
            SystemBarStyle.light(transparentArgb, transparentArgb)
        }

        activity?.enableEdgeToEdge(
            statusBarStyle = barStyle,
            navigationBarStyle = barStyle
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        val icon = icons.random()
                        currentIcon = icon
                        backgroundColor = if (icon == R.drawable.konqi) {
                            KONQI_BACKGROUND_COLOR
                        } else {
                            KDE_ICON_BACKGROUND_COLOR
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = currentIcon),
                contentDescription = null,
                modifier = Modifier
                    .size(396.dp)
                    .rotate(animatedRotation),
                colorFilter = if (currentIcon == R.drawable.konqi) null else ColorFilter.tint(Color.White)
            )

            if (accelerometer != null) {
                Text(
                    text = "${currentAngle.toInt()}°",
                    color = Color.White,
                    fontSize = 18.sp
                )
            }
        }
    }
}
