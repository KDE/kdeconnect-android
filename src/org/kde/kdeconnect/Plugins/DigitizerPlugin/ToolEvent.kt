/*
 * SPDX-FileCopyrightText: 2025 Martin Sh <hemisputnik@proton.me>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.DigitizerPlugin

data class ToolEvent(
    val active: Boolean? = null,
    val touching: Boolean? = null,
    val tool: Tool? = null,
    val x: Int? = null,
    val y: Int? = null,
    val pressure: Double? = null
) {
    enum class Tool {
        Pen,
        Rubber,
    }
}
