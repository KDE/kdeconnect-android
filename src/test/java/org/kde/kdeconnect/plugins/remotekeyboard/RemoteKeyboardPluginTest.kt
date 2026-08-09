package org.kde.kdeconnect.plugins.remotekeyboard

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket

@RunWith(AndroidJUnit4::class)
class RemoteKeyboardPluginTest {
    private lateinit var remoteKeyboardPlugin: RemoteKeyboardPlugin
    private lateinit var context: Context
    private lateinit var device: Device
    private var packet: NetworkPacket? = null

    @Before
    fun setup() {
        context = mockk<Context>()
        device = mockk {
            val packetSlot = slot<NetworkPacket>()
            every { sendPacket(capture(packetSlot)) } answers {
                packet = packetSlot.captured
            }
            every { onPluginsChanged() } returns Unit
        }

        val sharedPreferences = mockk<SharedPreferences>(relaxed = true)
        every { sharedPreferences.getBoolean(any(), any()) } returns true
        every { sharedPreferences.getString(any(), any()) } returns ""
        every { sharedPreferences.edit() } returns mockk {
            every { putBoolean(any(), any()) } returns this
            every { putString(any(), any()) } returns this
            every { apply() } returns Unit
        }

        mockkStatic(PreferenceManager::class)
        every { PreferenceManager.getDefaultSharedPreferences(context) } returns sharedPreferences

        every { context.getString(any()) } returns "test_key"
        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
        every { context.packageName } returns "org.kde.kdeconnect_tp"

        remoteKeyboardPlugin = RemoteKeyboardPlugin().apply {
            setContext(context, device)
        }
    }

    @After
    fun cleanup() {
        packet = null // remove old capture packet
        RemoteKeyboardService.instance = null
    }

    private fun createServiceWithInputConnection(inputConnection: InputConnection): RemoteKeyboardService {
        val service = object : RemoteKeyboardService() {
            override fun getCurrentInputConnection(): InputConnection = inputConnection
        }
        service.visible = true
        return service
    }

    private fun createService(inputConnection: InputConnection, editorInfo: EditorInfo? = null): RemoteKeyboardService {
        val service = object : RemoteKeyboardService() {
            override fun getCurrentInputConnection(): InputConnection = inputConnection
            override fun getCurrentInputEditorInfo(): EditorInfo = editorInfo ?: super.getCurrentInputEditorInfo()
        }
        service.visible = true
        return service
    }

    // LOCAL -> REMOTE

    @Test
    fun testNotifyKeyboardState() {
        remoteKeyboardPlugin.notifyKeyboardState(true)

        val sentPacket = checkNotNull(packet)
        Assert.assertEquals("kdeconnect.mousepad.keyboardstate", sentPacket.type)
        Assert.assertTrue(sentPacket.getBoolean("state"))

        packet = null

        remoteKeyboardPlugin.notifyKeyboardState(false)

        val sentPacket2 = checkNotNull(packet)
        Assert.assertEquals("kdeconnect.mousepad.keyboardstate", sentPacket2.type)
        Assert.assertFalse(sentPacket2.getBoolean("state"))
    }

    @Test
    fun testGetMousePadPacketType() {
        val keyboardPacket = NetworkPacket("kdeconnect.mousepad.request")
        keyboardPacket["key"] = "a"
        Assert.assertEquals(
            RemoteKeyboardPlugin.MousePadPacketType.Keyboard,
            RemoteKeyboardPlugin.getMousePadPacketType(keyboardPacket)
        )

        val specialKeyPacket = NetworkPacket("kdeconnect.mousepad.request")
        specialKeyPacket["specialKey"] = 1
        Assert.assertEquals(
            RemoteKeyboardPlugin.MousePadPacketType.Keyboard,
            RemoteKeyboardPlugin.getMousePadPacketType(specialKeyPacket)
        )

        val mousePacket = NetworkPacket("kdeconnect.mousepad.request")
        Assert.assertEquals(
            RemoteKeyboardPlugin.MousePadPacketType.Mouse,
            RemoteKeyboardPlugin.getMousePadPacketType(mousePacket)
        )
    }

