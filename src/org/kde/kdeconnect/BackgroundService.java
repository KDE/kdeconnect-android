/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.kde.kdeconnect.Backends.BaseLinkProvider;
import org.kde.kdeconnect.Backends.BluetoothBackend.BluetoothLinkProvider;
import org.kde.kdeconnect.Backends.LanBackend.LanLinkProvider;
import org.kde.kdeconnect.Helpers.NotificationHelper;
import org.kde.kdeconnect.Plugins.ClibpoardPlugin.ClipboardFloatingActivity;
import org.kde.kdeconnect.Plugins.RunCommandPlugin.RunCommandActivity;
import org.kde.kdeconnect.Plugins.RunCommandPlugin.RunCommandPlugin;
import org.kde.kdeconnect.Plugins.SharePlugin.SendFileActivity;
import org.kde.kdeconnect.UserInterface.MainActivity;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;

/*
 * This class (still) does 3 things:
 * - Keeps the app running by creating a foreground notification.
 * - Holds references to the active LinkProviders, but doesn't handle the DeviceLink those create (the KdeConnect class does that).
 * - Listens for network connectivity changes and tells the LinkProviders to re-check for devices.
 * It can be started by the KdeConnectBroadcastReceiver on some events or when the MainActivity is launched.
 */
public class BackgroundService extends Service {
    private static final int FOREGROUND_NOTIFICATION_ID = 1;

    private static BackgroundService instance;

    private KdeConnect applicationInstance;

    private final ArrayList<BaseLinkProvider> linkProviders = new ArrayList<>();

    public static BackgroundService getInstance() {
        return instance;
    }

    private static boolean initialized = false;

    // This indicates when connected over wifi/usb/bluetooth/(anything other than cellular)
    private final MutableLiveData<Boolean> connectedToNonCellularNetwork = new MutableLiveData<>();
    public LiveData<Boolean> isConnectedToNonCellularNetwork() {
        return connectedToNonCellularNetwork;
    }

    public void updateForegroundNotification() {
        if (NotificationHelper.isPersistentNotificationEnabled(this)) {
            //Update the foreground notification with the currently connected device list
            NotificationManager nm = ContextCompat.getSystemService(this, NotificationManager.class);
            nm.notify(FOREGROUND_NOTIFICATION_ID, createForegroundNotification());
        }
    }

    private void registerLinkProviders() {
        linkProviders.add(new LanLinkProvider(this));
//        linkProviders.add(new LoopbackLinkProvider(this));
        linkProviders.add(new BluetoothLinkProvider(this));
    }

    public void onNetworkChange(@Nullable Network network) {
        if (!initialized) {
            Log.d("KDE/BackgroundService", "ignoring onNetworkChange called before the service is initialized");
            return;
        }
        Log.d("KDE/BackgroundService", "onNetworkChange");
        for (BaseLinkProvider a : linkProviders) {
            a.onNetworkChange(network);
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

    //This will called only once, even if we launch the service intent several times
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("KdeConnect/BgService", "onCreate");
        instance = this;

        KdeConnect.getInstance().addDeviceListChangedCallback("BackgroundService", this::updateForegroundNotification);

        // Register screen on listener
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        // See: https://developer.android.com/reference/android/net/ConnectivityManager.html#CONNECTIVITY_ACTION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        }
        registerReceiver(new KdeConnectBroadcastReceiver(), filter);

        // Watch for changes on all network connections except cellular networks
        NetworkRequest.Builder networkRequestBuilder = getNonCellularNetworkRequestBuilder();
        ConnectivityManager cm = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        cm.registerNetworkCallback(networkRequestBuilder.build(), new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                Log.i("BackgroundService", "Valid network available");
                connectedToNonCellularNetwork.postValue(true);
                onNetworkChange(network);
            }
            @Override
            public void onLost(Network network) {
                Log.i("BackgroundService", "Valid network lost");
                connectedToNonCellularNetwork.postValue(false);
            }
        });

        applicationInstance = KdeConnect.getInstance();

