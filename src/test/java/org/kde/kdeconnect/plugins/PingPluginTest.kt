package org.kde.kdeconnect.plugins

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Test
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.plugins.ping.PingPlugin

class PingPluginTest {
    // Mocks the necessary components to test the PingPlugin
    private fun executeWithMocks(test: (device: Device, plugin: PingPlugin, notificationManager: NotificationManager) -> Unit) {
        val plugin = PingPlugin()
        val notificationManager = mockk<NotificationManager> {
            every { notify(any(), any()) } returns Unit
        }
        val context = mockk<Context> {
            every { getSharedPreferences(any(), any()) } returns mockk<SharedPreferences>()
            every { getSystemService(any()) } returns notificationManager
            every { getString(any()) } returns "STRING"
        }
        val device = mockk<Device> {
            every { name } returns "Test Device"
            every { sendPacket(any()) } returns Unit
        }
        mockkStatic(PendingIntent::class) {
            every<PendingIntent> {
                PendingIntent.getActivity(any<Context>(), any(), any(), any())
            } returns mockk<PendingIntent>()
            mockkStatic(NotificationCompat.Builder::class) {
                mockkConstructor(NotificationCompat.Builder::class)
                every { anyConstructed<NotificationCompat.Builder>().build() } returns mockk()
                plugin.setContext(context, device)
                test(device, plugin, notificationManager)
            }
        }
    }

    @Test
    fun startPlugin() {
        executeWithMocks { device, plugin, notificationManager ->
            val entries = plugin.getUiMenuEntries()
            entries.single().onClick(mockk())
            verify(exactly = 1) { device.sendPacket(match { np -> np.type == "kdeconnect.ping" && np.payload == null }) }
        }
    }

    // Tests all 3 return paths of PingPlugin.onPacketReceived

    @Test
    fun wrongPacketType() {
        executeWithMocks { device, plugin, notificationManager ->
            val np = NetworkPacket("kdeconnect.wrench")
            np["message"] = "Test Ping"
            assert(!plugin.onPacketReceived(np))
        }
    }

    @Test
    fun sendsNotificationNoMessage() {
        executeWithMocks { device, plugin, notificationManager ->
            val np = NetworkPacket("kdeconnect.ping")
            // np["message"] not set here
            assert(plugin.onPacketReceived(np))

            verify(exactly = 1) { notificationManager.notify(42, any()) }
        }
    }

    @Test
    fun sendsNotification() {
        executeWithMocks { device, plugin, notificationManager ->
            val np = NetworkPacket("kdeconnect.ping")
            np["message"] = "Test Ping"
            assert(plugin.onPacketReceived(np))

            verify(exactly = 1) { notificationManager.notify(not(42), any()) }
        }
    }
}
