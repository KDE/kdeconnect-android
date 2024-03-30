package org.kde.kdeconnect

import org.junit.Assert
import org.junit.Test
import org.kde.kdeconnect.DeviceStats.removeOldEvents

class DeviceStatsTest {
    @Test
    fun removeOldEvents_cutoutExists() {
        val key = "kdeconnect.ping"
        val eventsByType = HashMap<String, ArrayList<Long>>().apply {
            val events = arrayListOf(10L, 20L, 30L)
            put(key, events)
        }
        val cutout = 20L
        removeOldEvents(eventsByType, cutout)
        val eventsAfter = eventsByType[key]!!
        Assert.assertNotNull(eventsAfter)
        Assert.assertEquals(2, eventsAfter.size.toLong())
        Assert.assertEquals(eventsAfter[0], 20L)
        Assert.assertEquals(eventsAfter[1], 30L)
    }

    @Test
    fun removeOldEvents_cutoutDoesntExist() {
        val key = "kdeconnect.ping"
        val eventsByType = HashMap<String, ArrayList<Long>>().apply {
            val events = arrayListOf(10L, 20L, 30L)
            put(key, events)
        }
        val cutout = 25L
        removeOldEvents(eventsByType, cutout)
        val eventsAfter = eventsByType[key]!!
        Assert.assertNotNull(eventsAfter)
        Assert.assertEquals(1, eventsAfter.size.toLong())
        Assert.assertEquals(eventsAfter[0], 30L)
    }

    @Test
    fun removeOldEvents_OnlyOldEvents() {
        val key = "kdeconnect.ping"
        val eventsByType = HashMap<String, ArrayList<Long>>().apply {
            val events = arrayListOf(10L, 20L)
            put(key, events)
        }
        val cutout = 25L
        removeOldEvents(eventsByType, cutout)
        val eventsAfter = eventsByType[key]
        Assert.assertNull(eventsAfter)
    }

    @Test
    fun removeOldEvents_OnlyNewEvents() {
        val key = "kdeconnect.ping"
        val eventsByType = HashMap<String, ArrayList<Long>>().apply {
            val events = arrayListOf(10L)
            put(key, events)
        }
        val cutout = 5L
        removeOldEvents(eventsByType, cutout)
        val eventsAfter = eventsByType[key]!!
        Assert.assertNotNull(eventsAfter)
        Assert.assertEquals(1, eventsAfter.size.toLong())
        Assert.assertEquals(eventsAfter[0], 10L)
    }
}
