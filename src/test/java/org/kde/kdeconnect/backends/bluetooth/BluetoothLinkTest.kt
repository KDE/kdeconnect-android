/*
 * SPDX-FileCopyrightText: 2026 Martin Vlach <mr.huge@seznam.cz>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.backends.bluetooth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BluetoothLinkTest {

    /**
     * Sends [limit] bytes without a line terminator and then stalls, like a peer
     * that never finishes its packet. Reads fail once the stream is closed.
     */
    private class NoNewlineInputStream(private val limit: Long) : InputStream() {
        @Volatile
        private var closed = false
        private var sent = 0L

        override fun read(): Int {
            val b = ByteArray(1)
            return if (read(b, 0, 1) == -1) -1 else b[0].toInt()
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            while (sent >= limit && !closed) {
                Thread.sleep(10)
            }
            if (closed) throw IOException("Stream closed")
            val n = minOf(len.toLong(), limit - sent).toInt()
            b.fill('a'.code.toByte(), off, off + n)
            sent += n
            return n
        }

        override fun close() {
            closed = true
        }
    }

    @Test
    fun linkIsDroppedWhenPeerSendsAnOversizedPacket() {
        val disconnected = CountDownLatch(1)
        val linkProvider = mockk<BluetoothLinkProvider>(relaxed = true) {
            every { disconnectedLink(any(), any()) } answers { disconnected.countDown() }
        }
        val input = NoNewlineInputStream(limit = 64L * 1024 * 1024)
        val link = BluetoothLink(
            ApplicationProvider.getApplicationContext<Application>(),
            mockk(relaxed = true),
            input,
            ByteArrayOutputStream(),
            mockk(relaxed = true),
            mockk(relaxed = true),
            linkProvider
        )

        try {
            link.startListening()
            assertTrue(
                "BluetoothLink kept buffering a packet without a line terminator",
                disconnected.await(30, TimeUnit.SECONDS)
            )
        } finally {
            input.close()
        }
    }
}
