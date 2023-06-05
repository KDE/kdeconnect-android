/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Helpers;


import java.security.SecureRandom;

public class RandomHelper {
    public static final SecureRandom secureRandom = new SecureRandom();

    private static final char[] symbols = ("ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
            "abcdefghijklmnopqrstuvwxyz" +
            "1234567890").toCharArray();


    public static String randomString(int length) {
        char[] buffer = new char[length];
        for (int idx = 0; idx < length; ++idx) {
            buffer[idx] = symbols[secureRandom.nextInt(symbols.length)];
        }
        return new String(buffer);
    }

}
