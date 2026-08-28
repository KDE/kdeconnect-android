/*
 * SPDX-FileCopyrightText: 2026 Johann Specht <sajeg.dev@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.remotekeyboard

enum class SpecialKeys(val value: Int) {
    NO_KEY(0),
    DEL(1),
    TAB(2),
    DPAD_LEFT(4),
    DPAD_UP(5),
    DPAD_RIGHT(6),
    DPAD_DOWN(7),
    PAGE_UP(8),
    PAGE_DOWN(9),
    MOVE_HOME(10),
    MOVE_END(11),
    ENTER(12),
    FORWARD_DEL(13),
    ESCAPE(14),
    SYSRQ(15),
    SCROLL_LOCK(16);

    companion object {
        fun fromInt(value: Int): SpecialKeys {
            return try {
                entries.first { it.value == value }
            } catch (_: NoSuchElementException) {
                NO_KEY
            }
        }
    }
}

enum class Movement {
    LINE,
    WORD,
    CHARACTER;
}