package org.kde.kdeconnect;

import java.util.ArrayDeque;
import java.util.Optional;

/**
 * Keeps a queue of packets to send to a device, to prevent either blocking or using lots of threads
 */
class DevicePacketQueue {
    /**
     * Holds the packet and related stuff to keep in the queue
     */
    private static final class Item {
        NetworkPacket packet;
        /**
         * Replacement ID: if positive, it can be replaced by later packets with the same ID
         */
        int replaceID;
        Device.SendPacketStatusCallback callback;

        Item(NetworkPacket packet, int replaceID, Device.SendPacketStatusCallback callback) {
            this.packet = packet;
            this.callback = callback;
            this.replaceID = replaceID;
        }
    }

    private final ArrayDeque<Item> items = new ArrayDeque<>();
    private Device mDevice;
    private final Object lock = new Object();
    private boolean exit = false;

    DevicePacketQueue(Device device) {
        mDevice = device;
        new Thread(new SendingThread()).start();
    }

    /**
     * Send a packet (at some point in the future)
     * @param packet The packet
     * @param replaceID If positive, it will replace all older packets still in the queue
     * @param callback The callback after sending the packet
     */
    void addPacket(NetworkPacket packet, int replaceID, Device.SendPacketStatusCallback callback) {
        synchronized (lock) {
            if (exit) {
                callback.onFailure(new Exception("Device disconnected!"));
            } else {
                if (replaceID >= 0) {
                    final Optional<Item> itemOptional = items.stream()
                            .filter(item -> item.replaceID == replaceID).findFirst();
                    itemOptional.ifPresent(item -> {
                        //Replace contents with new contents
                        item.packet = packet;
                        item.callback = callback;
                    });
                }
                items.addLast(new Item(packet, replaceID, callback));
                lock.notify();
            }
        }
    }

    /**
     * Check if we still have an unsent packet in the queue with the given ID.
     * If so, remove it from the queue and return it
     * @param replaceID The replace ID (must be positive)
     * @return The found packet, or null
     */
    NetworkPacket getAndRemoveUnsentPacket(int replaceID) {
        synchronized (lock) {
            final Optional<Item> itemOptional = items.stream()
                    .filter(item -> item.replaceID == replaceID).findFirst();
            if (itemOptional.isPresent()) {
                final Item item = itemOptional.get();
                items.remove(item);
                return item.packet;
            }
        }
        return null;
    }

    void disconnected() {
        synchronized (lock) {
            exit = true;
            lock.notifyAll();
        }
    }

    private final class SendingThread implements Runnable {
        @Override
        public void run() {
            while (true) {
                Item item;
                synchronized (lock) {
                    while (items.isEmpty() && !exit) {
                        try {
                            lock.wait();
                        } catch (InterruptedException ignored) {
                        }
                    }
                    if (exit) break;

                    item = items.removeFirst();
                }

                mDevice.sendPacketBlocking(item.packet, item.callback);
            }

            while (!items.isEmpty()) {
                Item item = items.removeFirst();
                item.callback.onFailure(new Exception("Device disconnected!"));
            }
        }
    }
}
