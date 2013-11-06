package org.kde.kdeconnect.Plugins.NotificationsPlugin;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.ArrayList;

public class NotificationReceiver extends NotificationListenerService {

    public interface NotificationListener {
        void onNotificationPosted(StatusBarNotification statusBarNotification);
        void onNotificationRemoved(StatusBarNotification statusBarNotification);
    }

    private ArrayList<NotificationListener> listeners = new ArrayList<NotificationListener>();

    public void addListener(NotificationListener listener) {
        listeners.add(listener);
    }
    public void removeListener(NotificationListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification statusBarNotification) {
        //Log.e("NotificationReceiver.onNotificationPosted","listeners: " + listeners.size());
        for(NotificationListener listener : listeners) {
            listener.onNotificationPosted(statusBarNotification);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification statusBarNotification) {
        for(NotificationListener listener : listeners) {
            listener.onNotificationRemoved(statusBarNotification);
        }
    }





    //To use the service from the outer (name)space

    //This will be called for each intent launch, even if the service is already started and is reused
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //Log.e("NotificationReceiver", "onStartCommand");
        for (InstanceCallback c : callbacks) {
            c.onServiceStart(this);
        }
        callbacks.clear();
        return Service.START_STICKY;
    }

    public interface InstanceCallback {
        void onServiceStart(NotificationReceiver service);
    }

    private static ArrayList<InstanceCallback> callbacks = new ArrayList<InstanceCallback>();

    public static void Start(Context c) {
        RunCommand(c, null);
    }

    public static void RunCommand(Context c, final InstanceCallback callback) {
        if (callback != null) callbacks.add(callback);
        Intent serviceIntent = new Intent(c, NotificationReceiver.class);
        c.startService(serviceIntent);
    }

}
