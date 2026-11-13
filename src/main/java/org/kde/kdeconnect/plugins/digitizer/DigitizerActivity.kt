/*
 * SPDX-FileCopyrightText: 2025 Martin Sh <hemisputnik@proton.me>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.digitizer

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.base.BaseActivity
import org.kde.kdeconnect.extensions.viewBinding
import org.kde.kdeconnect.ui.PluginSettingsActivity
import org.kde.kdeconnect.ui.compose.components.addDeviceDisconnectedOverlay
import org.kde.kdeconnect_tp.R
import org.kde.kdeconnect_tp.databinding.ActivityDigitizerBinding
import kotlin.math.roundToInt

class DigitizerActivity : BaseActivity<ActivityDigitizerBinding>(), DrawingPadView.EventListener {
    override val binding: ActivityDigitizerBinding by viewBinding(ActivityDigitizerBinding::inflate)

    private lateinit var fullscreenBackCallback: OnBackPressedCallback

    private lateinit var plugin: DigitizerPlugin

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        plugin = KdeConnect.getInstance().getDevicePlugin<DigitizerPlugin>(intent.getStringExtra(EXTRA_DEVICE_ID))
            ?: return finish()
        binding.root.addDeviceDisconnectedOverlay(plugin.device)

        setSupportActionBar(binding.toolbarLayout.toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)

        binding.drawingPad.eventListener = this

        @SuppressLint("ClickableViewAccessibility")
        binding.buttonDraw.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    binding.drawingPad.fingerTouchEventsEnabled = true
                    true
                }
                MotionEvent.ACTION_UP -> {
                    binding.drawingPad.fingerTouchEventsEnabled = false
                    true
                }
                else -> false
            }
        }

        fullscreenBackCallback = onBackPressedDispatcher.addCallback(this) {
            disableFullscreen()
        }
    }

    private fun enableFullscreen() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        supportActionBar!!.hide()
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        fullscreenBackCallback.isEnabled = true
    }

    private fun disableFullscreen() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT

        supportActionBar!!.show()
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())

        fullscreenBackCallback.isEnabled = false
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onStart() {
        super.onStart()
        // During onStart, the views aren't laid out yet.
        // We must wait a frame for the view to get laid out before we can query its size.
        binding.drawingPad.post {
            plugin.startSession(
                binding.drawingPad.width,
                binding.drawingPad.height,
                (resources.displayMetrics.xdpi * INCHES_TO_MM).roundToInt(),
                (resources.displayMetrics.ydpi * INCHES_TO_MM).roundToInt()
            )
        }

        val prefHideDrawButton = plugin.preferences.getBoolean(getString(R.string.digitizer_preference_key_hide_draw_button), false)
        if (prefHideDrawButton) {
            binding.buttonDraw.visibility = View.GONE
        }

        binding.buttonDraw.layoutParams = (binding.buttonDraw.layoutParams as FrameLayout.LayoutParams).also {
            @SuppressLint("RtlHardcoded")
            when (plugin.preferences.getString(getString(R.string.digitizer_preference_key_draw_button_side), "bottom_left")) {
                "top_left" -> it.gravity = Gravity.TOP or Gravity.LEFT
                "top_right" -> it.gravity = Gravity.TOP or Gravity.RIGHT
                "bottom_left" -> it.gravity = Gravity.BOTTOM or Gravity.LEFT
                "bottom_right" -> it.gravity = Gravity.BOTTOM or Gravity.RIGHT
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (::plugin.isInitialized) {
            plugin.endSession()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_digitizer, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_fullscreen -> {
                enableFullscreen()
                true
            }
            R.id.menu_open_settings -> {
                startActivity(PluginSettingsActivity.createIntent(this, plugin.device.deviceId, DigitizerPlugin::class.java.simpleName))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onToolEvent(event: ToolEvent) {
        plugin.reportEvent(event)
    }

    override fun onFingerTouchEvent(touching: Boolean) {
        binding.buttonDraw.isEnabled = touching
    }

    companion object {
        const val EXTRA_DEVICE_ID = "deviceId"
        private const val INCHES_TO_MM = 0.0393701
    }
}
