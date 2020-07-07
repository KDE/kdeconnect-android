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

package org.kde.kdeconnect;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.kde.kdeconnect.Backends.BaseLink;
import org.kde.kdeconnect.Backends.BaseLinkProvider;
import org.kde.kdeconnect.Backends.LanBackend.LanLinkProvider;
import org.kde.kdeconnect.Helpers.NotificationHelper;
import org.kde.kdeconnect.Helpers.SecurityHelpers.RsaHelper;
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper;
import org.kde.kdeconnect.Plugins.ClibpoardPlugin.ClipboardFloatingActivity;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect.Plugins.RunCommandPlugin.RunCommandActivity;
import org.kde.kdeconnect.Plugins.RunCommandPlugin.RunCommandPlugin;
import org.kde.kdeconnect.Plugins.SharePlugin.SendFileActivity;
import org.kde.kdeconnect.UserInterface.MainActivity;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

//import org.kde.kdeconnect.Backends.BluetoothBackend.BluetoothLinkProvider;

public class BackgroundService extends Service {
    private static final int FOREGROUND_NOTIFICATION_ID = 1;

    private static BackgroundService instance;

    public interface DeviceListChangedCallback {
        void onDeviceListChanged();
    }

    public interface PluginCallback<T extends Plugin>  {
        void run(T plugin);
    }

    private final ConcurrentHashMap<String, DeviceListChangedCallback> deviceListChangedCallbacks = new ConcurrentHashMap<>();

    private final ArrayList<BaseLinkProvider> linkProviders = new ArrayList<>();

    private final ConcurrentHashMap<String, Device> devices = new ConcurrentHashMap<>();

    private final HashSet<Object> discoveryModeAcquisitions = new HashSet<>();

    public static BackgroundService getInstance() {
        return instance;
    }

    private boolean acquireDiscoveryMode(Object key) {
        boolean wasEmpty = discoveryModeAcquisitions.isEmpty();
        discoveryModeAcquisitions.add(key);
        if (wasEmpty) {
            onNetworkChange();
        }
        //Log.e("acquireDiscoveryMode",key.getClass().getName() +" ["+discoveryModeAcquisitions.size()+"]");
        return wasEmpty;
    }

    private void releaseDiscoveryMode(Object key) {
        boolean removed = discoveryModeAcquisitions.remove(key);
        //Log.e("releaseDiscoveryMode",key.getClass().getName() +" ["+discoveryModeAcquisitions.size()+"]");
        if (removed && discoveryModeAcquisitions.isEmpty()) {
            cleanDevices();
        }
    }

    private boolean isInDiscoveryMode() {
        //return !discoveryModeAcquisitions.isEmpty();
        return true; // Keep it always on for now
    }

    private final Device.PairingCallback devicePairingCallback = new Device.PairingCallback() {
        @Override
        public void incomingRequest() {
            onDeviceListChanged();
        }

        @Override
        public void pairingSuccessful() {
            onDeviceListChanged();
        }

        @Override
        public void pairingFailed(String error) {
            onDeviceListChanged();
        }

        @Override
        public void unpaired() {
            onDeviceListChanged();
        }
    };

    public void onDeviceListChanged() {
        for (DeviceListChangedCallback callback : deviceListChangedCallbacks.values()) {
            callback.onDeviceListChanged();
        }


        if (NotificationHelper.isPersistentNotificationEnabled(this)) {
            //Update the foreground notification with the currently connected device list
            NotificationManager nm = ContextCompat.getSystemService(this, NotificationManager.class);
            nm.notify(FOREGROUND_NOTIFICATION_ID, createForegroundNotification());
        }
    }

    private void loadRememberedDevicesFromSettings() {
        //Log.e("BackgroundService", "Loading remembered trusted devices");
        SharedPreferences preferences = getSharedPreferences("trusted_devices", Context.MODE_PRIVATE);
        Set<String> trustedDevices = preferences.getAll().keySet();
        for (String deviceId : trustedDevices) {
            //Log.e("BackgroundService", "Loading device "+deviceId);
            if (preferences.getBoolean(deviceId, false)) {
                Device device = new Device(this, deviceId);
                devices.put(deviceId, device);
                device.addPairingCallback(devicePairingCallback);
            }
        }
    }

    private void registerLinkProviders() {
        linkProviders.add(new LanLinkProvider(this));
//        linkProviders.add(new LoopbackLinkProvider(this));
//        linkProviders.add(new BluetoothLinkProvider(this));
    }

    public ArrayList<BaseLinkProvider> getLinkProviders() {
        return linkProviders;
    }

    public Device getDevice(String id) {
        return devices.get(id);
    }

