package org.kde.kdeconnect.Plugin

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
import org.kde.kdeconnect.Plugins.PingPlugin.PingPlugin

class PingPluginTest {
    // Mocks the necessary components to test the PingPlugin
    private fun executeWithMocks(test: (plugin: PingPlugin, notificationManager: NotificationManager) -> Unit) {
        val plugin = PingPlugin()
        val notificationManager = mockk<NotificationManager> {
            every { notify(any(), any()) } returns Unit
        }
        val context = mockk<Context> {
            every { getSharedPreferences(any(), any()) } returns mockk<SharedPreferences>()
            every { getSystemService(any()) } returns notificationManager
        }
        val device = mockk<Device> {
            every { name } returns "Test Device"
        }
        mockkStatic(PendingIntent::class) {
            every<PendingIntent> {
                PendingIntent.getActivity(any<Context>(), any(), any(), any())
            } returns mockk<PendingIntent>()
            mockkStatic(NotificationCompat.Builder::class) {
                mockkConstructor(NotificationCompat.Builder::class)
                every { anyConstructed<NotificationCompat.Builder>().build() } returns mockk()
                plugin.setContext(context, device)

                test(plugin, notificationManager)
            }
        }
    }

    // Tests all 3 return paths of PingPlugin.onPacketReceived

    @Test
    fun wrongPacketType() {
        executeWithMocks { plugin, notificationManager ->
            val np = NetworkPacket("kdeconnect.wrench")
            np["message"] = "Test Ping"
            assert(!plugin.onPacketReceived(np))
        }
    }

    @Test
    fun sendsNotificationNoMessage() {
        executeWithMocks { plugin, notificationManager ->
            val np = NetworkPacket("kdeconnect.ping")
            // np["message"] not set here
            assert(plugin.onPacketReceived(np))

            verify(exactly = 1) { notificationManager.notify(42, any()) }
        }
    }

    @Test
    fun sendsNotification() {
        executeWithMocks { plugin, notificationManager ->
            val np = NetworkPacket("kdeconnect.ping")
            np["message"] = "Test Ping"
            assert(plugin.onPacketReceived(np))

            verify(exactly = 1) { notificationManager.notify(not(42), any()) }
        }
    }
}