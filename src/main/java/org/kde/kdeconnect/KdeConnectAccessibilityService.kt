/*
 * SPDX-FileCopyrightText: 2021 SohnyBohny <sohny.bean@streber24.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.content.res.Configuration
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import androidx.core.math.MathUtils.clamp
import org.kde.kdeconnect.plugins.inputdevicesreceiver.InputDevicesReceiverPlugin.Cursor
import org.kde.kdeconnect_tp.R
import java.util.ArrayDeque
import java.util.Deque
import kotlin.math.abs
import kotlin.math.sign

open class KdeConnectAccessibilityService : AccessibilityService() {

    // Currently active window
    var window: AccessibilityNodeInfo? = null

    // Position of the center of the cursor
    private var x = 0
    private var y = 0

    private lateinit var windowManager : WindowManager

    private var cursorView : View? = null
    private val cursorLayout = LayoutParams(
        LayoutParams.WRAP_CONTENT,
        LayoutParams.WRAP_CONTENT,
        LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        LayoutParams.FLAG_DISMISS_KEYGUARD or LayoutParams.FLAG_NOT_FOCUSABLE
                or LayoutParams.FLAG_NOT_TOUCHABLE or LayoutParams.FLAG_FULLSCREEN
                or LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        // allow cursor to move over status bar on devices having a display cutout
        // https://developer.android.com/guide/topics/display-cutout/#render_content_in_short_edge_cutout_areas
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        gravity = Gravity.START or Gravity.TOP
    }

    private val runHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable {
        cursorView?.visibility = View.GONE
        Log.i("KdeConnectAccessibilityService", "Hiding pointer due to inactivity")
    }

    private var swipeStoke: StrokeDescription? = null
    private var scrollSum = 0.0

    private var cursorHalfWidth = 0
    private var cursorHalfHeight = 0

    private var screenWidth = 0
    private var screenHeight = 0

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService<WindowManager>()!!
        instance = this
        Log.i("KdeConnectAccessibilityService", "created")
    }

    override fun onServiceConnected() {
        cursorView = View.inflate(baseContext, R.layout.mouse_receiver_cursor, null).apply {
            // https://developer.android.com/training/system-ui/navigation.html#behind
            systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
            visibility = View.GONE
            // GONE views don't get measured, but we need the size to center the cursor on x/y
            measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            cursorHalfHeight = measuredHeight / 2
            cursorHalfWidth = measuredWidth / 2
        }
        windowManager.addView(cursorView, cursorLayout)

        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay?.getMetrics(displayMetrics)
        x = displayMetrics.widthPixels / 2
        y = displayMetrics.heightPixels / 2

        updateScreenBounds()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateScreenBounds()
    }

    private fun updateScreenBounds() {
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay?.getRealMetrics(displayMetrics)
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
    }

    private fun hideAfter5Seconds() {
        hide(5000)
    }

    fun hide(delayMillis: Int) {
        runHandler.removeCallbacks(hideRunnable)
        runHandler.postDelayed(hideRunnable, delayMillis.toLong())
    }

    fun moveView(dx: Int, dy: Int) {
        x = clamp(x + dx, 0, screenWidth)
        y = clamp(y + dy, 0, screenHeight)

        // Position the cursor view by its top-left corner
        cursorLayout.x = x - cursorHalfWidth
        cursorLayout.y = y - cursorHalfHeight

        // Hack for InputDevicesReceiver
        Cursor.x = x
        Cursor.y = y

        Handler(mainLooper).post {
            try {
                val cursorView = cursorView ?: return@post
                windowManager.updateViewLayout(cursorView, cursorLayout)
                cursorView.visibility = View.VISIBLE
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
            }
        }
    }

    fun move(dx: Int, dy: Int): Boolean {
        val fromX = x
        val fromY = y

        moveView(dx, dy)

        hideAfter5Seconds()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isSwiping()) {
            return continueSwipe(fromX, fromY)
        }

        return true
    }

    fun setPos(x2: Int, y2: Int): Boolean {
        return move(x2 - x, y2 - y)
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    fun click(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isSwiping()) {
            return stopSwipe()
        }

        return click(x, y)
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    fun click(x: Int, y: Int): Boolean {
        return dispatchGesture(createClick(x, y, 1 /*ms*/), null, null)
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    fun longClick(): Boolean {
        return dispatchGesture(
            createClick(
                x, y,
                ViewConfiguration.getLongPressTimeout()
            ), null, null
        )
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    fun longClickSwipe(): Boolean {
        return if (isSwiping()) {
            stopSwipe()
        } else {
            startSwipe()
        }
    }

    private fun isSwiping(): Boolean {
        return swipeStoke != null
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun startSwipe(): Boolean {
        assert(swipeStoke == null)
        val path = Path()
        path.moveTo(x.toFloat(), y.toFloat())
        val stroke = StrokeDescription(path, 0, 1, true)
        swipeStoke = stroke
        val builder = GestureDescription.Builder()
        builder.addStroke(stroke)
        (cursorView?.findViewById<View>(R.id.mouse_cursor) as? ImageView)?.setImageResource(R.drawable.mouse_pointer_clicked)
        return dispatchGesture(builder.build(), null, null)
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun continueSwipe(fromX: Int, fromY: Int): Boolean {
        val currentStroke = swipeStoke ?: return false
        val path = Path()
        path.moveTo(fromX.toFloat(), fromY.toFloat())
        path.lineTo(x.toFloat(), y.toFloat())
        val nextStroke = currentStroke.continueStroke(path, 0, 5, true)
        swipeStoke = nextStroke
        val builder = GestureDescription.Builder()
        builder.addStroke(nextStroke)
        return dispatchGesture(builder.build(), null, null)
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    fun stopSwipe(): Boolean {
        val path = Path()
        path.moveTo(x.toFloat(), y.toFloat())
        val stroke = swipeStoke ?: return true
        val finalStroke = stroke.continueStroke(path, 0, 1, false)
        val builder = GestureDescription.Builder()
        builder.addStroke(finalStroke)
        swipeStoke = null
        (cursorView?.findViewById<View>(R.id.mouse_cursor) as? ImageView)?.setImageResource(R.drawable.mouse_pointer)
        return dispatchGesture(builder.build(), null, null)
    }

    fun scroll(dx: Int, dy: Int): Boolean {
        scrollSum += dy.toDouble()
        if (sign(dy.toDouble()) != sign(scrollSum)) scrollSum = dy.toDouble()
        if (abs(scrollSum) < 500) return false
        scrollSum = 0.0

        val scrollable = findNodeByAction(
            rootInActiveWindow,
            if (dy > 0) AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD
            else AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD
        ) ?: return false

        return scrollable.performAction(
            if (dy > 0) AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD.id
            else AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD.id
        )
    }

    // https://codelabs.developers.google.com/codelabs/developing-android-a11y-service/#6
    private fun findNodeByAction(
        root: AccessibilityNodeInfo?,
        action: AccessibilityNodeInfo.AccessibilityAction
    ): AccessibilityNodeInfo? {
        if (root == null) return null
        val deque: Deque<AccessibilityNodeInfo> = ArrayDeque()
        deque.add(root)
        while (!deque.isEmpty()) {
            val node = deque.removeFirst()
            if (node.actionList.contains(action)) {
                return node
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    deque.addLast(child)
                }
            }
        }
        return null
    }

    override fun onDestroy() {
        window = null
        cursorView?.let {
            windowManager.removeView(it)
        }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(ignored: AccessibilityEvent?) {
        window = rootInActiveWindow
    }

    override fun onInterrupt() { }

    companion object {
        @JvmField
        var instance: KdeConnectAccessibilityService? = null

        @RequiresApi(api = Build.VERSION_CODES.N)
        private fun createClick(x: Int, y: Int, duration: Int): GestureDescription {
            val clickPath = Path()
            clickPath.moveTo(x.toFloat(), y.toFloat())
            val clickStroke = StrokeDescription(clickPath, 0, duration.toLong())
            val clickBuilder = GestureDescription.Builder()
            clickBuilder.addStroke(clickStroke)
            return clickBuilder.build()
        }
    }
}
