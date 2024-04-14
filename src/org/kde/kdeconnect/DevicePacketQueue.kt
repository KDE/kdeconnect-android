/*
 * SPDX-FileCopyrightText: 2019 Matthijs Tijink <matthijstijink@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.collections.ArrayDeque

/**
 * Keeps a queue of packets to send to a device, to prevent either blocking or using lots of threads
 */
class DevicePacketQueue @JvmOverloads constructor(private val device: Device, startRunning: Boolean = true) {
    /**
     * Holds the packet and related stuff to keep in the queue
     */
    private class Item(
        var packet: NetworkPacket,
        /**
         * Replacement ID: if positive, it can be replaced by later packets with the same ID
         */
        val replaceID: Int,
        var callback: Device.SendPacketStatusCallback
    )

    private val scope = CoroutineScope(Dispatchers.IO)
    private val sendingJob = SupervisorJob()
    private val loopJob = Job()

    private val items = ArrayDeque<Item>()
    private val mutex = Mutex()

    init {
        if (startRunning) {
            scope.launch(loopJob) {
                sending()
            }
        }
    }

    fun addPacketSync(packet: NetworkPacket, replaceID: Int, callback: Device.SendPacketStatusCallback) {
        runBlocking {
            addPacket(packet, replaceID, callback)
        }
    }

    /**
     * Send a packet (at some point in the future)
     * @param packet The packet
     * @param replaceID If positive, it will replace all older packets still in the queue
     * @param callback The callback after sending the packet
     */
    suspend fun addPacket(packet: NetworkPacket, replaceID: Int, callback: Device.SendPacketStatusCallback) {
        if (sendingJob.isCancelled) {
            callback.onFailure(Exception("Device disconnected!"))
        } else {
            mutex.withLock {
                var replaced = false
                if (replaceID >= 0) {
                    items.forEach {
                        if (it.replaceID == replaceID) {
                            it.packet = packet
                            it.callback = callback
                            replaced = true
                        }
                    }
                }

                if (!replaced) {
                    items.addLast(Item(packet, replaceID, callback))
                }
            }
        }
    }

    fun getAndRemoveUnsentPacketSync(replaceID: Int): NetworkPacket? {
        return runBlocking {
            getAndRemoveUnsentPacket(replaceID)
        }
    }
    /**
     * Check if we still have an unsent packet in the queue with the given ID.
     * If so, remove it from the queue and return it
     * @param replaceID The replace ID (must be positive)
     * @return The found packet, or null
     */
    suspend fun getAndRemoveUnsentPacket(replaceID: Int): NetworkPacket? {
        mutex.withLock {
            val itemOptional = items.stream()
                .filter { item: Item -> item.replaceID == replaceID }.findFirst()
            if (itemOptional.isPresent) {
                val item = itemOptional.get()
                items.remove(item)
                return item.packet
            }
        }
        return null
    }

    fun disconnected() {
        sendingJob.cancel()
    }

    private suspend fun sending() {
        while (true) {
            val item = mutex.withLock {
                if (items.isEmpty()) {
                    null
                } else {
                    items.removeFirst()
                }
            }

            if (item == null) {
                yield()
                continue
            }

            if (sendingJob.isCancelled) {
                item.callback.onFailure(Exception("Device disconnected!"))
            } else {
                scope.launch(sendingJob) {
                    device.sendPacketBlocking(item.packet, item.callback)
                }
            }
        }
    }
}
