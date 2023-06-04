package org.kde.kdeconnect;

import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DeviceStats {

    /**
     * Keep 24 hours of events
     */
    private static final long EVENT_KEEP_WINDOW_MILLIS = 24 * 60 * 60 * 1000;

    /**
     * Delete old (>24 hours, see EVENT_KEEP_WINDOW_MILLIS) events every 6 hours
     */
    private static final long CLEANUP_INTERVAL_MILLIS = EVENT_KEEP_WINDOW_MILLIS/4;

    private final static HashMap<String, PacketStats> eventsByDevice = new HashMap<>();
    private static long nextCleanup = System.currentTimeMillis() + CLEANUP_INTERVAL_MILLIS;

    static class PacketStats {
        public long createdAtMillis = System.currentTimeMillis();
        public HashMap<String, ArrayList<Long>> receivedByType = new HashMap<>();
        public HashMap<String, ArrayList<Long>> sentSuccessfulByType = new HashMap<>();
        public HashMap<String, ArrayList<Long>> sentFailedByType = new HashMap<>();

        static class Summary {
            final @NonNull String packetType;
            int received = 0;
            int sentSuccessful = 0;
            int sentFailed = 0;
            int total = 0;

            Summary(@NonNull String packetType) {
                this.packetType = packetType;
            }
        }

        @RequiresApi(api = Build.VERSION_CODES.N)
        public @NonNull Collection<Summary> getSummaries() {
            HashMap<String, Summary> countsByType = new HashMap<>();
            for (Map.Entry<String, ArrayList<Long>> entry : receivedByType.entrySet()) {
                Summary summary = countsByType.computeIfAbsent(entry.getKey(), Summary::new);
                summary.received += entry.getValue().size();
                summary.total += entry.getValue().size();
            }
            for (Map.Entry<String, ArrayList<Long>> entry : sentSuccessfulByType.entrySet()) {
                Summary summary = countsByType.computeIfAbsent(entry.getKey(), Summary::new);
                summary.sentSuccessful += entry.getValue().size();
                summary.total += entry.getValue().size();
            }
            for (Map.Entry<String, ArrayList<Long>> entry : sentFailedByType.entrySet()) {
                Summary summary = countsByType.computeIfAbsent(entry.getKey(), Summary::new);
                summary.sentFailed += entry.getValue().size();
                summary.total += entry.getValue().size();
            }
            return countsByType.values();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public static @NonNull String getStatsForDevice(@NonNull String deviceId) {

        cleanupIfNeeded();

        PacketStats packetStats = eventsByDevice.get(deviceId);
        if (packetStats == null) {
            return "";
        }

        StringBuilder ret = new StringBuilder();

        long timeInMillis = System.currentTimeMillis() - packetStats.createdAtMillis;
        if (timeInMillis > EVENT_KEEP_WINDOW_MILLIS) {
            timeInMillis = EVENT_KEEP_WINDOW_MILLIS;
        }
        long hours = TimeUnit.MILLISECONDS.toHours(timeInMillis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(timeInMillis) % 60;
        ret.append("From last ");
        ret.append(hours);
        ret.append("h ");
        ret.append(minutes);
        ret.append("m\n\n");

        ArrayList<PacketStats.Summary> counts = new ArrayList<>(packetStats.getSummaries());
        Collections.sort(counts, (o1, o2) -> Integer.compare(o2.total, o1.total)); // Sort them by total number of events

        for (PacketStats.Summary count : counts) {
            String name = count.packetType;
            if (name.startsWith("kdeconnect.")) {
                name = name.substring("kdeconnect.".length());
            }
            ret.append(name);
            ret.append("\n• ");
            ret.append(count.received);
            ret.append(" received\n• ");
            ret.append(count.sentSuccessful + count.sentFailed);
            ret.append(" sent (");
            ret.append(count.sentFailed);
            ret.append(" failed)\n");
        }

        return ret.toString();
    }

    public static void countReceived(@NonNull String deviceId, @NonNull String packetType) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
            return; // computeIfAbsent not present in API < 24
        }
        synchronized (DeviceStats.class) {
            eventsByDevice
                    .computeIfAbsent(deviceId, key -> new PacketStats())
                    .receivedByType
                    .computeIfAbsent(packetType, key -> new ArrayList<>())
                    .add(System.currentTimeMillis());
        }
        cleanupIfNeeded();
    }

    public static void countSent(@NonNull String deviceId, @NonNull String packetType, boolean success) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
            return; // computeIfAbsent not present in API < 24
        }
        if (success) {
            synchronized (DeviceStats.class) {
                eventsByDevice
                        .computeIfAbsent(deviceId, key -> new PacketStats())
                        .sentSuccessfulByType
                        .computeIfAbsent(packetType, key -> new ArrayList<>())
                        .add(System.currentTimeMillis());
            }
        } else {
            synchronized (DeviceStats.class) {
                eventsByDevice
                        .computeIfAbsent(deviceId, key -> new PacketStats())
                        .sentFailedByType
                        .computeIfAbsent(packetType, key -> new ArrayList<>())
                        .add(System.currentTimeMillis());
            }
        }
        cleanupIfNeeded();
    }

    private static void cleanupIfNeeded() {
        final long cutoutTimestamp = System.currentTimeMillis() - EVENT_KEEP_WINDOW_MILLIS;
        if (System.currentTimeMillis() > nextCleanup) {
            synchronized (DeviceStats.class) {
                Log.i("PacketStats", "Doing periodic cleanup");
                for (PacketStats de : eventsByDevice.values()) {
                    removeOldEvents(de.receivedByType, cutoutTimestamp);
                    removeOldEvents(de.sentFailedByType, cutoutTimestamp);
                    removeOldEvents(de.sentSuccessfulByType, cutoutTimestamp);
                }
                nextCleanup = System.currentTimeMillis() + CLEANUP_INTERVAL_MILLIS;
            }
        }
    }

    @VisibleForTesting
    static void removeOldEvents(HashMap<String, ArrayList<Long>> eventsByType, final long cutoutTimestamp) {

        Iterator<Map.Entry<String, ArrayList<Long>>> iterator = eventsByType.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ArrayList<Long>> entry = iterator.next();
            ArrayList<Long> events = entry.getValue();

            int index = Collections.binarySearch(events, cutoutTimestamp);
            if (index < 0) {
                index = -(index + 1); // Convert the negative index to insertion point
            }

            if (index < events.size()) {
                events.subList(0, index).clear();
            } else {
                iterator.remove(); // No element greater than the threshold
            }
        }
    }

}