    private void cleanDevices() {
        new Thread(() -> {
            for (Device d : devices.values()) {
                if (!d.isPaired() && !d.isPairRequested() && !d.isPairRequestedByPeer() && !d.deviceShouldBeKeptAlive()) {
                    d.disconnect();
                }
            }
        }).start();
    }

    private final BaseLinkProvider.ConnectionReceiver deviceListener = new BaseLinkProvider.ConnectionReceiver() {
        @Override
        public void onConnectionReceived(final NetworkPacket identityPacket, final BaseLink link) {

            String deviceId = identityPacket.getString("deviceId");

            Device device = devices.get(deviceId);

            if (device != null) {
                Log.i("KDE/BackgroundService", "addLink, known device: " + deviceId);
                device.addLink(identityPacket, link);
            } else {
                Log.i("KDE/BackgroundService", "addLink,unknown device: " + deviceId);
                device = new Device(BackgroundService.this, identityPacket, link);
                if (device.isPaired() || device.isPairRequested() || device.isPairRequestedByPeer()
                        || link.linkShouldBeKeptAlive()
                        || isInDiscoveryMode()) {
                    devices.put(deviceId, device);
                    device.addPairingCallback(devicePairingCallback);
                } else {
                    device.disconnect();
                }
            }

            onDeviceListChanged();
        }

        @Override
        public void onConnectionLost(BaseLink link) {
            Device d = devices.get(link.getDeviceId());
            Log.i("KDE/onConnectionLost", "removeLink, deviceId: " + link.getDeviceId());
            if (d != null) {
                d.removeLink(link);
                if (!d.isReachable() && !d.isPaired()) {
                    //Log.e("onConnectionLost","Removing connection device because it was not paired");
                    devices.remove(link.getDeviceId());
                    d.removePairingCallback(devicePairingCallback);
                }
            } else {
                //Log.d("KDE/onConnectionLost","Removing connection to unknown device");
            }
            onDeviceListChanged();
        }
    };

    public ConcurrentHashMap<String, Device> getDevices() {
        return devices;
    }

    public void onNetworkChange() {
        for (BaseLinkProvider a : linkProviders) {
            a.onNetworkChange();
        }
    }

    public void addConnectionListener(BaseLinkProvider.ConnectionReceiver cr) {
        for (BaseLinkProvider a : linkProviders) {
            a.addConnectionReceiver(cr);
        }
    }

    public void removeConnectionListener(BaseLinkProvider.ConnectionReceiver cr) {
        for (BaseLinkProvider a : linkProviders) {
            a.removeConnectionReceiver(cr);
        }
    }

    public void addDeviceListChangedCallback(String key, DeviceListChangedCallback callback) {
        deviceListChangedCallbacks.put(key, callback);
    }

    public void removeDeviceListChangedCallback(String key) {
        deviceListChangedCallbacks.remove(key);
    }

    //This will called only once, even if we launch the service intent several times
    @Override
    public void onCreate() {
        super.onCreate();

        instance = this;

        // Register screen on listener
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        // See: https://developer.android.com/reference/android/net/ConnectivityManager.html#CONNECTIVITY_ACTION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        }
        registerReceiver(new KdeConnectBroadcastReceiver(), filter);

        Log.i("KDE/BackgroundService", "Service not started yet, initializing...");

        PluginFactory.initPluginInfo(getBaseContext());
        initializeSecurityParameters();
        NotificationHelper.initializeChannels(this);
        loadRememberedDevicesFromSettings();
        migratePluginSettings();
        registerLinkProviders();

        //Link Providers need to be already registered
        addConnectionListener(deviceListener);

        for (BaseLinkProvider a : linkProviders) {
            a.onStart();
        }
    }

    private void migratePluginSettings() {
        SharedPreferences globalPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        for (String pluginKey : PluginFactory.getAvailablePlugins()) {
            if (PluginFactory.getPluginInfo(pluginKey).supportsDeviceSpecificSettings()) {
                Iterator<Device> it = devices.values().iterator();

                while (it.hasNext()) {
                    Device device = it.next();
                    Plugin plugin = PluginFactory.instantiatePluginForDevice(getBaseContext(), pluginKey, device);

                    if (plugin == null) {
                        continue;
                    }

                    plugin.copyGlobalToDeviceSpecificSettings(globalPrefs);
                    if (!it.hasNext()) {
                        plugin.removeSettings(globalPrefs);
                    }
                }
            }
        }
    }

    public void changePersistentNotificationVisibility(boolean visible) {
        NotificationManager nm = ContextCompat.getSystemService(this, NotificationManager.class);
        if (visible) {
            nm.notify(FOREGROUND_NOTIFICATION_ID, createForegroundNotification());
        } else {
            stopForeground(true);
            Start(this);
        }
    }

