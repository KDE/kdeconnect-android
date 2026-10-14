/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.backends

import org.kde.kdeconnect.NetworkPacket

/** Shared ordering and packet eligibility rules for the device's available links. */
internal object FallbackLinkManager {
    const val BLUETOOTH_MAX_PACKET_BYTES = 16 * 1024

    fun canSend(link: BaseLink, packet: NetworkPacket): Boolean =
        link.linkProvider.canSendPacket(packet)

    fun preferredFirst(links: Iterable<BaseLink>): List<BaseLink> =
        links.sortedByDescending { it.transport.priority }

    fun hasFastPayloadLink(links: Iterable<BaseLink>, payloadSize: Long): Boolean =
        links.any { it.linkProvider.supportsPayload(payloadSize) }
}