        registerLinkProviders();
        addConnectionListener(applicationInstance.getConnectionListener()); // Link Providers need to be already registered
        for (BaseLinkProvider a : linkProviders) {
            a.onStart();
        }
        initialized = true;
    }

    private static NetworkRequest.Builder getNonCellularNetworkRequestBuilder() {
        NetworkRequest.Builder networkRequestBuilder = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_BLUETOOTH);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            networkRequestBuilder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE);
        }
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S) {
            networkRequestBuilder.addTransportType(NetworkCapabilities.TRANSPORT_USB)
                                 .addTransportType(NetworkCapabilities.TRANSPORT_LOWPAN);
        }
        return networkRequestBuilder;
    }

    public void changePersistentNotificationVisibility(boolean visible) {
        if (visible) {
            updateForegroundNotification();
        } else {
            Stop();
            Start(this);
        }
    }

    private Notification createForegroundNotification() {

        //Why is this needed: https://developer.android.com/guide/components/services#Foreground

        ArrayList<String> connectedDevices = new ArrayList<>();
        ArrayList<String> connectedDeviceIds = new ArrayList<>();
        for (Device device : applicationInstance.getDevices().values()) {
            if (device.isReachable() && device.isPaired()) {
                connectedDeviceIds.add(device.getDeviceId());
                connectedDevices.add(device.getName());
            }
        }

        Intent intent = new Intent(this, MainActivity.class);
        if (connectedDeviceIds.size() == 1) {
            // Force open screen of the only connected device
            intent.putExtra(MainActivity.EXTRA_DEVICE_ID, connectedDeviceIds.get(0));
        }

        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(this, NotificationHelper.Channels.PERSISTENT);
        notification
                .setSmallIcon(R.drawable.ic_notification)
                .setOngoing(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_MIN) //MIN so it's not shown in the status bar before Oreo, on Oreo it will be bumped to LOW
                .setShowWhen(false)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setAutoCancel(false);
        notification.setGroup("BackgroundService");

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            //Pre-oreo, the notification will have an empty title line without this
            notification.setContentTitle(getString(R.string.kde_connect));
        }

        if (connectedDevices.isEmpty()) {
            notification.setContentText(getString(R.string.foreground_notification_no_devices));
        } else {
            notification.setContentText(getString(R.string.foreground_notification_devices, TextUtils.join(", ", connectedDevices)));

            // Adding an action button to send clipboard manually in Android 10 and later.
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_DENIED) {
                Intent sendClipboard = ClipboardFloatingActivity.getIntent(this, true);
                PendingIntent sendPendingClipboard = PendingIntent.getActivity(this, 3, sendClipboard, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                notification.addAction(0, getString(R.string.foreground_notification_send_clipboard), sendPendingClipboard);
            }

            if (connectedDeviceIds.size() == 1) {
                String deviceId = connectedDeviceIds.get(0);
                Device device = KdeConnect.getInstance().getDevice(deviceId);
                if (device != null) {
                    // Adding two action buttons only when there is a single device connected.
                    // Setting up Send File Intent.
                    Intent sendFile = new Intent(this, SendFileActivity.class);
                    sendFile.putExtra("deviceId", deviceId);
                    PendingIntent sendPendingFile = PendingIntent.getActivity(this, 1, sendFile, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                    notification.addAction(0, getString(R.string.send_files), sendPendingFile);

                    // Checking if there are registered commands and adding the button.
                    RunCommandPlugin plugin = (RunCommandPlugin) device.getPlugin("RunCommandPlugin");
                    if (plugin != null && !plugin.getCommandList().isEmpty()) {
                        Intent runCommand = new Intent(this, RunCommandActivity.class);
                        runCommand.putExtra("deviceId", connectedDeviceIds.get(0));
                        PendingIntent runPendingCommand = PendingIntent.getActivity(this, 2, runCommand, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                        notification.addAction(0, getString(R.string.pref_plugin_runcommand), runPendingCommand);
                    }
                }
            }
        }
        return notification.build();
    }

    @Override
    public void onDestroy() {
        Log.d("KdeConnect/BgService", "onDestroy");
        initialized = false;
        for (BaseLinkProvider a : linkProviders) {
            a.onStop();
        }
        KdeConnect.getInstance().removeDeviceListChangedCallback("BackgroundService");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("KDE/BackgroundService", "onStartCommand");
        if (NotificationHelper.isPersistentNotificationEnabled(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            } else {
                startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification());
            }
        }
        if (intent != null && intent.getBooleanExtra("refresh", false)) {
            onNetworkChange(null);
        }
        return Service.START_STICKY;
    }

    public static void Start(Context context) {
        Log.d("KDE/BackgroundService", "Start");
        Intent intent = new Intent(context, BackgroundService.class);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void ForceRefreshConnections(Context context) {
        Log.d("KDE/BackgroundService", "ForceRefreshConnections");
        Intent intent = new Intent(context, BackgroundService.class);
        intent.putExtra("refresh", true);
        ContextCompat.startForegroundService(context, intent);
    }

    public void Stop() {
        stopForeground(true);
    }

}
