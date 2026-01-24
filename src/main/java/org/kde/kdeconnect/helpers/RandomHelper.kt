/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.helpers

import java.security.SecureRandom

object RandomHelper {
    @JvmField
    val secureRandom: SecureRandom = SecureRandom()

    private val symbols = (('A'..'Z').toList() + ('a'..'z').toList() + ('0'..'9').toList()).toCharArray()
    fun randomString(length: Int): String {
        val buffer = CharArray(length)
        for (i in 0 until length) {
            buffer[i] = symbols[secureRandom.nextInt(symbols.size)]
        }
        return String(buffer)
    }
}
