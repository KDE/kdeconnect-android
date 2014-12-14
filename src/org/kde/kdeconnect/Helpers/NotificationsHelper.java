package org.kde.kdeconnect.Helpers;

import java.util.concurrent.atomic.AtomicInteger;

public class NotificationsHelper {

    private final static AtomicInteger c = new AtomicInteger((int)System.currentTimeMillis());
    public static int getUniqueId() {
        return c.incrementAndGet();
    }

}
