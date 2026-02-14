/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.helpers

import java.net.Inet4Address
import java.net.Inet6Address
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


/**
 * Returns true if the given address is in the Carrier-Grade NAT address space (100.64.0.0/10).
 */
val InetAddress.isCGNAT: Boolean
    get() {
        if (this !is Inet4Address) {
            return false
        }

        val bytes = this.address
        val firstOctet = bytes[0].toInt() and 0xFF
        val secondOctet = bytes[1].toInt() and 0xFF

        // First octet must be 100, and the top two bits of second octet must be 01 (01xx xxxx)
        return firstOctet == 100 && (secondOctet and 0xC0) == 0x40
    }


/**
 * Returns true if the given address is in the IPv6 Unique Local Address space (fc00::/7)
 */
val InetAddress.isUniqueLocal: Boolean
    get() {
        if (this !is Inet6Address) {
            return false
        }

        val bytes = this.address
        // First 7 bits must be 1111 110x
        val firstOctet = bytes[0].toInt() and 0xFF
        return (firstOctet and 0xfe) == 0xfc
    }

/**
 * Returns true if the address is not a public internet address, so it can be used to send and receive KDE Connect packets
 */
fun isPrivateAddress(address: InetAddress): Boolean {
    return address.isSiteLocalAddress || address.isLinkLocalAddress || address.isCGNAT || address.isUniqueLocal
}