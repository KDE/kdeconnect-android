/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.backends

import org.kde.kdeconnect_tp.R

/** User-visible transport order. Higher priorities are preferred for new packets and transfers. */
enum class ConnectionTransport(val priority: Int, val label: String, val labelRes: Int) {
    WIFI(500, "Wi-Fi", R.string.wifi),
    WIFI_DIRECT(400, "Wi-Fi Direct", R.string.connection_wifi_direct),
    HOTSPOT(300, "Hotspot", R.string.connection_hotspot),
    BLUETOOTH(200, "Bluetooth", R.string.connection_bluetooth),
    TAILSCALE(100, "Tailscale", R.string.connection_tailscale),
    OTHER(0, "Connection", R.string.connection_generic),
}
