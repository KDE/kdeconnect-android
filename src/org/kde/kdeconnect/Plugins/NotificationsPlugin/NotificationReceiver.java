/*
 * Copyright 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License or (at your option) version 3 or any later version
 * accepted by the membership of KDE e.V. (or its successor approved
 * by the membership of KDE e.V.), which shall act as a proxy
 * defined in Section 14 of version 3 of the license.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
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
