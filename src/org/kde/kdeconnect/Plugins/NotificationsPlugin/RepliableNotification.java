package org.kde.kdeconnect.Plugins.NotificationsPlugin;

import android.app.PendingIntent;

import java.util.ArrayList;
import java.util.UUID;

class RepliableNotification {
    final String id = UUID.randomUUID().toString();
    PendingIntent pendingIntent;
    final ArrayList<android.app.RemoteInput> remoteInputs = new ArrayList<>();
    String packageName;
    String tag;
}
