/*
 * SPDX-FileCopyrightText: 2024 TPJ Schikhof <kde@schikhof.eu>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.helpers

import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test
import java.net.InetAddress


internal class NetworkHelperTest {
    @Test
    fun testCGNATLowerBound() {
        val address = InetAddress.getByName("100.64.0.0")
        assertTrue(address.isCGNAT)
        assertFalse(address.isUniqueLocal)
        assertTrue(isPrivateAddress(address))
    }

    @Test
    @Throws(Exception::class)
    fun testCGNATUpperBound() {
        val address = InetAddress.getByName("100.127.255.255")
        assertTrue(address.isCGNAT)
        assertFalse(address.isUniqueLocal)
        assertTrue(isPrivateAddress(address))
    }

    @Test
    @Throws(Exception::class)
    fun testInsideRange() {
        val address = InetAddress.getByName("100.100.42.1")
        assertTrue(address.isCGNAT)
        assertFalse(address.isUniqueLocal)
        assertTrue(isPrivateAddress(address))
    }

    @Test
    @Throws(Exception::class)
    fun testBelowRange() {
        val address = InetAddress.getByName("100.63.255.255")
        assertFalse(address.isCGNAT)
        assertFalse(address.isUniqueLocal)
        assertFalse(isPrivateAddress(address))
    }

    @Test
    @Throws(Exception::class)
    fun testAboveRange() {
        val address = InetAddress.getByName("100.128.0.0")
        assertFalse(address.isCGNAT)
        assertFalse(address.isUniqueLocal)
        assertFalse(isPrivateAddress(address))
    }

    @Test
    @Throws(Exception::class)
    fun testDifferentPublicRange() {
        val address = InetAddress.getByName("189.168.187.10")
        assertFalse(address.isCGNAT)
        assertFalse(address.isUniqueLocal)
        assertFalse(isPrivateAddress(address))
    }

    @Test
    @Throws(Exception::class)
    fun testPrivateRange() {
        val address = InetAddress.getByName("192.168.1.1")
        assertFalse(address.isCGNAT)
        assertFalse(address.isUniqueLocal)
        assertTrue(isPrivateAddress(address))
    }

    @Test
    @Throws(Exception::class)
    fun testPrivateRange2() {
        val address = InetAddress.getByName("10.0.0.1")
        assertFalse(address.isCGNAT)
        assertFalse(address.isUniqueLocal)
        assertTrue(isPrivateAddress(address))
    }

    @Test
    @Throws(Exception::class)
    fun testPrivateRange3() {
        val address = InetAddress.getByName("172.16.0.1")
        assertFalse(address.isCGNAT)
        assertFalse(address.isUniqueLocal)

        assertTrue(isPrivateAddress(address))
    }

    @Test
    @Throws(Exception::class)
    fun testIpv6Address() {
        val address = InetAddress.getByName("2001:db8::1")
        assertFalse(address.isCGNAT)
        assertFalse(address.isUniqueLocal)
        assertFalse(isPrivateAddress(address))
    }

    @Test
    @Throws(Exception::class)
    fun testIpv6LinkLocalAddress() {
        val address = InetAddress.getByName("fe80::1")
        assertFalse(address.isCGNAT)
        assertFalse(address.isUniqueLocal)
        assertTrue(isPrivateAddress(address))
    }

    @Test
    @Throws(Exception::class)
    fun testIpv6PrivateAddress() {
        val address = InetAddress.getByName("fd00::1")
        assertFalse(address.isCGNAT)
        assertTrue(address.isUniqueLocal)
        assertTrue(isPrivateAddress(address))
    }
}