package org.kde.kdeconnect;

import static org.junit.Assert.*;

import org.junit.Test;
import org.mockito.Mockito;

public class DevicePacketQueueTest {
    @Test
    public void addPacketWithPositiveReplaceId() {
        Device device = Mockito.mock(Device.class);
        Device.SendPacketStatusCallback callback = Mockito.mock(Device.SendPacketStatusCallback.class);

        DevicePacketQueue queue = new DevicePacketQueue(device, false);

        queue.addPacket(new NetworkPacket("Test"), 0, callback);
        queue.addPacket(new NetworkPacket("Test1"), 1, callback);

        assertNotNull(queue.getAndRemoveUnsentPacket(0));
        assertNotNull(queue.getAndRemoveUnsentPacket(1));
    }

    @Test
    public void addPacketWithNegativeReplaceId() {
        Device device = Mockito.mock(Device.class);
        Device.SendPacketStatusCallback callback = Mockito.mock(Device.SendPacketStatusCallback.class);

        DevicePacketQueue queue = new DevicePacketQueue(device, false);

        queue.addPacket(new NetworkPacket("Test"), -1, callback);
        queue.addPacket(new NetworkPacket("Test1"), -1, callback);

        assertNotNull(queue.getAndRemoveUnsentPacket(-1));
        assertNotNull(queue.getAndRemoveUnsentPacket(-1));
    }

    @Test
    public void addPacketReplacesPacket() {
        Device device = Mockito.mock(Device.class);
        Device.SendPacketStatusCallback callback = Mockito.mock(Device.SendPacketStatusCallback.class);

        DevicePacketQueue queue = new DevicePacketQueue(device, false);

        queue.addPacket(new NetworkPacket("Test"), 1, callback);
        queue.addPacket(new NetworkPacket("Test1"), 1, callback);

        assertNotNull(queue.getAndRemoveUnsentPacket(1));
        assertNull(queue.getAndRemoveUnsentPacket(1));
    }
}