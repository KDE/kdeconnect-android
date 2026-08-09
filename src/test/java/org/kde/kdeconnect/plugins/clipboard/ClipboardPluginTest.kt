package org.kde.kdeconnect.plugins.clipboard

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.verify
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.MockSharedPreference
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect_tp.R

@RunWith(AndroidJUnit4::class)
class ClipboardPluginTest {
    private lateinit var clipboardPlugin: ClipboardPlugin
    private lateinit var clipboardListener: ClipboardListener
    private lateinit var context: Application
    private lateinit var device: Device
    private lateinit var preferences: MockSharedPreference
    private var packet: NetworkPacket? = null

    private companion object {
        const val SKIP_SENSITIVE_PREF_KEY = "clipboard_skip_sensitive_sync"
        const val PREFERENCES_NAME = "testDevice_ClipboardPlugin_preferences"
    }

    @Before
    fun setup() {
        preferences = MockSharedPreference()
        context = spyk(ApplicationProvider.getApplicationContext()) {
            every { getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE) } returns preferences
            every { getString(R.string.clipboard_preference_key_skip_sensitive) } returns SKIP_SENSITIVE_PREF_KEY
        }

        val realListener = ClipboardListener.instance(context)
        clipboardListener = spyk(realListener)

        mockkObject(ClipboardListener.Companion)
        every { ClipboardListener.instance(context) } returns clipboardListener

        device = mockk {
            every { deviceId } returns "testDevice"
            every { sendPacket(any()) } answers {
                packet = arg<NetworkPacket>(0)
            }
        }

        clipboardPlugin = ClipboardPlugin().apply {
            setContext(context, device)
        }
    }

    @After
    fun cleanup() {
        packet = null // Remove old capture packet
        preferences.edit().clear().apply()
    }

    // REMOTE -> LOCAL

    @Test
    fun testReceiveAndApplyClipboardUpdate() {
        val content = "Gr5hDjL68YQKfH6pez7n59Dm6"
        val packet = NetworkPacket("kdeconnect.clipboard").apply {
            set("content", content)
        }

        Assert.assertTrue(clipboardPlugin.onPacketReceived(packet))

        // Verify that the clipboard content has been set
        verify(exactly = 1) { clipboardListener.setText(content) }
    }

    @Test
    fun testReceiveAndDiscardClipboardUpdateTimestampZero() {
        val content = "DLWq7RvblSa6zFPrwLjs9JAdA"
        val timestamp = System.currentTimeMillis()
        val packet = NetworkPacket("kdeconnect.clipboard.connect").apply {
            set("content", content)
            set("timestamp", timestamp)
        }

        every { clipboardListener.updateTimestamp } returns 0L // Existing timestamp is invalid

        Assert.assertTrue(clipboardPlugin.onPacketReceived(packet))

        // Verify that the clipboard content is updated
        verify(exactly = 1) { clipboardListener.setText(content) }
    }

    @Test
    fun testReceiveAndDiscardClipboardUpdateTimestampOld() {
        val content = "2aZB2x22brdYSubSPPDr864LW"
        val timestamp = System.currentTimeMillis() - 1000 // Simulating an older timestamp
        val packet = NetworkPacket("kdeconnect.clipboard.connect").apply {
            set("content", content)
            set("timestamp", timestamp)
        }

        every { clipboardListener.updateTimestamp } returns timestamp + 1000

        Assert.assertFalse(clipboardPlugin.onPacketReceived(packet))

        // Verify that the clipboard content is NOT updated
        verify(exactly = 0) { clipboardListener.setText(content) }
    }

    @Test(expected = UnsupportedOperationException::class)
    fun testReceiveInvalidPacket() {
        val invalidPacket = NetworkPacket("invalid.type")
        clipboardPlugin.onPacketReceived(invalidPacket) // Should throw exception
    }

    // LOCAL -> REMOTE

    @Test
    fun testOnCreate() {
        val content = "B7n30xe0NNO6Y1J7PXOFj6pGd"
        every { clipboardListener.currentContent } returns content
        every { clipboardListener.currentContentType } returns ClipboardListener.ClipboardContentType.Text

        Assert.assertTrue(clipboardPlugin.onCreate())

        val sentPacket = checkNotNull(packet)
        Assert.assertEquals("kdeconnect.clipboard.connect", sentPacket.type)
        Assert.assertEquals(0, sentPacket.getLong("timestamp"))
        Assert.assertEquals(content, sentPacket.getString("content"))
    }

    @Test
    fun testSendConnectPacketSkippedWhenClipSensitiveAndPreferenceOn() {
        preferences.edit().putBoolean(SKIP_SENSITIVE_PREF_KEY, true).apply()

        every { clipboardListener.currentContent } returns "secret"
        every { clipboardListener.currentContentType } returns ClipboardListener.ClipboardContentType.Password
        every { clipboardListener.updateTimestamp } returns 12345L

        Assert.assertTrue(clipboardPlugin.onCreate())

        Assert.assertNull(packet)
        verify(exactly = 0) { device.sendPacket(any()) }
    }

    @Test
    fun testSendConnectPacketSentWhenClipSensitiveButPreferenceOff() {
        preferences.edit().putBoolean(SKIP_SENSITIVE_PREF_KEY, false).apply()

        val content = "still-sent"
        every { clipboardListener.currentContent } returns content
        every { clipboardListener.currentContentType } returns ClipboardListener.ClipboardContentType.Password
        every { clipboardListener.updateTimestamp } returns 999L

        Assert.assertTrue(clipboardPlugin.onCreate())

        val sentPacket = checkNotNull(packet)
        Assert.assertEquals("kdeconnect.clipboard.connect", sentPacket.type)
        Assert.assertEquals(content, sentPacket.getString("content"))
    }

    @Test
    fun testPropagateClipboard() {
        val content = "llY3kfZNhPur9ldTWEuHQBHPC"
        every { clipboardListener.currentContent } returns content

        clipboardPlugin.propagateClipboard(content)

        val sentPacket = checkNotNull(packet)
        Assert.assertEquals("kdeconnect.clipboard", sentPacket.type)
        Assert.assertEquals(content, sentPacket.getString("content"))
    }
}
