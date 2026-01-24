/*
 * SPDX-FileCopyrightText: 2021 Daniel Weigl <DanielWeigl@gmx.at>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.helpers

class SafeTextChecker {
    private val safeChars: String
    private val maxLength: Int

    constructor(safeChars: String, maxLength: Int) {
        this.safeChars = safeChars
        this.maxLength = maxLength
    }

    // is used by the SendKeystrokes functionality to evaluate if a to-be-send text is safe for
    // sending without user confirmation
    // only allow sending text that can not harm any connected desktop (like "format c:\n" / "rm -rf\n",...)
    fun isSafe(content: String?): Boolean {
        return content != null &&
               content.length <= maxLength &&
               content.toCharArray().all { c -> c in safeChars }
    }
}
