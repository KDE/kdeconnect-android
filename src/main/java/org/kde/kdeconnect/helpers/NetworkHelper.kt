/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.helpers

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException

fun getLocalIpAddress(): InetAddress? {
    var ip6: InetAddress? = null
    try {
        for (intf in NetworkInterface.getNetworkInterfaces()) {
            // Anything with rmnet is related to cellular connections or USB tethering mechanisms. See:
            //
            // https://android.googlesource.com/kernel/msm/+/android-msm-flo-3.4-kitkat-mr1/Documentation/usb/gadget_rmnet.txt
            //
            // If we run across an interface that has this, we can safely ignore it.
            // In fact, it's much safer to do. If we don't, we might get invalid IP addresses out of it.
            if ("rmnet" in intf.displayName) {
                continue
            }
            for (inetAddress in intf.inetAddresses.iterator()) {
                if (!inetAddress.isLoopbackAddress) {
                    // Prefer IPv4 over IPv6, because sshfs doesn't seem to like IPv6
                    ip6 = if (inetAddress is Inet4Address) { return inetAddress } else { inetAddress }
                }
            }
        }
    } catch (_: SocketException) { }
    return ip6
}
