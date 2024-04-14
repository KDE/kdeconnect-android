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

        queue.addPacketSync(new NetworkPacket("Test"), 0, callback);
        queue.addPacketSync(new NetworkPacket("Test1"), 1, callback);

        assertNotNull(queue.getAndRemoveUnsentPacketSync(0));
        assertNotNull(queue.getAndRemoveUnsentPacketSync(1));
    }

    @Test
    public void addPacketWithNegativeReplaceId() {
        Device device = Mockito.mock(Device.class);
        Device.SendPacketStatusCallback callback = Mockito.mock(Device.SendPacketStatusCallback.class);

        DevicePacketQueue queue = new DevicePacketQueue(device, false);

        queue.addPacketSync(new NetworkPacket("Test"), -1, callback);
        queue.addPacketSync(new NetworkPacket("Test1"), -1, callback);

        assertNotNull(queue.getAndRemoveUnsentPacketSync(-1));
        assertNotNull(queue.getAndRemoveUnsentPacketSync(-1));
    }

    @Test
    public void addPacketReplacesPacket() {
        Device device = Mockito.mock(Device.class);
        Device.SendPacketStatusCallback callback = Mockito.mock(Device.SendPacketStatusCallback.class);

        DevicePacketQueue queue = new DevicePacketQueue(device, false);

        queue.addPacketSync(new NetworkPacket("Test"), 1, callback);
        queue.addPacketSync(new NetworkPacket("Test1"), 1, callback);

        assertNotNull(queue.getAndRemoveUnsentPacketSync(1));
        assertNull(queue.getAndRemoveUnsentPacketSync(1));
    }
}