    // REMOTE -> LOCAL

    @Test
    fun testWrongPacketType() {
        val np = NetworkPacket("kdeconnect.wrench")
        Assert.assertFalse(remoteKeyboardPlugin.onPacketReceived(np))
    }

    @Test
    fun testMousePacketIgnored() {
        val np = NetworkPacket("kdeconnect.mousepad.request")
        Assert.assertFalse(remoteKeyboardPlugin.onPacketReceived(np))
    }

    @Test
    fun testKeyboardPacketProcessesVisibleKey() {
        val inputConnection = mockk<InputConnection>(relaxed = true)
        every { inputConnection.commitText(any(), any()) } returns true

        RemoteKeyboardService.instance = createServiceWithInputConnection(inputConnection)

        val np = NetworkPacket("kdeconnect.mousepad.request")
        np["key"] = "Hello"
        Assert.assertTrue(remoteKeyboardPlugin.onPacketReceived(np))

        verify { inputConnection.commitText("Hello", 5) }
    }

    @Test
    fun testKeyboardPacketSendsAck() {
        val inputConnection = mockk<InputConnection>(relaxed = true)
        every { inputConnection.commitText(any(), any()) } returns true

        RemoteKeyboardService.instance = createServiceWithInputConnection(inputConnection)

        packet = null

        val np = NetworkPacket("kdeconnect.mousepad.request")
        np["key"] = "World"
        np["sendAck"] = true
        Assert.assertTrue(remoteKeyboardPlugin.onPacketReceived(np))

        val ackPacket = checkNotNull(packet)
        Assert.assertEquals("kdeconnect.mousepad.echo", ackPacket.type)
        Assert.assertEquals("World", ackPacket.getString("key"))
        Assert.assertTrue(ackPacket.getBoolean("isAck"))
    }

    @Test
    fun testKeyboardPacketCtrlC() {
        val inputConnection = mockk<InputConnection>(relaxed = true)
        every { inputConnection.performContextMenuAction(android.R.id.copy) } returns true

        RemoteKeyboardService.instance = createServiceWithInputConnection(inputConnection)

        val np = NetworkPacket("kdeconnect.mousepad.request")
        np["key"] = "c"
        np["ctrl"] = true
        Assert.assertTrue(remoteKeyboardPlugin.onPacketReceived(np))

        verify { inputConnection.performContextMenuAction(android.R.id.copy) }
    }

    @Test
    fun testKeyboardPacketCtrlV() {
        val inputConnection = mockk<InputConnection>(relaxed = true)
        every { inputConnection.performContextMenuAction(android.R.id.paste) } returns true

        RemoteKeyboardService.instance = createServiceWithInputConnection(inputConnection)

        val np = NetworkPacket("kdeconnect.mousepad.request")
        np["key"] = "v"
        np["ctrl"] = true
        Assert.assertTrue(remoteKeyboardPlugin.onPacketReceived(np))

        verify { inputConnection.performContextMenuAction(android.R.id.paste) }
    }

    // SPECIAL KEYS

    @Test
    fun testSpecialKeyDelete() {
        // specialKey 1 -> KEYCODE_DEL -> default handling (sendKeyEvent)
        val events = mutableListOf<KeyEvent>()
        val inputConnection = mockk<InputConnection>(relaxed = true)
        every { inputConnection.sendKeyEvent(capture(events)) } returns true

        RemoteKeyboardService.instance = createServiceWithInputConnection(inputConnection)

        val np = NetworkPacket("kdeconnect.mousepad.request")
        np["specialKey"] = 1
        Assert.assertTrue(remoteKeyboardPlugin.onPacketReceived(np))

        Assert.assertEquals(2, events.size)
        Assert.assertEquals(KeyEvent.ACTION_DOWN, events[0].action)
        Assert.assertEquals(KeyEvent.KEYCODE_DEL, events[0].keyCode)
        Assert.assertEquals(KeyEvent.ACTION_UP, events[1].action)
        Assert.assertEquals(KeyEvent.KEYCODE_DEL, events[1].keyCode)
    }

