/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.backends.lan

import android.app.Application
import android.content.Context
import android.net.nsd.NsdManager
import android.net.wifi.WifiManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.helpers.TrustedNetworkHelper
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class MdnsDiscoveryTest {
    private lateinit var discovery: MdnsDiscovery
    private lateinit var nsd: NsdManager
    private lateinit var lock: WifiManager.MulticastLock
    private val listeners = mutableListOf<NsdManager.DiscoveryListener>()
    private var held = false

    @Before
    fun setup() {
        val context = spyk(ApplicationProvider.getApplicationContext<Application>())
        nsd = mockk(relaxed = true)
        lock = mockk(relaxed = true)
        val wifi = mockk<WifiManager>()
        every { context.applicationContext } returns context
        every { context.getSystemService(Context.NSD_SERVICE) } returns nsd
        every { context.getSystemService(Context.WIFI_SERVICE) } returns wifi
        every { wifi.createMulticastLock(any()) } returns lock
        every { lock.acquire() } answers { held = true }
        every { lock.release() } answers { held = false }
        every { lock.isHeld } answers { held }
        every { nsd.discoverServices(any<String>(), any(), any<NsdManager.DiscoveryListener>()) } answers {
            listeners.add(arg(2))
        }
        mockkObject(TrustedNetworkHelper.Companion)
        every { TrustedNetworkHelper.isTrustedNetwork(any()) } returns true
        DeviceHelper.initializeDeviceId(context)
        discovery = MdnsDiscovery(context, mockk(relaxed = true))
    }

    @After
    fun cleanup() {
        discovery.stopDiscovering()
        unmockkAll()
    }

    private fun advance(seconds: Long) = shadowOf(Looper.getMainLooper()).idleFor(seconds, TimeUnit.SECONDS)

    @Test
    fun discoveryReleasesLockAfterThirtySecondsAndCanRestart() {
        discovery.startDiscovering()
        advance(29)
        assertTrue(held)
        advance(1)
        assertFalse(held)
        verify(exactly = 1) { nsd.stopServiceDiscovery(listeners[0]) }
        discovery.startDiscovering()
        assertTrue(held)
        advance(30)
        assertFalse(held)
        verify(exactly = 2) { lock.release() }
    }

    @Test
    fun repeatedStartDoesNotExtendActiveWindow() {
        discovery.startDiscovering()
        advance(20)
        discovery.startDiscovering()
        advance(10)
        assertFalse(held)
        assertEquals(1, listeners.size)
    }

    @Test
    fun synchronousStartExceptionReleasesLockAndAllowsRetry() {
        every { nsd.discoverServices(any<String>(), any(), any<NsdManager.DiscoveryListener>()) } throws SecurityException("denied")
        assertThrows(SecurityException::class.java) { discovery.startDiscovering() }
        assertFalse(held)
        advance(30)
        verify(exactly = 0) { nsd.stopServiceDiscovery(any()) }
        every { nsd.discoverServices(any<String>(), any(), any<NsdManager.DiscoveryListener>()) } answers {
            listeners.add(arg(2))
        }
        discovery.startDiscovering()
        assertTrue(held)
    }

    @Test
    fun failedStartCancelsTimeoutWithoutStoppingNextSession() {
        discovery.startDiscovering()
        advance(10)
        listeners[0].onStartDiscoveryFailed("_kdeconnect._udp", 3)
        assertFalse(held)
        discovery.startDiscovering()
        advance(20)
        assertTrue(held)
        advance(10)
        assertFalse(held)
    }

    @Test
    fun oldCallbacksCannotReleaseNewSessionsLock() {
        discovery.startDiscovering()
        val old = listeners[0]
        discovery.stopDiscovering()
        discovery.startDiscovering()
        old.onDiscoveryStopped("_kdeconnect._udp")
        old.onStartDiscoveryFailed("_kdeconnect._udp", 3)
        assertTrue(held)
        advance(30)
        assertFalse(held)
    }

    @Test
    fun unexpectedStopReleasesLockAndCancelsTimeout() {
        discovery.startDiscovering()
        listeners[0].onDiscoveryStopped("_kdeconnect._udp")
        assertFalse(held)
        advance(30)
        verify(exactly = 0) { nsd.stopServiceDiscovery(any()) }
        verify(exactly = 1) { lock.release() }
    }

    @Test
    fun explicitStopIsIdempotentAndCancelsTimeout() {
        discovery.startDiscovering()
        discovery.stopDiscovering()
        discovery.stopDiscovering()
        advance(30)
        assertFalse(held)
        verify(exactly = 1) { lock.release() }
        verify(exactly = 1) { nsd.stopServiceDiscovery(any()) }
    }

    @Test
    fun stopExceptionStillReleasesLock() {
        discovery.startDiscovering()
        every { nsd.stopServiceDiscovery(any()) } throws SecurityException("denied")
        assertThrows(SecurityException::class.java) { discovery.stopDiscovering() }
        assertFalse(held)
        advance(30)
        verify(exactly = 1) { lock.release() }
    }

    @Test
    fun untrustedNetworkDoesNotAcquireLock() {
        every { TrustedNetworkHelper.isTrustedNetwork(any()) } returns false
        discovery.startDiscovering()
        advance(30)
        verify(exactly = 0) { lock.acquire() }
        assertTrue(listeners.isEmpty())
    }
}
