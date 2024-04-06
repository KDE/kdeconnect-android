/*
 * SPDX-FileCopyrightText: 2015 Vineet Garg <grg.vineet@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect

import android.util.Log
import org.json.JSONException
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kde.kdeconnect.DeviceInfo.Companion.fromIdentityPacketAndCert
import org.kde.kdeconnect.Helpers.DeviceHelper
import org.kde.kdeconnect.NetworkPacket.Companion.unserialize
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.internal.util.collections.Sets
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import java.security.cert.Certificate

@RunWith(PowerMockRunner::class)
@PrepareForTest(DeviceHelper::class, Log::class)
class NetworkPacketTest {
    @Before
    fun setUp() {
        PowerMockito.mockStatic(DeviceHelper::class.java)
        PowerMockito.`when`(DeviceHelper.getDeviceId(ArgumentMatchers.any())).thenReturn("123")
        PowerMockito.`when`(DeviceHelper.getDeviceType(ArgumentMatchers.any())).thenReturn(DeviceType.PHONE)

        PowerMockito.mockStatic(Log::class.java)
    }

    @Test
    @Throws(JSONException::class)
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
        Assert.assertEquals(np.getJSONArray("body"), np2.getJSONArray("body"))

        val json = "{\"id\":123,\"type\":\"test\",\"body\":{\"testing\":true}}"
        np2 = unserialize(json)
        Assert.assertEquals(np2.id, 123)
        Assert.assertTrue(np2.getBoolean("testing"))
        Assert.assertFalse(np2.getBoolean("not_testing"))
        Assert.assertTrue(np2.getBoolean("not_testing", true))
    }

    @Test
    fun testIdentity() {
        val cert = Mockito.mock(Certificate::class.java)

        val deviceInfo =
            DeviceInfo("myid", cert, "myname", DeviceType.TV, 12, Sets.newSet("ASDFG"), Sets.newSet("QWERTY"))

        val np = deviceInfo.toIdentityPacket()

        Assert.assertEquals(np.getInt("protocolVersion").toLong(), 12)

        val parsed = fromIdentityPacketAndCert(np, cert)

        Assert.assertEquals(parsed.name, deviceInfo.name)
        Assert.assertEquals(parsed.id, deviceInfo.id)
        Assert.assertEquals(parsed.type, deviceInfo.type)
        Assert.assertEquals(parsed.protocolVersion.toLong(), deviceInfo.protocolVersion.toLong())
        Assert.assertEquals(parsed.incomingCapabilities, deviceInfo.incomingCapabilities)
        Assert.assertEquals(parsed.outgoingCapabilities, deviceInfo.outgoingCapabilities)
    }
}