    private Notification createForegroundNotification() {

        //Why is this needed: https://developer.android.com/guide/components/services#Foreground

        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(this, NotificationHelper.Channels.PERSISTENT);
        notification
                .setSmallIcon(R.drawable.ic_notification)
                .setOngoing(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_MIN) //MIN so it's not shown in the status bar before Oreo, on Oreo it will be bumped to LOW
                .setShowWhen(false)
                .setAutoCancel(false);
        notification.setGroup("BackgroundService");

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            //Pre-oreo, the notification will have an empty title line without this
            notification.setContentTitle(getString(R.string.kde_connect));
        }

        ArrayList<String> connectedDevices = new ArrayList<>();
        ArrayList<String> connectedDeviceIds = new ArrayList<>();
        for (Device device : getDevices().values()) {
            if (device.isReachable() && device.isPaired()) {
                connectedDeviceIds.add(device.getDeviceId());
                connectedDevices.add(device.getName());
            }
        }

        if (connectedDevices.isEmpty()) {
            notification.setContentText(getString(R.string.foreground_notification_no_devices));
        } else {
            notification.setContentText(getString(R.string.foreground_notification_devices, TextUtils.join(", ", connectedDevices)));

            // Adding an action button to send clipboard manually in Android 10 and later.
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                Intent sendClipboard = new Intent(this, ClipboardFloatingActivity.class);
                sendClipboard.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                sendClipboard.putExtra("connectedDeviceIds", connectedDeviceIds);
                PendingIntent sendPendingClipboard = PendingIntent.getActivity(this, 3, sendClipboard, PendingIntent.FLAG_UPDATE_CURRENT);
                notification.addAction(0, getString(R.string.foreground_notification_send_clipboard), sendPendingClipboard);
            }

            if (connectedDeviceIds.size() == 1) {
                // Adding two action buttons only when there is a single device connected.
                // Setting up Send File Intent.
                Intent sendFile = new Intent(this, SendFileActivity.class);
                sendFile.putExtra("deviceId", connectedDeviceIds.get(0));
                PendingIntent sendPendingFile = PendingIntent.getActivity(this, 1, sendFile, PendingIntent.FLAG_UPDATE_CURRENT);
                notification.addAction(0, getString(R.string.send_files), sendPendingFile);

                // Checking if there are registered commands and adding the button.
                Device device = getDevice(connectedDeviceIds.get(0));
                RunCommandPlugin plugin = (RunCommandPlugin) device.getPlugin("RunCommandPlugin");
                if (plugin != null && !plugin.getCommandList().isEmpty()) {
                    Intent runCommand = new Intent(this, RunCommandActivity.class);
                    runCommand.putExtra("deviceId", connectedDeviceIds.get(0));
                    PendingIntent runPendingCommand = PendingIntent.getActivity(this, 2, runCommand, PendingIntent.FLAG_UPDATE_CURRENT);
                    notification.addAction(0, getString(R.string.pref_plugin_runcommand), runPendingCommand);
                }
            }
        }
        return notification.build();
    }

    private void initializeSecurityParameters() {
        RsaHelper.initialiseRsaKeys(this);
        SslHelper.initialiseCertificate(this);
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        for (BaseLinkProvider a : linkProviders) {
            a.onStop();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new Binder();
    }


    //To use the service from the gui

    public interface InstanceCallback {
        void onServiceStart(BackgroundService service);
    }

    private final static ArrayList<InstanceCallback> callbacks = new ArrayList<>();

    private final static Lock mutex = new ReentrantLock(true);

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //This will be called for each intent launch, even if the service is already started and it is reused
        mutex.lock();
        try {
            for (InstanceCallback c : callbacks) {
                c.onServiceStart(this);
            }
            callbacks.clear();
        } finally {
            mutex.unlock();
        }

        if (NotificationHelper.isPersistentNotificationEnabled(this)) {
            startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification());
        }
        return Service.START_STICKY;
    }

    private static void Start(Context c) {
        RunCommand(c, null);
    }

    public static void RunCommand(final Context c, final InstanceCallback callback) {
        new Thread(() -> {
            if (callback != null) {
                mutex.lock();
                try {
                    callbacks.add(callback);
                } finally {
                    mutex.unlock();
                }
            }
            ContextCompat.startForegroundService(c, new Intent(c, BackgroundService.class));
        }).start();
    }

    public static <T extends Plugin> void RunWithPlugin(final Context c, final String deviceId, final Class<T> pluginClass, final PluginCallback<T> cb) {
        RunCommand(c, service -> {
            Device device = service.getDevice(deviceId);

            if (device == null) {
                Log.e("BackgroundService", "Device " + deviceId + " not found");
                return;
            }

            final T plugin = device.getPlugin(pluginClass);

            if (plugin == null) {
                Log.e("BackgroundService", "Device " + device.getName() + " does not have plugin " + pluginClass.getName());
                return;
            }
            cb.run(plugin);
        });
    }
}
