/*
 * SPDX-FileCopyrightText: 2018 Chansol Yang <CosmicSubspace@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins.mousepad

import kotlin.math.pow
import kotlin.math.sqrt


object PointerAccelerationProfileFactory {
    @JvmStatic
    fun getProfileWithName(name: String): PointerAccelerationProfile = when (name) {
        "weaker" -> PolynomialProfile(0.25f)
        "weak" -> PolynomialProfile(0.5f)
        "medium" -> PolynomialProfile(1.0f)
        "strong" -> PolynomialProfile(1.5f)
        "stronger" -> PolynomialProfile(2.0f)
        "noacceleration" -> DefaultProfile()
        else -> DefaultProfile()
    }

    /* The simplest profile. Merely adds the mouse deltas without any processing. */
    private class DefaultProfile : PointerAccelerationProfile() {
        var accumulatedX: Float = 0.0f
        var accumulatedY: Float = 0.0f

        override fun touchMoved(deltaX: Float, deltaY: Float, eventTime: Long) {
            accumulatedX += deltaX
            accumulatedY += deltaY
        }

        override fun commitAcceleratedMouseDelta(reusedObject: MouseDelta): MouseDelta {
            reusedObject.x = accumulatedX
            reusedObject.y = accumulatedY
            accumulatedY = 0f
            accumulatedX = 0f
            return reusedObject
        }
    }


    /* Base class for acceleration profiles that takes touch movement speed into account.
     * To calculate the speed, a history of 32 touch events are stored
     * and then later used to calculate the speed. */
    private abstract class SpeedBasedAccelerationProfile : PointerAccelerationProfile() {
        var accumulatedX: Float = 0.0f
        var accumulatedY: Float = 0.0f

        // Higher values will reduce the amount of noise in the speed calculation
        // but will also increase latency until the acceleration kicks in.
        // 150ms seemed like a nice middle ground.
        val freshThreshold: Long = 150

        private class TouchDeltaEvent(val x: Float, val y: Float, val time: Long)

        private val touchEventHistory: Array<TouchDeltaEvent?> = arrayOfNulls<TouchDeltaEvent>(32)

        /* add an event to the touchEventHistory array, shifting everything else in the array. */
        fun addHistory(deltaX: Float, deltaY: Float, eventTime: Long) {
            System.arraycopy(touchEventHistory, 0, touchEventHistory, 1, touchEventHistory.size - 1)
            touchEventHistory[0] = TouchDeltaEvent(deltaX, deltaY, eventTime)
        }

        // To calculate the touch movement speed,
        // we iterate through the touchEventHistory array,
        // adding up the distance moved for each event.
        // Breaks if a touchEventHistory entry is too "stale".
        private fun speedFromTouchHistory(eventTime: Long): Pair<Float, Long> {
            var distanceMoved = 0.0f
            var deltaT: Long = 0
            for (aTouchEventHistory in touchEventHistory) {
                if (aTouchEventHistory == null) break
                if (eventTime - aTouchEventHistory.time > freshThreshold) break

                distanceMoved += sqrt((aTouchEventHistory.x * aTouchEventHistory.x + aTouchEventHistory.y * aTouchEventHistory.y).toDouble()).toFloat()
                deltaT = eventTime - aTouchEventHistory.time
            }
            return Pair(distanceMoved, deltaT)
        }

        private fun multiplierFromTouchHistory(eventTime: Long): Float {
            val (distanceMoved: Float, deltaT: Long) = speedFromTouchHistory(eventTime)

            val multiplier: Float = if (deltaT == 0L) {
                0f // Edge case when there are no historical touch data to calculate speed from.
            } else {
                val speed = distanceMoved / (deltaT / 1000.0f) // units: px/sec
                calculateMultiplier(speed)
            }

            return multiplier.coerceAtLeast(0.01f)
        }

        override fun touchMoved(deltaX: Float, deltaY: Float, eventTime: Long) {
            val multiplier: Float = multiplierFromTouchHistory(eventTime)

            accumulatedX += deltaX * multiplier
            accumulatedY += deltaY * multiplier

            addHistory(deltaX, deltaY, eventTime)
        }

        /* Should be overridden by the child class.
         * Given the current touch movement speed, this method should return a multiplier
         * for the touch delta. ( mouse_delta = touch_delta * multiplier ) */
        abstract fun calculateMultiplier(speed: Float): Float

        override fun commitAcceleratedMouseDelta(reusedObject: MouseDelta): MouseDelta {
            /* This makes sure that only the integer components of the deltas are sent,
             * since the coordinates are converted to integers in the desktop client anyway.
             * The leftover fractional part is stored and added later; this makes
             * the cursor move much smoother in slow speeds. */
            reusedObject.x = accumulatedX.toInt().toFloat()
            reusedObject.y = accumulatedY.toInt().toFloat()
            accumulatedY %= 1.0f
            accumulatedX %= 1.0f
            return reusedObject
        }
    }

    /* Pointer acceleration with mouse_delta = touch_delta * ( touch_speed ^ exponent )
     * It is similar to x.org server's Polynomial pointer acceleration profile. */
    private class PolynomialProfile(val exponent: Float) : SpeedBasedAccelerationProfile() {
        override fun calculateMultiplier(speed: Float): Float {
            // The value 600 was chosen arbitrarily.
            return ((speed / 600).toDouble().pow(exponent.toDouble())).toFloat()
        }
    }


    /* Mimics the behavior of xorg's Power profile.
     * Currently not visible to the user since it is rather hard to control. */
    private class PowerProfile : SpeedBasedAccelerationProfile() {
        override fun calculateMultiplier(speed: Float): Float {
            return (2.0.pow((speed / 1000).toDouble())).toFloat() - 1
        }
    }
}
