package org.kde.kdeconnect.plugins.findmyphone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kde.kdeconnect.plugins.findmyphone.FindMyPhonePlugin.Companion.PACKET_TYPE_FINDMYPHONE_REQUEST

class FindMyPhonePluginTest {

    @Test
    fun testSupportedPacketTypes() {
        val plugin = FindMyPhonePlugin()
        val supportedTypes = plugin.supportedPacketTypes
        assertEquals(1, supportedTypes.size)
        assertEquals(PACKET_TYPE_FINDMYPHONE_REQUEST, supportedTypes[0])
    }

    @Test
    fun testOutgoingPacketTypesIsEmpty() {
        val plugin = FindMyPhonePlugin()
        assertTrue(plugin.outgoingPacketTypes.isEmpty())
    }

    @Test
    fun testHasSettings() {
        val plugin = FindMyPhonePlugin()
        assertTrue(plugin.hasSettings())
    }
}
