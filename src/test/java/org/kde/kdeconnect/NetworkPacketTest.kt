/*
 * SPDX-FileCopyrightText: 2015 Vineet Garg <grg.vineet@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect

import io.mockk.mockk
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
    fun testSerializePayload() {
        val np = NetworkPacket("com.test")
        np["path"] = "/a/b"
        np.payload = NetworkPacket.Payload(42)
        np.payloadTransferInfo = buildJsonObject { put("port", 1739) }

        val serialized = np.serialize()
        Assert.assertTrue(serialized.endsWith("\n"))
        Assert.assertTrue(serialized.contains("\"path\":\"/a/b\""))

        val np2 = unserialize(serialized)
        Assert.assertEquals("com.test", np2.type)
        Assert.assertEquals("/a/b", np2.getString("path"))
        Assert.assertEquals(42L, np2.payloadSize)
        Assert.assertEquals(np.payloadTransferInfo, np2.payloadTransferInfo)

        val noPayload = NetworkPacket("com.test").serialize()
        Assert.assertFalse(noPayload.contains("payloadSize"))
        Assert.assertFalse(noPayload.contains("payloadTransferInfo"))
    }

    @Test
    fun testTypeCoercion() {
        val np = unserialize("{\"id\":1,\"type\":\"test\",\"body\":{\"int\":8,\"str\":\"12\",\"bool\":\"true\",\"nil\":null},\"extra\":0}")
        Assert.assertEquals("8", np.getString("int"))
        Assert.assertEquals(12, np.getInt("str"))
        Assert.assertEquals(12L, np.getLong("str"))
        Assert.assertTrue(np.getBoolean("bool"))
        Assert.assertNull(np.getStringOrNull("nil"))
        Assert.assertEquals(-1, np.getInt("missing"))
        Assert.assertFalse(np.hasPayload())
        Assert.assertFalse(np.hasPayloadTransferInfo())
    }

    @Test(expected = SerializationException::class)
    fun testUnserializeInvalid() {
        unserialize("{\"body\":{}}")
    }

    @Test(expected = SerializationException::class)
    fun testUnserializePayloadSizeWithoutTransferInfo() {
        unserialize("{\"id\":1,\"type\":\"test\",\"body\":{},\"payloadSize\":42}")
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
