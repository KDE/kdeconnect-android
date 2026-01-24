/*
 * SPDX-FileCopyrightText: 2021 Maxim Leshchenko <cnmaks90@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.about

import android.animation.ValueAnimator
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.kde.kdeconnect_tp.R
import org.kde.kdeconnect_tp.databinding.ActivityEasterEggBinding
import kotlin.math.PI
import kotlin.math.atan2

private val KDE_ICON_BACKGROUND_COLOR = Color.rgb(29, 153, 243)
private val KONQI_BACKGROUND_COLOR = Color.rgb(191, 255, 0)

class EasterEggActivity : AppCompatActivity(), SensorEventListener {
    private var binding: ActivityEasterEggBinding? = null
    private lateinit var sensorManager: SensorManager
    private val animator = ValueAnimator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityEasterEggBinding.inflate(layoutInflater)
        setContentView(binding!!.root)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR

        setBgColor(KDE_ICON_BACKGROUND_COLOR)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        if (hasAccelerometer()) {
            animator.addUpdateListener {
                binding!!.logo.rotation = animator.animatedValue as Float
            }
            animator.duration = 300
        } else {
            binding!!.angle.visibility = View.GONE
        }

        // Make Easter Egg more fun
        binding!!.easterEggLayout.setOnLongClickListener {
            val icon = intArrayOf(
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
            ).random()

            if (icon == R.drawable.konqi) {
                binding!!.logo.clearColorFilter()
                setBgColor(KONQI_BACKGROUND_COLOR)
            } else {
                binding!!.logo.setColorFilter(ContextCompat.getColor(this, android.R.color.white))
                setBgColor(KDE_ICON_BACKGROUND_COLOR)
            }

            binding!!.logo.setImageResource(icon)

            true
        }
    }

    private fun setBgColor(color : Int) {
        binding!!.easterEggLayout.setBackgroundColor(color)
        window.statusBarColor = color
        window.navigationBarColor = color
    }
    private fun hasAccelerometer(): Boolean = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

    override fun onResume() {
        super.onResume()
        if (hasAccelerometer()) {
            sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        if (hasAccelerometer()) {
            sensorManager.unregisterListener(this)
        }
        super.onPause()
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) { }
    override fun onSensorChanged(event: SensorEvent) {
        if (binding != null) {
            val axisX = event.values[0]
            val axisY = event.values[1]

            val angle = (atan2(axisX, axisY) / (PI / 180)).toInt()
            binding!!.angle.text = "$angle°"

            animator.setFloatValues(binding!!.logo.rotation, angle.toFloat())
            animator.start()
        }
    }
}
