/*
 * SPDX-FileCopyrightText: 2018 Chansol Yang <CosmicSubspace@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins.mousepad

/* Base class for a pointer acceleration profile. */
abstract class PointerAccelerationProfile {
    /* Class representing a mouse delta, a pair of floats.*/
    class MouseDelta(@JvmField var x: Float = 0f, @JvmField var y: Float = 0f)

    /* Touch coordinate deltas are fed through this method. */
    abstract fun touchMoved(deltaX: Float, deltaY: Float, eventTime: Long)

    /* An acceleration profile should 'commit' the processed delta when this method is called.
     * The value returned here will be directly sent to the desktop client.
     *
     * A MouseDelta object will be provided by the caller to reduce object allocations.*/
    abstract fun commitAcceleratedMouseDelta(reusedObject: MouseDelta): MouseDelta
}
