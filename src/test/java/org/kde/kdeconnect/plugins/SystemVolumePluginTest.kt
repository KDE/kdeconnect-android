package org.kde.kdeconnect.plugins

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.plugins.systemvolume.SystemVolumePlugin

@RunWith(AndroidJUnit4::class)
class SystemVolumePluginTest {
    private lateinit var systemVolumePlugin: SystemVolumePlugin
    private lateinit var context: Context
    private lateinit var device: Device
    private var packet: NetworkPacket? = null

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Application>()
        device = mockk {
            every { sendPacket(any()) } answers {
                val sentPacket = arg<NetworkPacket>(0)
                packet = sentPacket
            }
        }
        systemVolumePlugin = SystemVolumePlugin().apply {
            setContext(context, device)
        }
    }

    @After
    fun cleanup() {
        packet = null // Ensuring we clean up any captured packets
    }

    // LOCAL -> REMOTE

    @Test
    fun testSendVolume() {
        systemVolumePlugin.sendVolume("Sink 1", 85)
        val sentPacket = checkNotNull(packet)

        assertEquals("kdeconnect.systemvolume.request", sentPacket.type)
        assertEquals(85, sentPacket.getInt("volume"))
        assertEquals("Sink 1", sentPacket.getString("name"))
    }

    @Test
    fun testSendMute() {
        systemVolumePlugin.sendMute("Sink 1", true)
        val sentPacket = checkNotNull(packet)

        assertEquals("kdeconnect.systemvolume.request", sentPacket.type)
        assertTrue(sentPacket.getBoolean("muted"))
        assertEquals("Sink 1", sentPacket.getString("name"))
    }

    @Test
    fun testSendEnable() {
        systemVolumePlugin.sendEnable("Sink 1")
        val sentPacket = checkNotNull(packet)

        assertEquals("kdeconnect.systemvolume.request", sentPacket.type)
        assertEquals(true, sentPacket.getBoolean("enabled"))
        assertEquals("Sink 1", sentPacket.getString("name"))
    }

    // REMOTE -> LOCAL

    @Test
    fun testSinkListeners() {
        val listener = mockk<SystemVolumePlugin.SinkListener>(relaxed = true)

        systemVolumePlugin.addSinkListener(listener)
        systemVolumePlugin.addSinkListener(listener)

        // Simulate receiving a packet to trigger listener
        val sinkPacket = NetworkPacket("kdeconnect.systemvolume").apply {
            set("sinkList", listOf())
        }

        systemVolumePlugin.onPacketReceived(sinkPacket)

        // Verify listener called
        verify(exactly = 2) { listener.sinksChanged() }
    }

    @Test
    fun testReceiveSinkList() {
        // Simulate receiving a packet with sink list
        val sinkPacket = NetworkPacket("kdeconnect.systemvolume").apply {
            set("sinkList", JSONArray().apply {
                put(JSONObject().apply {
                    put("name", "Sink 1")
                    put("volume", 50)
                    put("muted", false)
                    put("description", "")
                    put("maxVolume", 100)
                })
                put(JSONObject().apply {
                    put("name", "Sink 2")
                    put("volume", 70)
                    put("muted", true)
                    put("description", "")
                    put("maxVolume", 100)
                })
            })
        }

        assertTrue(systemVolumePlugin.onPacketReceived(sinkPacket))

        val sinks = systemVolumePlugin.sinks
        assertEquals(2, sinks.size)

        val sink1 = sinks.first { it.name == "Sink 1" }
        assertEquals(50, sink1.volume)
        assertTrue(!sink1.isMute())

        val sink2 = sinks.first { it.name == "Sink 2" }
        assertEquals(70, sink2.volume)
        assertTrue(sink2.isMute())
    }

    @Test
    fun testReceiveSinkUpdate() {
        // First, add a sink to ensure proper updates
        val sinkPacket = NetworkPacket("kdeconnect.systemvolume").apply {
            set("sinkList", JSONArray().apply {
                put(JSONObject().apply {
                    put("name", "Sink 1")
                    put("volume", 30)
                    put("muted", false)
                    put("description", "")
                    put("maxVolume", 100)
                })
            })
        }

        systemVolumePlugin.onPacketReceived(sinkPacket)

        // Update the sink's volume and mute status
        val updatePacket = NetworkPacket("kdeconnect.systemvolume").apply {
            set("name", "Sink 1")
            set("volume", 40)
            set("muted", true)
        }

        assertTrue(systemVolumePlugin.onPacketReceived(updatePacket))

        val sinks = systemVolumePlugin.sinks
        val updatedSink = sinks.first { it.name == "Sink 1" }

        assertEquals(40, updatedSink.volume)
        assertEquals(true, updatedSink.isMute())
        assertTrue(updatedSink.isMute())
    }
}