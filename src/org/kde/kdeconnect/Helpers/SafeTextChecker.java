/*
 * SPDX-FileCopyrightText: 2021 Daniel Weigl <DanielWeigl@gmx.at>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Helpers;

public class SafeTextChecker {

    private final String safeChars;
    private final Integer maxLength;

    public SafeTextChecker(String safeChars, Integer maxLength) {
        this.safeChars = safeChars;
        this.maxLength = maxLength;
    }


    // is used by the SendKeystrokes functionality to evaluate if a to-be-send text is safe for
    // sending without user confirmation
    // only allow sending text that can not harm any connected desktop (like "format c:\n" / "rm -rf\n",...)
    public boolean isSafe(String content) {
        if (content == null) {
            return false;
        }

        if (content.length() > maxLength) {
            return false;
        }

        for (int i = 0; i < content.length(); i++) {
            String charAtPos = content.substring(i, i + 1);
            if (!safeChars.contains(charAtPos)) {
                return false;
            }
        }

        // we are happy with the string
        return true;
    }
}
