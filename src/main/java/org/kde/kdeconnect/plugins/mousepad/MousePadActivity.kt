/*
 * SPDX-FileCopyrightText: 2014 Ahmed I. Khalil <ahmedibrahimkhali@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.mousepad

import android.content.Intent
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.Insets
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.base.BaseActivity
import org.kde.kdeconnect.ui.PluginSettingsActivity
import org.kde.kdeconnect_tp.R
import org.kde.kdeconnect_tp.databinding.ActivityMousepadBinding
import java.util.Objects
import kotlin.math.abs
import kotlin.math.pow

class MousePadActivity :
    BaseActivity<ActivityMousepadBinding>(),
    GestureDetector.OnGestureListener,
    GestureDetector.OnDoubleTapListener,
    MousePadGestureDetector.OnGestureListener,
    SensorEventListener,
    SharedPreferences.OnSharedPreferenceChangeListener,
    OnApplyWindowInsetsListener {

    private lateinit var deviceId: String

    private var mPrevX: Float = 0f
    private var mPrevY: Float = 0f
    internal var dragging: Boolean = false
    internal var maybeDragging: Boolean = false
    private var accumulatedDragDistance2: Float = 0.0f
    private var mCurrentSensitivity: Float = 1.0f
    private var displayDpiMultiplier: Float = 1.0f
    private var scrollDirection: Int = 1
    private var scrollCoefficient: Double = 1.0
    private var allowGyro: Boolean = false
    private var gyroEnabled: Boolean = false
    private var doubleTapDragEnabled: Boolean = false
    private var gyroscopeSensitivity: Int = 100
    private var isScrolling: Boolean = false
    private var accumulatedDistanceX: Double = 0.0
    private var accumulatedDistanceY: Double = 0.0
    private var pendingShowKeyboard: Boolean = false
    private var keyboardShown: Boolean = false

    private lateinit var mDetector: GestureDetector
    private var mSensorManager: SensorManager? = null
    private lateinit var mMousePadGestureDetector: MousePadGestureDetector
    private lateinit var mPointerAccelerationProfile: PointerAccelerationProfile

    private var mouseDelta: PointerAccelerationProfile.MouseDelta = PointerAccelerationProfile.MouseDelta() // to be reused on every touch move event

    private lateinit var keyListenerView: KeyListenerView

    private lateinit var prefs: SharedPreferences

    private var prefsApplied: Boolean = false

    override val binding: ActivityMousepadBinding by lazy {
        ActivityMousepadBinding.inflate(layoutInflater)
    }

    private enum class ClickType {
        LEFT,
        RIGHT,
        MIDDLE,
        NONE;

        companion object {
            fun fromString(s: String?): ClickType {
                return when (s) {
                    "left" -> LEFT
                    "right" -> RIGHT
                    "middle" -> MIDDLE
                    else -> NONE
                }
            }
        }
    }

    private var singleTapAction: ClickType = ClickType.NONE
    private var doubleTapAction: ClickType = ClickType.NONE
    private var tripleTapAction: ClickType = ClickType.NONE

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent) {
        val values = event.values

        var x = -values[2] * 70 * (gyroscopeSensitivity / 100.0f)
        var y = -values[0] * 70 * (gyroscopeSensitivity / 100.0f)

        if (x < 0.25 && x > -0.25) {
            x = 0f
        } else {
            x *= (gyroscopeSensitivity / 100.0f)
        }

        if (y < 0.25 && y > -0.25) {
            y = 0f
        } else {
            y *= (gyroscopeSensitivity / 100.0f)
        }

        val plugin = KdeConnect.getInstance().getDevicePlugin<MousePadPlugin>(deviceId)
        if (plugin == null) {
            finish()
            return
        }
        plugin.sendMouseDelta(x, y)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setSupportActionBar(binding.toolbarLayout.toolbar)
        requireNotNull(supportActionBar).setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.mouseClickLeft.setOnClickListener { sendLeftClick() }
        binding.mouseClickMiddle.setOnClickListener { sendMiddleClick() }
        binding.mouseClickRight.setOnClickListener { sendRightClick() }

        deviceId = intent.getStringExtra(EXTRA_DEVICE_ID) ?: ""
        if (deviceId.isEmpty()) {
            finish()
            return
        }

        window.decorView.isHapticFeedbackEnabled = true

        mDetector = GestureDetector(this, this)
        mMousePadGestureDetector = MousePadGestureDetector(this)
        mDetector.setOnDoubleTapListener(this)
        mSensorManager = ContextCompat.getSystemService(this, SensorManager::class.java)

        keyListenerView = binding.keyListener
        keyListenerView.setDeviceId(deviceId)
        ViewCompat.setOnApplyWindowInsetsListener(keyListenerView, this)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.registerOnSharedPreferenceChangeListener(this)

        applyPrefs()

        // Technically xdpi and ydpi should be handled separately,
        // but since ydpi is usually almost equal to xdpi, only xdpi is used for the multiplier.
        displayDpiMultiplier = StandardDpi / resources.displayMetrics.xdpi

        val decorView = window.decorView
        decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if ((visibility and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                var fullscreenType = 0

                fullscreenType = fullscreenType or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                fullscreenType = fullscreenType or View.SYSTEM_UI_FLAG_FULLSCREEN
                fullscreenType = fullscreenType or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

                window.decorView.systemUiVisibility = fullscreenType
            }
        }
    }

    override fun onResume() {
        applyPrefs()

        if (allowGyro && !gyroEnabled) {
            mSensorManager?.let {
                it.registerListener(this, it.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_GAME)
            }
            gyroEnabled = true
        }

        invalidateOptionsMenu()

        super.onResume()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && pendingShowKeyboard) {
            showKeyboard()
            pendingShowKeyboard = false
        }
    }

    override fun onPause() {
        if (gyroEnabled) {
            mSensorManager?.unregisterListener(this)
            gyroEnabled = false
        }
        super.onPause()
    }

    override fun onStop() {
        if (gyroEnabled) {
            mSensorManager?.unregisterListener(this)
            gyroEnabled = false
        }
        super.onStop()
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_mousepad, menu)

        val mouseButtonsEnabled = prefs.getBoolean(getString(R.string.mousepad_mouse_buttons_enabled_pref), true)

        menu.findItem(R.id.menu_right_click).isVisible = !mouseButtonsEnabled
        menu.findItem(R.id.menu_middle_click).isVisible = !mouseButtonsEnabled
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_right_click -> {
                sendRightClick()
                true
            }
            R.id.menu_middle_click -> {
                sendMiddleClick()
                true
            }
            R.id.menu_open_mousepad_settings -> {
                val intent = PluginSettingsActivity.createIntent(this, deviceId, MousePadPlugin::class.java.simpleName)
                startActivity(intent)
                true
            }
            R.id.menu_show_keyboard -> {
                val plugin = KdeConnect.getInstance().getDevicePlugin<MousePadPlugin>(deviceId)
                if (plugin == null) {
                    finish()
                    return true
                }
                if (plugin.isKeyboardEnabled) {
                    toggleKeyboard()
                } else {
                    Toast.makeText(this, R.string.mousepad_keyboard_input_not_supported, Toast.LENGTH_SHORT).show()
                }
                true
            }
            R.id.menu_open_compose_send -> {
                val plugin = KdeConnect.getInstance().getDevicePlugin<MousePadPlugin>(deviceId)
                if (plugin == null) {
                    finish()
                    return true
                }
                if (plugin.isKeyboardEnabled) {
                    showCompose()
                } else {
                    Toast.makeText(this, R.string.mousepad_keyboard_input_not_supported, Toast.LENGTH_SHORT).show()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (mMousePadGestureDetector.onTouchEvent(event)) {
            return true
        }
        if (mDetector.onTouchEvent(event)) {
            return true
        }

        val actionType = event.action

        if (isScrolling) {
            if (actionType == MotionEvent.ACTION_UP) {
                isScrolling = false
            } else {
                return false
            }
        }

        val plugin = KdeConnect.getInstance().getDevicePlugin<MousePadPlugin>(deviceId)
        if (plugin == null) {
            finish()
            return true
        }

        when (actionType) {
            MotionEvent.ACTION_DOWN -> {
                mPrevX = event.x
                mPrevY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                val mCurrentX = event.x
                val mCurrentY = event.y

                val deltaX = (mCurrentX - mPrevX) * displayDpiMultiplier * mCurrentSensitivity
                val deltaY = (mCurrentY - mPrevY) * displayDpiMultiplier * mCurrentSensitivity

                if (maybeDragging) {
                    accumulatedDragDistance2 += deltaX * deltaX + deltaY * deltaY
                    if (accumulatedDragDistance2 >= MinDraggingDistance2) {
                        maybeDragging = false
                        dragging = true
                        accumulatedDragDistance2 = 0.0f
                        plugin.sendSingleHold()
                    }
                } else {
                    // Run the mouse delta through the pointer acceleration profile
                    mPointerAccelerationProfile.touchMoved(deltaX, deltaY, event.eventTime)
                    mouseDelta = mPointerAccelerationProfile.commitAcceleratedMouseDelta(mouseDelta)

                    plugin.sendMouseDelta(mouseDelta.x, mouseDelta.y)
                }

                mPrevX = mCurrentX
                mPrevY = mCurrentY
            }
            MotionEvent.ACTION_UP -> {
                if (doubleTapDragEnabled && maybeDragging) {
                    maybeDragging = false
                    accumulatedDragDistance2 = 0.0f
                    plugin.sendDoubleClick()
                }
            }
        }
        return true
    }

    override fun onDown(e: MotionEvent): Boolean = false

    override fun onShowPress(e: MotionEvent) {
        // From GestureDetector, left empty
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean = false

    override fun onGenericMotionEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_SCROLL) {
            val distanceY = e.getAxisValue(MotionEvent.AXIS_VSCROLL)
            val distanceX = e.getAxisValue(MotionEvent.AXIS_HSCROLL)

            accumulatedDistanceY += distanceY

            if (abs(accumulatedDistanceX) > MinDistanceToSendScroll || abs(accumulatedDistanceY) > MinDistanceToSendScroll) {
                sendScroll(accumulatedDistanceX, accumulatedDistanceY)
                accumulatedDistanceY = 0.0
            }
        }

        return super.onGenericMotionEvent(e)
    }

    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
        // If only one thumb is used then cancel the scroll gesture
        if (e2.pointerCount <= 1) {
            return false
        }

        isScrolling = true

        var filteredDistanceX = distanceX
        var filteredDistanceY = distanceY

        val absX = abs(filteredDistanceX)
        val absY = abs(filteredDistanceY)

        // Suppress minor perpendicular motion near either axis while preserving
        // deliberate diagonal scrolling.
        if (absX < absY * DirectionLockRatio) {
            filteredDistanceX = 0f
        } else if (absY < absX * DirectionLockRatio) {
            filteredDistanceY = 0f
        }

        accumulatedDistanceX += filteredDistanceX * scrollCoefficient
        accumulatedDistanceY += filteredDistanceY * scrollCoefficient

        if (abs(accumulatedDistanceX) > MinDistanceToSendScroll || abs(accumulatedDistanceY) > MinDistanceToSendScroll) {
            sendScroll(-scrollDirection * accumulatedDistanceX, scrollDirection * accumulatedDistanceY)

            accumulatedDistanceX = 0.0
            accumulatedDistanceY = 0.0
        }

        return true
    }

    override fun onLongPress(e: MotionEvent) {
        if (!doubleTapDragEnabled && !dragging) {
            window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val plugin = KdeConnect.getInstance().getDevicePlugin<MousePadPlugin>(deviceId)
            if (plugin == null) {
                finish()
                return
            }
            plugin.sendSingleHold()
            dragging = true
        }
    }

    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean = false

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        when (singleTapAction) {
            ClickType.LEFT -> sendLeftClick()
            ClickType.RIGHT -> sendRightClick()
            ClickType.MIDDLE -> sendMiddleClick()
            ClickType.NONE -> {}
        }
        return true
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        val plugin = KdeConnect.getInstance().getDevicePlugin<MousePadPlugin>(deviceId)
        if (plugin == null) {
            finish()
            return true
        }
        if (!dragging) {
            if (doubleTapDragEnabled) {
                maybeDragging = true
            } else {
                plugin.sendDoubleClick()
            }
        }
        return true
    }

    override fun onDoubleTapEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_UP && maybeDragging) {
            // Make sure we pass the event on to the general motion event handler which takes
            // care of ending an eventual drag.
            return false
        }
        return true
    }

    override fun onTripleFingerTap(ev: MotionEvent): Boolean {
        when (tripleTapAction) {
            ClickType.LEFT -> sendLeftClick()
            ClickType.RIGHT -> sendRightClick()
            ClickType.MIDDLE -> sendMiddleClick()
            ClickType.NONE -> {}
        }
        return true
    }

    override fun onDoubleFingerTap(ev: MotionEvent): Boolean {
        when (doubleTapAction) {
            ClickType.LEFT -> sendLeftClick()
            ClickType.RIGHT -> sendRightClick()
            ClickType.MIDDLE -> sendMiddleClick()
            ClickType.NONE -> {}
        }
        return true
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        prefsApplied = false
    }

    override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
        val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
        keyboardShown = imeInsets.bottom != 0 || imeInsets.top != 0 ||
            imeInsets.left != 0 || imeInsets.right != 0
        return ViewCompat.onApplyWindowInsets(v, insets)
    }

    private fun sendLeftClick() {
        val plugin = KdeConnect.getInstance().getDevicePlugin<MousePadPlugin>(deviceId)
        if (plugin == null) {
            finish()
            return
        }
        if (dragging) {
            plugin.sendSingleRelease()
            dragging = false
        } else {
            plugin.sendLeftClick()
        }
    }

    private fun sendMiddleClick() {
        val plugin = KdeConnect.getInstance().getDevicePlugin<MousePadPlugin>(deviceId)
        if (plugin == null) {
            finish()
            return
        }
        plugin.sendMiddleClick()
    }

    private fun sendRightClick() {
        val plugin = KdeConnect.getInstance().getDevicePlugin<MousePadPlugin>(deviceId)
        if (plugin == null) {
            finish()
            return
        }
        plugin.sendRightClick()
    }

    private fun sendScroll(x: Double, y: Double) {
        val plugin = KdeConnect.getInstance().getDevicePlugin<MousePadPlugin>(deviceId)
        if (plugin == null) {
            finish()
            return
        }
        plugin.sendScroll(x, y)
    }

    private fun toggleKeyboard() {
        if (keyboardShown) {
            hideKeyboard()
        } else {
            showKeyboard()
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService<InputMethodManager>()
        if (::keyListenerView.isInitialized) {
            imm?.hideSoftInputFromWindow(keyListenerView.windowToken, 0)
        }
    }

    private fun showKeyboard() {
        val imm = getSystemService<InputMethodManager>()
        if (::keyListenerView.isInitialized) {
            keyListenerView.requestFocus()
            imm?.showSoftInput(keyListenerView, 0)
        }
    }

    private fun showCompose() {
        val intent = Intent(this, ComposeSendActivity::class.java)
        intent.putExtra(ComposeSendActivity.EXTRA_DEVICE_ID, deviceId)
        startActivity(intent)
    }

    private fun applyPrefs() {
        if (prefsApplied) return
        if (!::prefs.isInitialized) return

        val p = prefs

        if (p.getBoolean(getString(R.string.mousepad_scroll_direction), false)) {
            scrollDirection = -1
        } else {
            scrollDirection = 1
        }

        var scrollSensitivity = p.getInt(getString(R.string.mousepad_scroll_sensitivity), 100)
        if (scrollSensitivity == 0) scrollSensitivity = 1
        scrollCoefficient = (scrollSensitivity / 100f).toDouble().pow(1.5)

        allowGyro = isGyroSensorAvailable() && p.getBoolean(getString(R.string.gyro_mouse_enabled), false)
        if (allowGyro) gyroscopeSensitivity = p.getInt(getString(R.string.gyro_mouse_sensitivity), 100)

        val singleTapSetting = p.getString(
            getString(R.string.mousepad_single_tap_key),
            getString(R.string.mousepad_default_single),
        )
        val doubleTapSetting = p.getString(
            getString(R.string.mousepad_double_tap_key),
            getString(R.string.mousepad_default_double),
        )
        val tripleTapSetting = p.getString(
            getString(R.string.mousepad_triple_tap_key),
            getString(R.string.mousepad_default_triple),
        )
        val sensitivitySetting = p.getString(
            getString(R.string.mousepad_sensitivity_key),
            getString(R.string.mousepad_default_sensitivity),
        )

        val accelerationProfileName = p.getString(
            getString(R.string.mousepad_acceleration_profile_key),
            getString(R.string.mousepad_default_acceleration_profile),
        ) ?: getString(R.string.mousepad_default_acceleration_profile)

        mPointerAccelerationProfile = PointerAccelerationProfileFactory.getProfileWithName(accelerationProfileName)

        singleTapAction = ClickType.fromString(singleTapSetting)
        doubleTapAction = ClickType.fromString(doubleTapSetting)
        tripleTapAction = ClickType.fromString(tripleTapSetting)

        when (sensitivitySetting) {
            "slowest" -> mCurrentSensitivity = 0.2f
            "aboveSlowest" -> mCurrentSensitivity = 0.5f
            "default" -> mCurrentSensitivity = 1.0f
            "aboveDefault" -> mCurrentSensitivity = 1.5f
            "fastest" -> mCurrentSensitivity = 2.0f
            else -> {
                mCurrentSensitivity = 1.0f
                return
            }
        }
        if (p.getBoolean("pref_mousepad_show_keyboard", true)) {
            if (hasWindowFocus()) {
                showKeyboard()
            } else {
                // Defer until the window gains focus, see pendingShowKeyboard.
                pendingShowKeyboard = true
            }
        } else {
            hideKeyboard()
        }

        if (p.getBoolean(getString(R.string.mousepad_mouse_buttons_enabled_pref), true)) {
            binding.mouseButtons.visibility = View.VISIBLE
        } else {
            binding.mouseButtons.visibility = View.GONE
        }

        doubleTapDragEnabled = p.getBoolean(getString(R.string.mousepad_doubletap_drag_enabled_pref), true)

        prefsApplied = true
    }

    private fun isGyroSensorAvailable(): Boolean {
        return mSensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
    }

    override fun onSupportNavigateUp(): Boolean {
        hideKeyboard()
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    companion object {
        const val EXTRA_DEVICE_ID = "deviceId"

        private const val MinDistanceToSendScroll = 2.5f // touch gesture scroll
        private const val DirectionLockRatio = 0.5f
        private const val StandardDpi = 240.0f // = hdpi
        private const val MinDraggingDistance2 = 25.0f // distance squared to move after a double tap to start dragging
    }
}
