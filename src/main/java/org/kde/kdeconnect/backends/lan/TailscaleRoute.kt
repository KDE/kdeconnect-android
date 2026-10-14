/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.backends.lan

import java.net.InetAddress

/** Identifies configured Tailscale peers using their documented CGNAT or ULA address ranges. */
internal object TailscaleRoute {
    fun isTailscaleAddress(address: InetAddress): Boolean {
        val bytes = address.address
        return when (bytes.size) {
            4 -> (bytes[0].toInt() and 0xff) == 100 &&
                (bytes[1].toInt() and 0xff) in 64..127
            16 -> (bytes[0].toInt() and 0xff) == 0xfd &&
                bytes[1] == 0x7a.toByte() && bytes[2] == 0x11.toByte() &&
                bytes[3] == 0x5c.toByte() && bytes[4] == 0xa1.toByte() && bytes[5] == 0xe0.toByte()
            else -> false
        }
    }
}
