/*
 * SPDX-FileCopyrightText: 2015 Vineet Garg <grg.vineet@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect

import com.google.gson.JsonParseException
import io.mockk.mockk
import org.junit.Assert
import org.junit.Test
import org.kde.kdeconnect.NetworkPacket.Companion.unserialize
import java.security.cert.Certificate

class NetworkPacketTest {

    @Test
    fun testNetworkPacket() {
        val np = NetworkPacket("com.test")

        np["hello"] = "hola"
        Assert.assertEquals(np.getString("hello", "bye"), "hola")

        np["hello"] = ""
        Assert.assertEquals(np.getString("hello", "bye"), "")

        Assert.assertEquals(np.getString("hi", "bye"), "bye")

        np["foo"] = "bar"
        val serialized = np.serialize()
        var np2 = unserialize(serialized)

        Assert.assertEquals(np.getLong("id"), np2.getLong("id"))
        Assert.assertEquals(np.getString("type"), np2.getString("type"))
        Assert.assertEquals(np.getJsonArray("body"), np2.getJsonArray("body"))

        val json = "{\"type\":\"test\",\"body\":{\"testing\":true}}"
        np2 = unserialize(json)
        Assert.assertTrue(np2.getBoolean("testing"))
        Assert.assertFalse(np2.getBoolean("not_testing"))
        Assert.assertTrue(np2.getBoolean("not_testing", true))
    }

    @Test
    fun testMalformedPacketsThrowJsonParseException() {
        val inputs = listOf(
            "",
            "[]",
            "{\"type\":\"test\",\"body\":{\"a\":\"\\uZZZZ\"}}",
            "{\"body\":{}}",
            "{\"type\":\"test\"}",
            "{\"type\":\"test\",\"body\":[]}",
            "{\"type\":\"test\",\"body\":{},\"payloadSize\":10}",
            "{\"type\":\"test\",\"body\":{},\"payloadSize\":\"abc\",\"payloadTransferInfo\":{}}",
        )
        for (json in inputs) {
            Assert.assertThrows(json, JsonParseException::class.java) { unserialize(json) }
        }
    }

    @Test
    fun testWrongTypesReturnDefaults() {
        val np = unserialize("{\"type\":\"test\",\"body\":{\"a\":{},\"b\":\"x\",\"c\":null,\"d\":[1]}}")
        Assert.assertEquals(-1, np.getInt("a"))
        Assert.assertNull(np.getIntOrNull("b"))
        Assert.assertNull(np.getStringOrNull("c"))
        Assert.assertEquals("default", np.getString("c", "default"))
        Assert.assertNull(np.getJsonObject("d"))
        Assert.assertEquals(1, np.getJsonArray("d")!!.size())
    }

    @Test
    fun testSerializeDoesNotEscapeSlashes() {
        val np = NetworkPacket("com.test")
        np["path"] = "a/b\\/c"
        val serialized = np.serialize()
        Assert.assertTrue(serialized, serialized.contains("\"a/b\\\\/c\""))
        Assert.assertEquals("a/b\\/c", unserialize(serialized).getString("path"))
    }

    @Test
    fun testIdentity() {
        val cert = mockk<Certificate>()
        val deviceInfo = DeviceInfo("myid", cert, "myname", DeviceType.TV, 12, setOf("ASDFG"), setOf("QWERTY"))

        val np = deviceInfo.toIdentityPacket()

        Assert.assertEquals(np.getInt("protocolVersion").toLong(), 12)

        val parsed = DeviceInfo.fromIdentityPacketAndCert(np, cert)

        Assert.assertEquals(parsed.name, deviceInfo.name)
        Assert.assertEquals(parsed.id, deviceInfo.id)
        Assert.assertEquals(parsed.type, deviceInfo.type)
        Assert.assertEquals(parsed.protocolVersion.toLong(), deviceInfo.protocolVersion.toLong())
        Assert.assertEquals(parsed.incomingCapabilities, deviceInfo.incomingCapabilities)
        Assert.assertEquals(parsed.outgoingCapabilities, deviceInfo.outgoingCapabilities)
    }
}
