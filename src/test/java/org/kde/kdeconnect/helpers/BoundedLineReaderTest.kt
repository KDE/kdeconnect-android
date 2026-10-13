/*
 * SPDX-FileCopyrightText: 2026 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.helpers

import org.junit.Assert
import org.junit.Test
import java.io.ByteArrayInputStream

class BoundedLineReaderTest {

    private fun stream(s: String) = ByteArrayInputStream(s.toByteArray())

    @Test
    fun testReadsLine() {
        Assert.assertEquals("hello\n", readLineBounded(stream("hello\nworld\n"), 10))
    }

    @Test
    fun testLineTooLong() {
        Assert.assertThrows(LineTooLongException::class.java) { readLineBounded(stream("hello world\n"), 5) }
    }
}
