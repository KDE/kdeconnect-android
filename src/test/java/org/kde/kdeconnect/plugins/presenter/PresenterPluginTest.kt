package org.kde.kdeconnect.plugins.presenter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket

@RunWith(AndroidJUnit4::class)
class PresenterPluginTest {
    private lateinit var device: Device
    private lateinit var plugin: PresenterPlugin
    private val packets = mutableListOf<NetworkPacket>()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        device = mockk(relaxed = true) {
            every { isReachable } returns true
            every { sendPacket(any<NetworkPacket>()) } answers {
                packets.add(firstArg())
            }
        }
        plugin = PresenterPlugin().also { it.setContext(context, device) }
    }

    @Test
    fun testSendNextSendsPageDown() {
        plugin.sendNext()

        val packet = packets.single()
        assertEquals("kdeconnect.mousepad.request", packet.type)
        assertEquals(9, packet.getInt("specialKey"))
    }

    @Test
    fun testSendPreviousSendsPageUp() {
        plugin.sendPrevious()

        val packet = packets.single()
        assertEquals("kdeconnect.mousepad.request", packet.type)
        assertEquals(8, packet.getInt("specialKey"))
    }

    @Test
    fun testSendFullscreenSendsF5() {
        plugin.sendFullscreen()

        assertEquals(25, packets.single().getInt("specialKey"))
    }

    @Test
    fun testSendEscSendsEscape() {
        plugin.sendEsc()

        assertEquals(14, packets.single().getInt("specialKey"))
    }

    @Test
    fun testZoomKeysUseCtrl() {
        plugin.sendZoomIn()
        plugin.sendZoomOut()

        assertEquals(2, packets.size)
        assertEquals("+", packets[0].getString("key"))
        assertTrue(packets[0].getBoolean("ctrl"))
        assertEquals("-", packets[1].getString("key"))
        assertTrue(packets[1].getBoolean("ctrl"))
    }

    @Test
    fun testInitializeRemoteInputSendsEmptyMousepadRequest() {
        plugin.initializeRemoteInput()

        val packet = packets.single()
        assertEquals("kdeconnect.mousepad.request", packet.type)
        assertFalse(packet.has("specialKey"))
        assertFalse(packet.has("key"))
        assertFalse(packet.has("dx"))
        assertFalse(packet.has("dy"))
    }

    @Test
    fun testWarmUpKeepsFirstSlideKeyFromBeingDropped() {
        // The desktop drops the packet that creates the Wayland portal session.
        var sessionReady = false
        every { device.sendPacket(any<NetworkPacket>()) } answers {
            val packet = firstArg<NetworkPacket>()
            if (sessionReady) {
                packets.add(packet)
            } else {
                sessionReady = true
            }
        }

        plugin.initializeRemoteInput()
        plugin.sendNext()

        assertEquals(9, packets.single().getInt("specialKey"))
    }

    @Test
    fun testKeysAreNotSentWhenDeviceIsUnreachable() {
        every { device.isReachable } returns false

        plugin.initializeRemoteInput()
        plugin.sendNext()
        plugin.sendPrevious()

        assertTrue(packets.isEmpty())
    }
}
