package org.kde.kdeconnect.Plugins.NotificationsPlugin;
 
import android.app.Notification;
import android.app.PendingIntent;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.UUID;

public class RepliableNotification {
    public String id = UUID.randomUUID().toString();
    public PendingIntent pendingIntent;
    public ArrayList<android.app.RemoteInput> remoteInputs = new ArrayList<>();
    public String packageName;
    public String tag;
}