    @Test
    fun testSpecialKeyInvalid() {
        // specialKey 17 is unmapped -> falls through to handleVisibleKey -> no "key" -> returns false
        RemoteKeyboardService.instance = createServiceWithInputConnection(mockk(relaxed = true))

        val np = NetworkPacket("kdeconnect.mousepad.request")
        np["specialKey"] = 17
        Assert.assertFalse(remoteKeyboardPlugin.onPacketReceived(np))
    }

    @Test
    fun testSpecialKeyDpadLeftWithShift() {
        // specialKey 4 -> KEYCODE_DPAD_LEFT + shift -> shift branch (4 key events)
        val events = mutableListOf<KeyEvent>()
        val inputConnection = mockk<InputConnection>(relaxed = true)
        every { inputConnection.sendKeyEvent(capture(events)) } returns true

        RemoteKeyboardService.instance = createServiceWithInputConnection(inputConnection)

        val np = NetworkPacket("kdeconnect.mousepad.request")
        np["specialKey"] = 4
        np["shift"] = true
        Assert.assertTrue(remoteKeyboardPlugin.onPacketReceived(np))

        Assert.assertEquals(4, events.size)
        Assert.assertEquals(KeyEvent.KEYCODE_SHIFT_LEFT, events[0].keyCode)
        Assert.assertEquals(KeyEvent.ACTION_DOWN, events[0].action)
        Assert.assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, events[1].keyCode)
        Assert.assertEquals(KeyEvent.ACTION_DOWN, events[1].action)
        Assert.assertEquals(KeyEvent.META_SHIFT_LEFT_ON, events[1].metaState)
        Assert.assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, events[2].keyCode)
        Assert.assertEquals(KeyEvent.ACTION_UP, events[2].action)
        Assert.assertEquals(KeyEvent.META_SHIFT_LEFT_ON, events[2].metaState)
        Assert.assertEquals(KeyEvent.KEYCODE_SHIFT_LEFT, events[3].keyCode)
        Assert.assertEquals(KeyEvent.ACTION_UP, events[3].action)
    }

    @Test
    fun testSpecialKeyDpadRightWithShift() {
        // specialKey 6 -> KEYCODE_DPAD_RIGHT + shift -> shift branch
        val events = mutableListOf<KeyEvent>()
        val inputConnection = mockk<InputConnection>(relaxed = true)
        every { inputConnection.sendKeyEvent(capture(events)) } returns true

        RemoteKeyboardService.instance = createServiceWithInputConnection(inputConnection)

        val np = NetworkPacket("kdeconnect.mousepad.request")
        np["specialKey"] = 6
        np["shift"] = true
        Assert.assertTrue(remoteKeyboardPlugin.onPacketReceived(np))

        Assert.assertEquals(4, events.size)
        Assert.assertEquals(KeyEvent.KEYCODE_SHIFT_LEFT, events[0].keyCode)
        Assert.assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, events[1].keyCode)
        Assert.assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, events[2].keyCode)
        Assert.assertEquals(KeyEvent.KEYCODE_SHIFT_LEFT, events[3].keyCode)
    }

    @Test
    fun testSpecialKeyDpadUpWithShift() {
        // specialKey 5 -> KEYCODE_DPAD_UP + shift -> shift branch
        val events = mutableListOf<KeyEvent>()
        val inputConnection = mockk<InputConnection>(relaxed = true)
        every { inputConnection.sendKeyEvent(capture(events)) } returns true

        RemoteKeyboardService.instance = createServiceWithInputConnection(inputConnection)

        val np = NetworkPacket("kdeconnect.mousepad.request")
        np["specialKey"] = 5
        np["shift"] = true
        Assert.assertTrue(remoteKeyboardPlugin.onPacketReceived(np))

        Assert.assertEquals(4, events.size)
        Assert.assertEquals(KeyEvent.KEYCODE_DPAD_UP, events[1].keyCode)
    }

    @Test
    fun testSpecialKeyDpadDownWithShift() {
        // specialKey 7 -> KEYCODE_DPAD_DOWN + shift -> shift branch
        val events = mutableListOf<KeyEvent>()
        val inputConnection = mockk<InputConnection>(relaxed = true)
        every { inputConnection.sendKeyEvent(capture(events)) } returns true

        RemoteKeyboardService.instance = createServiceWithInputConnection(inputConnection)

        val np = NetworkPacket("kdeconnect.mousepad.request")
        np["specialKey"] = 7
        np["shift"] = true
        Assert.assertTrue(remoteKeyboardPlugin.onPacketReceived(np))

        Assert.assertEquals(4, events.size)
        Assert.assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, events[1].keyCode)
    }

    @Test
    fun testSpecialKeyEnterPerformsEditorAction() {
        // specialKey 12 -> KEYCODE_ENTER with IME_ACTION_GO set
        val inputConnection = mockk<InputConnection>(relaxed = true)
        every { inputConnection.performEditorAction(any()) } returns true

        val editorInfo = EditorInfo().apply {
            imeOptions = EditorInfo.IME_ACTION_GO
        }

        RemoteKeyboardService.instance = createService(inputConnection, editorInfo)

        val np = NetworkPacket("kdeconnect.mousepad.request")
        np["specialKey"] = 12
        Assert.assertTrue(remoteKeyboardPlugin.onPacketReceived(np))

        verify { inputConnection.performEditorAction(EditorInfo.IME_ACTION_GO) }
    }

    @Test
    fun testSpecialKeyEnterFallbackToKeyEvent() {
        // specialKey 12 -> KEYCODE_ENTER with IME_FLAG_NO_ENTER_ACTION set
        val events = mutableListOf<KeyEvent>()
        val inputConnection = mockk<InputConnection>(relaxed = true)
        every { inputConnection.sendKeyEvent(capture(events)) } returns true

        val editorInfo = EditorInfo().apply {
            imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION
        }

        RemoteKeyboardService.instance = createService(inputConnection, editorInfo)

        val np = NetworkPacket("kdeconnect.mousepad.request")
        np["specialKey"] = 12
        Assert.assertTrue(remoteKeyboardPlugin.onPacketReceived(np))

        Assert.assertEquals(2, events.size)
        Assert.assertEquals(KeyEvent.KEYCODE_ENTER, events[0].keyCode)
        Assert.assertEquals(KeyEvent.KEYCODE_ENTER, events[1].keyCode)
    }

    @Test
    fun testSpecialKeyCtrlOverrideEnterNoAction() {
        // Ctrl+Enter overrides IME_FLAG_NO_ENTER_ACTION
        val inputConnection = mockk<InputConnection>(relaxed = true)
        every { inputConnection.performEditorAction(any()) } returns true

        val editorInfo = EditorInfo().apply {
            imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_ENTER_ACTION
        }

        RemoteKeyboardService.instance = createService(inputConnection, editorInfo)

        val np = NetworkPacket("kdeconnect.mousepad.request")
        np["specialKey"] = 12
        np["ctrl"] = true
        Assert.assertTrue(remoteKeyboardPlugin.onPacketReceived(np))

        // With ctrl, IME_FLAG_NO_ENTER_ACTION is overridden and IME_ACTION_GO is performed
        verify { inputConnection.performEditorAction(EditorInfo.IME_ACTION_GO) }
    }

    @Test
    fun testSpecialKeyCtrlRightWordNavigation() {
        // Ctrl+DPAD_RIGHT -> jump to next word
        val extractedText = ExtractedText().apply {
            text = "hello world foo"
            selectionEnd = 6 // cursor at index 6 (right after "hello ")
        }
        val inputConnection = mockk<InputConnection>(relaxed = true)
        every { inputConnection.getExtractedText(any<ExtractedTextRequest>(), any()) } returns extractedText

        RemoteKeyboardService.instance = createServiceWithInputConnection(inputConnection)

        val np = NetworkPacket("kdeconnect.mousepad.request")
        np["specialKey"] = 6
        np["ctrl"] = true
        Assert.assertTrue(remoteKeyboardPlugin.onPacketReceived(np))

        verify { inputConnection.setSelection(12, 12) }
    }

    @Test
    fun testSpecialKeyCtrlLeftWordNavigation() {
        // Ctrl+DPAD_LEFT -> jump to previous word
        val extractedText = ExtractedText().apply {
            text = "hello world foo"
            selectionEnd = 12 // cursor at index 12 (right before "foo")
        }
        val inputConnection = mockk<InputConnection>(relaxed = true)
        every { inputConnection.getExtractedText(any<ExtractedTextRequest>(), any()) } returns extractedText

        RemoteKeyboardService.instance = createServiceWithInputConnection(inputConnection)

        val np = NetworkPacket("kdeconnect.mousepad.request")
        np["specialKey"] = 4
        np["ctrl"] = true
        Assert.assertTrue(remoteKeyboardPlugin.onPacketReceived(np))

        verify { inputConnection.setSelection(6, 6) }
    }

    @Test
    fun testSpecialKeyCtrlShiftRightWordSelection() {
        // Ctrl+Shift+DPAD_RIGHT -> select to next word
        val extractedText = ExtractedText().apply {
            text = "hello world foo"
            selectionStart = 0
            selectionEnd = 6 // cursor at 6, selection from 0 to 6
        }
        val inputConnection = mockk<InputConnection>(relaxed = true)
        every { inputConnection.getExtractedText(any<ExtractedTextRequest>(), any()) } returns extractedText

        RemoteKeyboardService.instance = createServiceWithInputConnection(inputConnection)

        val np = NetworkPacket("kdeconnect.mousepad.request")
        np["specialKey"] = 6
        np["ctrl"] = true
        np["shift"] = true
        Assert.assertTrue(remoteKeyboardPlugin.onPacketReceived(np))

        verify { inputConnection.setSelection(0, 12) }
    }

    @Test
    fun testSpecialKeyTab() {
        // specialKey 2 -> KEYCODE_TAB -> default handling
        val events = mutableListOf<KeyEvent>()
        val inputConnection = mockk<InputConnection>(relaxed = true)
        every { inputConnection.sendKeyEvent(capture(events)) } returns true

        RemoteKeyboardService.instance = createServiceWithInputConnection(inputConnection)

        val np = NetworkPacket("kdeconnect.mousepad.request")
        np["specialKey"] = 2
        Assert.assertTrue(remoteKeyboardPlugin.onPacketReceived(np))

        Assert.assertEquals(2, events.size)
        Assert.assertEquals(KeyEvent.KEYCODE_TAB, events[0].keyCode)
        Assert.assertEquals(KeyEvent.KEYCODE_TAB, events[1].keyCode)
    }

    @Test
    fun testSpecialKeyForwardDelete() {
        // specialKey 13 -> KEYCODE_FORWARD_DEL -> default handling
        val events = mutableListOf<KeyEvent>()
        val inputConnection = mockk<InputConnection>(relaxed = true)
        every { inputConnection.sendKeyEvent(capture(events)) } returns true

        RemoteKeyboardService.instance = createServiceWithInputConnection(inputConnection)

        val np = NetworkPacket("kdeconnect.mousepad.request")
        np["specialKey"] = 13
        Assert.assertTrue(remoteKeyboardPlugin.onPacketReceived(np))

        Assert.assertEquals(2, events.size)
        Assert.assertEquals(KeyEvent.KEYCODE_FORWARD_DEL, events[0].keyCode)
        Assert.assertEquals(KeyEvent.KEYCODE_FORWARD_DEL, events[1].keyCode)
    }
}