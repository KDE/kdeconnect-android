package org.kde.kdeconnect.Plugin

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.Plugins.BatteryPlugin.BatteryPlugin

@RunWith(AndroidJUnit4::class)
class BatteryPluginTest {
    private lateinit var batteryPlugin: BatteryPlugin
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
        batteryPlugin = BatteryPlugin().apply {
            setContext(context, device)
        }
    }

    @After
    fun cleanup() {
        packet = null // remove old capture packet
    }

    // LOCAL -> REMOTE

    @Test
    fun testSendBatteryLow() {
        val intent = Intent().apply {
            action = Intent.ACTION_BATTERY_LOW
            putExtra(BatteryManager.EXTRA_LEVEL, 15)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_PLUGGED, 0) // Not charging
        }
        batteryPlugin.receiver.onReceive(context, intent)

        // Check battery info updated accordingly
        val packet = this.packet
        checkNotNull(packet)
        Assert.assertEquals(15, packet.getInt("currentCharge"))
        Assert.assertEquals(false, packet.getBoolean("isCharging"))
        Assert.assertEquals(1, packet.getInt("thresholdEvent"))
    }

    @Test
    fun testSendBatteryOkAfterLow() {
        // First simulate battery low
        val lowIntent = Intent().apply {
            action = Intent.ACTION_BATTERY_LOW
            putExtra(BatteryManager.EXTRA_LEVEL, 15)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_PLUGGED, 0) // Not charging
        }
        batteryPlugin.receiver.onReceive(context, lowIntent)

        checkNotNull(this.packet)

        // Now simulate battery is okay
        val okIntent = Intent().apply {
            action = Intent.ACTION_BATTERY_OKAY
        }
        batteryPlugin.receiver.onReceive(context, okIntent)

        // Check if the isLowBattery flag is reset
        val packet = this.packet
        checkNotNull(packet)
        Assert.assertEquals(15, packet.getInt("currentCharge"))
        Assert.assertEquals(false, packet.getBoolean("isCharging"))
        Assert.assertEquals(0, packet.getInt("thresholdEvent"))
    }

    @Test
    fun testBatteryCharging() {
        val intent = Intent().apply {
            action = Intent.ACTION_BATTERY_CHANGED
            putExtra(BatteryManager.EXTRA_LEVEL, 50)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_AC)
        }
        batteryPlugin.receiver.onReceive(context, intent)

        // Check battery info updated accordingly
        val packet = this.packet
        checkNotNull(packet)
        Assert.assertEquals(50, packet.getInt("currentCharge"))
        Assert.assertEquals(true, packet.getBoolean("isCharging"))
        Assert.assertEquals(0, packet.getInt("thresholdEvent"))
    }

    @Test
    fun testBatteryChargingToLow() {
        val chargingIntent = Intent().apply {
            action = Intent.ACTION_BATTERY_CHANGED
            putExtra(BatteryManager.EXTRA_LEVEL, 25)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_AC)
        }
        batteryPlugin.receiver.onReceive(context, chargingIntent)

        // Initial state should reflect charging
        val firstPacket = this.packet
        checkNotNull(firstPacket)
        Assert.assertEquals(25, firstPacket.getInt("currentCharge"))
        Assert.assertEquals(true, firstPacket.getBoolean("isCharging"))

        // Now simulate battery low condition
        val lowIntent = Intent().apply {
            action = Intent.ACTION_BATTERY_LOW
            putExtra(BatteryManager.EXTRA_LEVEL, 15)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_PLUGGED, 0)
        }
        batteryPlugin.receiver.onReceive(context, lowIntent)

        // Check battery info updated accordingly
        val lastPacket = this.packet
        checkNotNull(lastPacket)
        Assert.assertEquals(15, lastPacket.getInt("currentCharge"))
        Assert.assertEquals(false, lastPacket.getBoolean("isCharging"))
        Assert.assertEquals(1, lastPacket.getInt("thresholdEvent"))
    }

    @Test
    fun testBatteryStatusChangeFromChargingToNotCharging() {
        val chargingIntent = Intent().apply {
            action = Intent.ACTION_BATTERY_CHANGED
            putExtra(BatteryManager.EXTRA_LEVEL, 60)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_AC)
        }
        batteryPlugin.receiver.onReceive(context, chargingIntent)

        // Initial state should reflect charging
        val firstPacket = this.packet
        checkNotNull(firstPacket)
        Assert.assertEquals(60, firstPacket.getInt("currentCharge"))
        Assert.assertEquals(true, firstPacket.getBoolean("isCharging"))

        // Now, simulate battery status change to not charging
        val notChargingIntent = Intent().apply {
            action = Intent.ACTION_BATTERY_CHANGED
            putExtra(BatteryManager.EXTRA_LEVEL, 60)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_PLUGGED, 0) // Not charging
        }
        batteryPlugin.receiver.onReceive(context, notChargingIntent)

        // Check battery info updated accordingly
        val lastPacket = this.packet
        checkNotNull(lastPacket)
        Assert.assertEquals(60, lastPacket.getInt("currentCharge"))
        Assert.assertEquals(false, lastPacket.getBoolean("isCharging"))
        Assert.assertEquals(0, lastPacket.getInt("thresholdEvent"))
    }

    // REMOTE -> LOCAL

    @Test
    fun checkPacketType() {
        Assert.assertFalse(batteryPlugin.onPacketReceived(NetworkPacket("invalid type")))

        Assert.assertTrue(batteryPlugin.onPacketReceived(NetworkPacket("kdeconnect.battery")))
        verify(exactly = 1) { device.onPluginsChanged() }
    }

    @Test
    fun processIncomingBatteryInfoPacket() {
        val packet = NetworkPacket("kdeconnect.battery")
        packet["currentCharge"] = 75
        packet["isCharging"] = true
        packet["thresholdEvent"] = 0

        batteryPlugin.onPacketReceived(packet)

        val batteryInfo = batteryPlugin.remoteBatteryInfo
        checkNotNull(batteryInfo)
        Assert.assertEquals(75, batteryInfo.currentCharge)
        Assert.assertEquals(true, batteryInfo.isCharging)
        Assert.assertEquals(0, batteryInfo.thresholdEvent)

        verify(exactly = 1) { device.onPluginsChanged() }
    }
}