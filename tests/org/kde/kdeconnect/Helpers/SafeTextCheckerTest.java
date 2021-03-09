/*
 * SPDX-FileCopyrightText: 2021 Daniel Weigl <DanielWeigl@gmx.at>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Helpers;

import junit.framework.TestCase;

public class SafeTextCheckerTest extends TestCase {

    public void testSafeTextChecker() {
        SafeTextChecker safeTextChecker = new SafeTextChecker("1234567890", 8);
        assertIsOkay("123456", safeTextChecker);
        assertIsOkay("123", safeTextChecker);
        assertIsOkay("12345678", safeTextChecker);
        assertIsOkay("", safeTextChecker);

        assertIsNotOkay(null, safeTextChecker);
        assertIsNotOkay("123456789", safeTextChecker);
        assertIsNotOkay("123o", safeTextChecker);
        assertIsNotOkay("O123", safeTextChecker);  // its a O not a 0
        assertIsNotOkay("o", safeTextChecker);
        assertIsNotOkay(" ", safeTextChecker);
        assertIsNotOkay("12345678 ", safeTextChecker);
    }

    private void assertIsOkay(String text, SafeTextChecker stc) {
        assertTrue(text + " should be okay", stc.isSafe(text));
    }

    private void assertIsNotOkay(String text, SafeTextChecker stc) {
        assertFalse(text + " should not be okay", stc.isSafe(text));
    }
}