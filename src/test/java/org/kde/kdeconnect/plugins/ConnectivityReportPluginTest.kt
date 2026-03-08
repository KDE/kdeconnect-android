package org.kde.kdeconnect.plugins.connectivityreport

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket

@RunWith(AndroidJUnit4::class)
class ConnectivityReportPluginTest {

    private lateinit var plugin: ConnectivityReportPlugin
    private lateinit var context: Context
    private lateinit var device: Device
    private var packet: NetworkPacket? = null

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Application>()

        device = mockk {
            val packetSlot = slot<NetworkPacket>()
            every { sendPacket(capture(packetSlot)) } answers {
                packet = packetSlot.captured
            }
            every { onPluginsChanged() } returns Unit
        }

        plugin = ConnectivityReportPlugin().apply {
            setContext(context, device)
        }
    }

    @After
    fun cleanup() {
        packet = null
        unmockkObject(ConnectivityListener.Companion)
    }

    // LOCAL -> REMOTE

    @Test
    fun testDoesNotSendPacketWhenStatesEmpty() {
        plugin.listener.statesChanged(emptyMap())
        assertNull(packet)
    }

    @Test
    fun testSendsConnectivityStateSingle() {
        val subState = mockk<ConnectivityListener.SubscriptionState> {
            every { networkType } returns "4G"
            every { signalStrength } returns 3
        }

        plugin.listener.statesChanged(mapOf(6 to subState))

        val sent = checkNotNull(packet)

        assertEquals("kdeconnect.connectivity_report", sent.type)

        val signalStrengths = sent.getJSONObject("signalStrengths")!!
        val subInfo = signalStrengths.getJSONObject("6")
        assertEquals("4G", subInfo.getString("networkType"))
        assertEquals(3, subInfo.getInt("signalStrength"))
    }

    @Test
    fun testSendsConnectivityStateMultiple() {
        val subState1 = mockk<ConnectivityListener.SubscriptionState> {
            every { networkType } returns "5G"
            every { signalStrength } returns 4
        }
        val subState2 = mockk<ConnectivityListener.SubscriptionState> {
            every { networkType } returns "HSPA"
            every { signalStrength } returns 2
        }

        plugin.listener.statesChanged(mapOf(6 to subState1, 17 to subState2))

        val sent = checkNotNull(packet)

        val signalStrengths = sent.getJSONObject("signalStrengths")!!
        val subInfo1 = signalStrengths.getJSONObject("6")
        val subInfo2 = signalStrengths.getJSONObject("17")

        assertEquals("5G", subInfo1.getString("networkType"))
        assertEquals(4, subInfo1.getInt("signalStrength"))

        assertEquals("HSPA", subInfo2.getString("networkType"))
        assertEquals(2, subInfo2.getInt("signalStrength"))
    }

    // REMOTE -> LOCAL

    @Test
    fun testIgnoresReceivedPackets() {
        assertFalse(plugin.onPacketReceived(NetworkPacket("kdeconnect.connectivity_report")))
        assertFalse(plugin.onPacketReceived(NetworkPacket("some.other.type")))

        verify(exactly = 0) { device.onPluginsChanged() }
    }

    @Test
    fun testRegistersAndUnregistersStateListener() {
        val connectivityListener = mockk<ConnectivityListener>(relaxed = true)

        io.mockk.mockkObject(ConnectivityListener.Companion)
        every { ConnectivityListener.getInstance(context) } returns connectivityListener

        plugin.onCreate()
        verify(exactly = 1) { connectivityListener.listenStateChanges(plugin.listener) }

        plugin.onDestroy()
        verify(exactly = 1) { connectivityListener.cancelActiveListener(plugin.listener) }
    }
}