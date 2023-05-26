package org.kde.kdeconnect;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.HashMap;

@RunWith(PowerMockRunner.class)
public class PacketStatsTest {

    @Test
    public void removeOldEvents_cutoutExists() {
        final String key = "kdeconnect.ping";
        HashMap<String, ArrayList<Long>> eventsByType = new HashMap<>();
        ArrayList<Long> events = new ArrayList<>();
        eventsByType.put(key, events);
        events.add(10L);
        events.add(20L);
        events.add(30L);
        final long cutout = 20L;
        PacketStats.removeOldEvents(eventsByType, cutout);
        ArrayList<Long> eventsAfter = eventsByType.get(key);
        Assert.assertNotNull(eventsAfter);
        Assert.assertEquals(2, eventsAfter.size());
        Assert.assertEquals(eventsAfter.get(0).longValue(), 20L);
        Assert.assertEquals(eventsAfter.get(1).longValue(), 30L);
    }

    @Test
    public void removeOldEvents_cutoutDoesntExist() {
        final String key = "kdeconnect.ping";
        HashMap<String, ArrayList<Long>> eventsByType = new HashMap<>();
        ArrayList<Long> events = new ArrayList<>();
        eventsByType.put(key, events);
        events.add(10L);
        events.add(20L);
        events.add(30L);
        final long cutout = 25L;
        PacketStats.removeOldEvents(eventsByType, cutout);
        ArrayList<Long> eventsAfter = eventsByType.get(key);
        Assert.assertNotNull(eventsAfter);
        Assert.assertEquals(1, eventsAfter.size());
        Assert.assertEquals(eventsAfter.get(0).longValue(), 30L);
    }

    @Test
    public void removeOldEvents_OnlyOldEvents() {
        final String key = "kdeconnect.ping";
        HashMap<String, ArrayList<Long>> eventsByType = new HashMap<>();
        ArrayList<Long> events = new ArrayList<>();
        eventsByType.put(key, events);
        events.add(10L);
        events.add(20L);
        final long cutout = 25L;
        PacketStats.removeOldEvents(eventsByType, cutout);
        ArrayList<Long> eventsAfter = eventsByType.get(key);
        Assert.assertNull(eventsAfter);
    }

    @Test
    public void removeOldEvents_OnlyNewEvents() {
        final String key = "kdeconnect.ping";
        HashMap<String, ArrayList<Long>> eventsByType = new HashMap<>();
        ArrayList<Long> events = new ArrayList<>();
        eventsByType.put(key, events);
        events.add(10L);
        final long cutout = 5L;
        PacketStats.removeOldEvents(eventsByType, cutout);
        ArrayList<Long> eventsAfter = eventsByType.get(key);
        Assert.assertNotNull(eventsAfter);
        Assert.assertEquals(1, eventsAfter.size());
        Assert.assertEquals(eventsAfter.get(0).longValue(), 10L);
    }
}
