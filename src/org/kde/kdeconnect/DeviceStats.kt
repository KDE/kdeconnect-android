/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import java.util.*
import java.util.concurrent.TimeUnit

object DeviceStats {
    /**
     * Keep 24 hours of events
     */
    private const val EVENT_KEEP_WINDOW_MILLIS: Long = 24 * 60 * 60 * 1000

    /**
     * Delete old (>24 hours, see EVENT_KEEP_WINDOW_MILLIS) events every 6 hours
     */
    private const val CLEANUP_INTERVAL_MILLIS = EVENT_KEEP_WINDOW_MILLIS / 4

    private val eventsByDevice: MutableMap<String, PacketStats> = HashMap<String, PacketStats>()
    private var nextCleanup = System.currentTimeMillis() + CLEANUP_INTERVAL_MILLIS

    @RequiresApi(api = Build.VERSION_CODES.N)
    fun getStatsForDevice(deviceId: String): String {
        cleanupIfNeeded()

        val packetStats = eventsByDevice[deviceId] ?: return ""

        return buildString {
            val timeInMillis =
                minOf((System.currentTimeMillis() - packetStats.createdAtMillis), EVENT_KEEP_WINDOW_MILLIS)
            val hours = TimeUnit.MILLISECONDS.toHours(timeInMillis)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(timeInMillis) % 60
            append("From last ")
            append(hours)
            append("h ")
            append(minutes)
            append("m\n\n")

            packetStats.summaries.stream().sorted { o1, o2 ->
                o2.total compareTo o1.total // Sort them by total number of events
            }.forEach { count ->
                append(count.packetType.removePrefix("kdeconnect."))
                append("\n• ")
                append(count.received)
                append(" received\n• ")
                append(count.sentSuccessful + count.sentFailed)
                append(" sent (")
                append(count.sentFailed)
                append(" failed)\n")
            }
        }
    }

    @SuppressLint("NewApi") // We use core library desugar
    fun countReceived(deviceId: String, packetType: String) {
        synchronized(DeviceStats::class.java) {
            eventsByDevice
                .computeIfAbsent(deviceId) { PacketStats() }
                .receivedByType
                .computeIfAbsent(packetType) { ArrayList() }
                .add(System.currentTimeMillis())
        }
        cleanupIfNeeded()
    }

    @SuppressLint("NewApi") // We use core library desugar
    fun countSent(deviceId: String, packetType: String, success: Boolean) {
        if (success) {
            synchronized(DeviceStats::class.java) {
                eventsByDevice
                    .computeIfAbsent(deviceId) { PacketStats() }
                    .sentSuccessfulByType
                    .computeIfAbsent(packetType) { ArrayList() }
                    .add(System.currentTimeMillis())
            }
        } else {
            synchronized(DeviceStats::class.java) {
                eventsByDevice
                    .computeIfAbsent(deviceId) { PacketStats() }
                    .sentFailedByType
                    .computeIfAbsent(packetType) { ArrayList() }
                    .add(System.currentTimeMillis())
            }
        }
        cleanupIfNeeded()
    }

    private fun cleanupIfNeeded() {
        val cutoutTimestamp = System.currentTimeMillis() - EVENT_KEEP_WINDOW_MILLIS
        if (System.currentTimeMillis() > nextCleanup) {
            synchronized(DeviceStats::class.java) {
                Log.i("PacketStats", "Doing periodic cleanup")
                for (de in eventsByDevice.values) {
                    removeOldEvents(de.receivedByType, cutoutTimestamp)
                    removeOldEvents(de.sentFailedByType, cutoutTimestamp)
                    removeOldEvents(de.sentSuccessfulByType, cutoutTimestamp)
                }
                nextCleanup = System.currentTimeMillis() + CLEANUP_INTERVAL_MILLIS
            }
        }
    }

    @VisibleForTesting
    fun removeOldEvents(eventsByType: HashMap<String, ArrayList<Long>>, cutoutTimestamp: Long) {
        val iterator = eventsByType.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val events = entry.value

            var index = Collections.binarySearch(events, cutoutTimestamp)
            if (index < 0) {
                index = -(index + 1) // Convert the negative index to insertion point
            }

            if (index < events.size) {
                events.subList(0, index).clear()
            } else {
                iterator.remove() // No element greater than the threshold
            }
        }
    }

    internal class PacketStats {
        val createdAtMillis: Long = System.currentTimeMillis()
        val receivedByType: HashMap<String, ArrayList<Long>> = HashMap()
        val sentSuccessfulByType: HashMap<String, ArrayList<Long>> = HashMap()
        val sentFailedByType: HashMap<String, ArrayList<Long>> = HashMap()

        internal data class Summary(
            val packetType: String,
            var received: Int = 0,
            var sentSuccessful: Int = 0,
            var sentFailed: Int = 0,
            var total: Int = 0
        )

        @get:SuppressLint("NewApi") // We use core library desugar
        val summaries: Collection<Summary>
            get() {
                val countsByType: MutableMap<String, Summary> = HashMap()
                for ((key, value) in receivedByType) {
                    val summary = countsByType.computeIfAbsent(key) { packetType -> Summary(packetType) }
                    summary.received += value.size
                    summary.total += value.size
                }
                for ((key, value) in sentSuccessfulByType) {
                    val summary = countsByType.computeIfAbsent(key) { packetType -> Summary(packetType) }
                    summary.sentSuccessful += value.size
                    summary.total += value.size
                }
                for ((key, value) in sentFailedByType) {
                    val summary = countsByType.computeIfAbsent(key) { packetType -> Summary(packetType) }
                    summary.sentFailed += value.size
                    summary.total += value.size
                }
                return countsByType.values
            }
    }
}
