/*
 * SPDX-FileCopyrightText: 2021 Art Pinch <leonardo90690@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Helpers

const val DEFAULT_MAX_VOLUME = 100
const val DEFAULT_VOLUME_STEP = 5

fun calculateNewVolume(currentVolume: Int, maxVolume: Int, stepPercent: Int): Int {

    val adjustedStepPercent = stepPercent.coerceIn(-100, 100)

    val step = maxVolume * adjustedStepPercent / 100

    val newVolume = currentVolume + step

    return newVolume.coerceIn(0, maxVolume)
}