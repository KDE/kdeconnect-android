/*
 * SPDX-FileCopyrightText: 2025 Martin Sh <hemisputnik@proton.me>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.DigitizerPlugin

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import com.google.android.material.R
import com.google.android.material.color.MaterialColors
import kotlin.math.ceil
import kotlin.math.roundToInt

class DrawingPadView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private fun mmToPixels(mm: Float): Float =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_MM,
            mm,
            resources.displayMetrics
        )

    val gridSpacing = mmToPixels(5.0f)
    val gridOffset = mmToPixels(2.5f)

    val backgroundPaint = Paint().apply {
        color = MaterialColors.getColor(this@DrawingPadView, R.attr.colorSurfaceContainer, Color.WHITE)
        style = Paint.Style.FILL
    }

    val linePaint = Paint().apply {
        color = MaterialColors.getColor(this@DrawingPadView, R.attr.colorOutlineVariant, Color.WHITE)
        style = Paint.Style.STROKE
        strokeWidth = 1.0f
    }

    var eventListener: EventListener? = null
    var fingerTouchEventsEnabled: Boolean = false

    private fun convertTool(motionEventTool: Int): ToolEvent.Tool =
        when (motionEventTool) {
            MotionEvent.TOOL_TYPE_ERASER -> ToolEvent.Tool.Rubber
            else -> ToolEvent.Tool.Pen
        }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_HOVER_ENTER -> eventListener?.onToolEvent(
                ToolEvent(
                    active = true,
                    x = event.x.roundToInt(),
                    y = event.y.roundToInt()
                )
            )

            MotionEvent.ACTION_HOVER_EXIT -> eventListener?.onToolEvent(
                ToolEvent(
                    active = false,
                )
            )

            MotionEvent.ACTION_HOVER_MOVE -> eventListener?.onToolEvent(
                ToolEvent(
                    tool = convertTool(event.getToolType(0)),
                    x = event.x.roundToInt(),
                    y = event.y.roundToInt()
                )
            )
        }
        return true
    }

    private fun findPressure(event: MotionEvent): Double =
        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER)
            // We report constant 1.0 pressure for fingers, since most devices can't report
            // pressure for fingers (so they report a constant 0.001).
            if (fingerTouchEventsEnabled) 1.0
            else 0.0
        else
            event.pressure.toDouble()

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val toolType = event.getToolType(0)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                eventListener?.onToolEvent(
                    ToolEvent(
                        active = true,
                        touching = toolType != MotionEvent.TOOL_TYPE_FINGER,
                        tool = convertTool(toolType),
                        x = event.x.roundToInt(),
                        y = event.y.roundToInt(),
                        pressure = findPressure(event)
                    )
                )

                if (toolType == MotionEvent.TOOL_TYPE_FINGER)
                    eventListener?.onFingerTouchEvent(true)
            }

            MotionEvent.ACTION_UP -> {
                eventListener?.onToolEvent(
                    ToolEvent(
                        // If the finger is lifted from the screen,
                        // we consider the device as "not tracking the tool".
                        active = toolType != MotionEvent.TOOL_TYPE_FINGER,
                        touching = false,
                    )
                )

                if (toolType == MotionEvent.TOOL_TYPE_FINGER)
                    eventListener?.onFingerTouchEvent(false)

                // Set this variable to `false` if the user stopped drawing without first letting go
                // of the "draw" button.
                fingerTouchEventsEnabled = false
            }

            MotionEvent.ACTION_MOVE -> eventListener?.onToolEvent(
                ToolEvent(
                    tool = convertTool(toolType),
                    x = event.x.roundToInt(),
                    y = event.y.roundToInt(),
                    pressure = findPressure(event)
                )
            )
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPaint(backgroundPaint)

        for (i in 0..<ceil(width / gridSpacing).toInt()) {
            val x = gridOffset + i * gridSpacing
            canvas.drawLine(x, 0f, x, height.toFloat(), linePaint)
        }

        for (i in 0..<ceil(height / gridSpacing).toInt()) {
            val y = gridOffset + i * gridSpacing
            canvas.drawLine(0f, y, width.toFloat(), y, linePaint)
        }
    }

    interface EventListener {
        fun onToolEvent(event: ToolEvent)
        fun onFingerTouchEvent(touching: Boolean)
    }

    companion object {
        const val TAG = "DrawingPadView"
    }
}
