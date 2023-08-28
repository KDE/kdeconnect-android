/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.Helpers

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException

object NetworkHelper {
    //Prefer IPv4 over IPv6, because sshfs doesn't seem to like IPv6
    // Anything with rmnet is related to cellular connections or USB
    // tethering mechanisms.  See:
    //
    // https://android.googlesource.com/kernel/msm/+/android-msm-flo-3.4-kitkat-mr1/Documentation/usb/gadget_rmnet.txt
    //
    // If we run across an interface that has this, we can safely
    // ignore it.  In fact, it's much safer to do.  If we don't, we
    // might get invalid IP adddresses out of it.
    @JvmStatic
    val localIpAddress: InetAddress?
        get() {
            var ip6: InetAddress? = null
            try {
                for (intf in NetworkInterface.getNetworkInterfaces()) {

                    // Anything with rmnet is related to cellular connections or USB
                    // tethering mechanisms.  See:
                    //
                    // https://android.googlesource.com/kernel/msm/+/android-msm-flo-3.4-kitkat-mr1/Documentation/usb/gadget_rmnet.txt
                    //
                    // If we run across an interface that has this, we can safely
                    // ignore it.  In fact, it's much safer to do.  If we don't, we
                    // might get invalid IP adddresses out of it.
                    if (intf.displayName.contains("rmnet")) {
                        continue
                    }
                    val enumIpAddr = intf.inetAddresses
                    while (enumIpAddr.hasMoreElements()) {
                        val inetAddress = enumIpAddr.nextElement()
                        if (!inetAddress.isLoopbackAddress) {
                            ip6 =
                                if (inetAddress is Inet4Address) { //Prefer IPv4 over IPv6, because sshfs doesn't seem to like IPv6
                                    return inetAddress
                                } else {
                                    inetAddress
                                }
                        }
                    }
                }
            } catch (ignored: SocketException) {
            }
            return ip6
        }
}
