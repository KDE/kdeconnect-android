/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Plugins.NotificationsPlugin;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class NotificationReceiver extends NotificationListenerService {

    private boolean connected;

    public interface NotificationListener {
        void onNotificationPosted(StatusBarNotification statusBarNotification);

        void onNotificationRemoved(StatusBarNotification statusBarNotification);

        void onListenerConnected(NotificationReceiver service);
    }

    private final ArrayList<NotificationListener> listeners = new ArrayList<>();

    public void addListener(NotificationListener listener) {
        listeners.add(listener);
    }

    public void removeListener(NotificationListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification statusBarNotification) {
        //Log.e("NotificationReceiver.onNotificationPosted","listeners: " + listeners.size());
        for (NotificationListener listener : listeners) {
            listener.onNotificationPosted(statusBarNotification);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification statusBarNotification) {
        for (NotificationListener listener : listeners) {
            listener.onNotificationRemoved(statusBarNotification);
        }
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        for (NotificationListener listener : listeners) {
            listener.onListenerConnected(this);
        }
        connected = true;
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        connected = false;
    }

    public boolean isConnected() {
        return connected;
    }

    //To use the service from the outer (name)space

    public interface InstanceCallback {
        void onServiceStart(NotificationReceiver service);
    }

    private final static ArrayList<InstanceCallback> callbacks = new ArrayList<>();

    private final static Lock mutex = new ReentrantLock(true);

    //This will be called for each intent launch, even if the service is already started and is reused
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //Log.e("NotificationReceiver", "onStartCommand");
        mutex.lock();
        try {
            for (InstanceCallback c : callbacks) {
                c.onServiceStart(this);
            }
            callbacks.clear();
        } finally {
            mutex.unlock();
        }
        return Service.START_STICKY;
    }


    public static void Start(Context c) {
        RunCommand(c, null);
    }

    public static void RunCommand(Context c, final InstanceCallback callback) {
        if (callback != null) {
            mutex.lock();
            try {
                callbacks.add(callback);
            } finally {
                mutex.unlock();
            }
        }
        Intent serviceIntent = new Intent(c, NotificationReceiver.class);
        c.startService(serviceIntent);
    }

}
