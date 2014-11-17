package org.kde.kdeconnect.Plugins.NotificationsPlugin;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class NotificationReceiver extends NotificationListenerService {

    public interface NotificationListener {
        void onNotificationPosted(StatusBarNotification statusBarNotification);
        void onNotificationRemoved(StatusBarNotification statusBarNotification);
    }

    private final ArrayList<NotificationListener> listeners = new ArrayList<NotificationListener>();

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

    public interface InstanceCallback {
        void onServiceStart(NotificationReceiver service);
    }

    private final static ArrayList<InstanceCallback> callbacks = new ArrayList<InstanceCallback>();

    private final static Lock mutex = new ReentrantLock(true);

    //This will be called for each intent launch, even if the service is already started and is reused
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //Log.e("NotificationReceiver", "onStartCommand");
        mutex.lock();
        for (InstanceCallback c : callbacks) {
            c.onServiceStart(this);
        }
        callbacks.clear();
        mutex.unlock();
        return Service.START_STICKY;
    }


    public static void Start(Context c) {
        RunCommand(c, null);
    }

    public static void RunCommand(Context c, final InstanceCallback callback) {
        if (callback != null) {
            mutex.lock();
            callbacks.add(callback);
            mutex.unlock();
        }
        Intent serviceIntent = new Intent(c, NotificationReceiver.class);
        c.startService(serviceIntent);
    }

}
