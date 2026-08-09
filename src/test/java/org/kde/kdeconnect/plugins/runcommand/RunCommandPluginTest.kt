package org.kde.kdeconnect.plugins.runcommand

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket

@RunWith(AndroidJUnit4::class)
class RunCommandPluginTest {
    private lateinit var runCommandPlugin: RunCommandPlugin
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
            every { onPluginsChanged() } returns Unit
        }
        runCommandPlugin = RunCommandPlugin().apply {
            setContext(context, device)
        }
    }

    @After
    fun cleanup() {
        packet = null // Ensuring we clean up any captured packets
    }

    // LOCAL -> REMOTE

    @Test
    fun testRunCommand() {
        val commandKey = "testCommandKey"
        runCommandPlugin.runCommand(commandKey)

        val sentPacket = checkNotNull(packet)
        assertEquals("kdeconnect.runcommand.request", sentPacket.type)
        assertEquals(commandKey, sentPacket.getString("key"))
    }

    @Test
    fun testRequestCommandList() {
        runCommandPlugin.onCreate() // Simulate plugin creation that requests command list

        val sentPacket = checkNotNull(packet)
        assertEquals("kdeconnect.runcommand.request", sentPacket.type)
        assertTrue(sentPacket.has("requestCommandList"))
        assertTrue(sentPacket.getBoolean("requestCommandList"))
    }

    // REMOTE -> LOCAL

    @Test
    fun testReceiveCommandList() {
        val commandListPacket = NetworkPacket("kdeconnect.runcommand").apply {
            set("commandList", JSONObject().apply {
                put("command1", JSONObject().apply {
                    put("name", "Command 1")
                    put("key", "command1")
                })
                put("command2", JSONObject().apply {
                    put("name", "Command 2")
                    put("key", "command2")
                })
            })
        }

        assertTrue(runCommandPlugin.onPacketReceived(commandListPacket))

        val commandList = runCommandPlugin.commandList
        assertEquals(2, commandList.size)

        val command1 = commandList[0]
        assertEquals("command1", command1.getString("key"))
        assertEquals("Command 1", command1.getString("name"))

        val command2 = commandList[1]
        assertEquals("command2", command2.getString("key"))
        assertEquals("Command 2", command2.getString("name"))

        verify(exactly = 1) { device.onPluginsChanged() }
    }

    @Test
    fun testReceiveCommandsUpdate() {
        // First, simulate receiving a basic command list
        val initialCommandPacket = NetworkPacket("kdeconnect.runcommand").apply {
            set("commandList", JSONObject().apply {
                put("command1", JSONObject().apply {
                    put("name", "Command 1")
                    put("key", "command1")
                })
            })
        }
        assertTrue(runCommandPlugin.onPacketReceived(initialCommandPacket))

        // Then, send a new packet with an updated command1 and a new command2
        val updatedCommandPacket = NetworkPacket("kdeconnect.runcommand").apply {
            set("commandList", JSONObject().apply {
                put("command1", JSONObject().apply {
                    put("name", "Updated Command 1")
                    put("key", "command1")
                })
                put("command2", JSONObject().apply {
                    put("name", "Command 2")
                    put("key", "command2")
                })
            })
        }
        assertTrue(runCommandPlugin.onPacketReceived(updatedCommandPacket))

        // Afterward we check the list has been updated appropriately
        val commandList = runCommandPlugin.commandList
        assertEquals(2, commandList.size)

        val updatedCommand1 = commandList[0]
        assertEquals("command1", updatedCommand1.getString("key"))
        assertEquals("Updated Command 1", updatedCommand1.getString("name"))

        val command2 = commandList[1]
        assertEquals("command2", command2.getString("key"))
        assertEquals("Command 2", command2.getString("name"))

        verify { device.onPluginsChanged() }
    }

    @Test
    fun testCallbacksOnCommandListUpdate() {
        val listener = mockk<RunCommandPlugin.CommandsChangedCallback>(relaxed = true)
        runCommandPlugin.addCommandsUpdatedCallback(listener)

        val commandListPacket = NetworkPacket("kdeconnect.runcommand").apply {
            set("commandList", JSONObject().apply {
                put("command1", JSONObject().apply {
                    put("name", "Command 1")
                    put("key", "command1")
                })
            })
        }

        runCommandPlugin.onPacketReceived(commandListPacket)
        verify { listener.update() }
    }

    @Test
    fun testCanAddCommandFlag() {
        fun addBasicCommandList(np: NetworkPacket) {
            np["commandList"] = JSONObject().apply {
                put("command1", JSONObject().apply {
                    put("name", "Command 1")
                    put("key", "command1")
                })
                put("command2", JSONObject().apply {
                    put("name", "Command 2")
                    put("key", "command2")
                })
            }
        }

        val canAddCommandPacket = NetworkPacket("kdeconnect.runcommand").apply {
            set("canAddCommand", true)
            addBasicCommandList(this)
        }

        assertTrue(runCommandPlugin.onPacketReceived(canAddCommandPacket))
        assertTrue(runCommandPlugin.canAddCommand())

        val cannotAddCommandPacket = NetworkPacket("kdeconnect.runcommand").apply {
            set("canAddCommand", false)
            addBasicCommandList(this)
        }

        assertTrue(runCommandPlugin.onPacketReceived(cannotAddCommandPacket))
        assertFalse(runCommandPlugin.canAddCommand())
    }
}