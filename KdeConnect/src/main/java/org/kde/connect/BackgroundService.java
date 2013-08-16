package org.kde.connect;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;

import org.kde.connect.ComputerLinks.BaseComputerLink;
import org.kde.connect.LinkProviders.BaseLinkProvider;
import org.kde.connect.LinkProviders.BroadcastTcpLinkProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class BackgroundService extends Service {

    private ArrayList<BaseLinkProvider> linkProviders = new ArrayList<BaseLinkProvider>();

    private HashMap<String, Device> devices = new HashMap<String, Device>();

    private void loadRememberedDevicesFromSettings() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        Set<String> trustedDevices = preferences.getStringSet("trusted", new HashSet<String>());
        for(String deviceId : trustedDevices) {
            Log.e("loadRememberedDevicesFromSettings",deviceId);
            devices.put(deviceId,new Device(getApplicationContext(), deviceId));
        }
    }

    public void registerLinkProviders() {

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

        if (settings.getBoolean("avahitcp_link", true)) {
            //linkProviders.add(new AvahiTcpLinkProvider(this));
        }

        if (settings.getBoolean("broadcasttcp_link", true)) {
            linkProviders.add(new BroadcastTcpLinkProvider(this));
        }

    }

    public Device getDevice(String id) {
        return devices.get(id);
    }

    private BaseLinkProvider.ConnectionReceiver deviceListener = new BaseLinkProvider.ConnectionReceiver() {
        @Override
        public void onConnectionReceived(final NetworkPackage identityPackage, final BaseComputerLink link) {

            Log.i("BackgroundService", "Connection accepted!");

            runOnMainThread(new Runnable() { //Some plugins that create Handlers will crash if started from a different thread!
                @Override
                public void run() {

                    String deviceId = identityPackage.getString("deviceId");
                    String name = identityPackage.getString("deviceName");

                    if (devices.containsKey(deviceId)) {
                        Log.i("BackgroundService", "known device");
                        Device device = devices.get(deviceId);
                        if (!device.hasName()) device.setName(identityPackage.getString("deviceName"));
                        device.addLink(link);
                    } else {
                        Log.i("BackgroundService", "unknown device");
                        Device device = new Device(getApplicationContext(), deviceId, name, link);
                        devices.put(deviceId, device);
                    }
                }
            });

        }

        @Override
        public void onConnectionLost(BaseComputerLink link) {
            Device d = devices.get(link.getDeviceId());
            if (d != null) {
                d.removeLink(link);
                if (!d.isReachable() && !d.isTrusted()) {
                    devices.remove(link.getDeviceId());
                }
            }

        }
    };

    public HashMap<String, Device> getDevices() {
        return devices;
    }

    //This will be called for each intent launch, even if the service is already started and is reused
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("BackgroundService","onStartCommand");
        for (InstanceCallback c : callbacks) {
            c.onServiceStart(this);
        }
        callbacks.clear();
        return Service.START_STICKY;
    }

    public void startDiscovery() {
        Log.i("BackgroundService","StartDiscovery");
        for (BaseLinkProvider a : linkProviders) {
            a.onStart();
        }
    }

    public void stopDiscovery() {
        Log.i("BackgroundService","StopDiscovery");
        for (BaseLinkProvider a : linkProviders) {
            a.onStop();
        }
    }

    public void onNetworkChange() {
        Log.i("BackgroundService","OnNetworkChange");
        for (BaseLinkProvider a : linkProviders) {
            a.onNetworkChange();
        }
    }

    public void addConnectionListener(BaseLinkProvider.ConnectionReceiver cr) {
        Log.i("BackgroundService","Registering connection listener");
        for (BaseLinkProvider a : linkProviders) {
            a.addConnectionReceiver(cr);
        }
    }

    //This will called only once, even if we launch the service intent several times
    @Override
    public void onCreate() {
        super.onCreate();

        // Register screen on listener
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        registerReceiver(new KdeConnectBroadcastReceiver(), filter);

        Log.i("BackgroundService","Service not started yet, initializing...");

        loadRememberedDevicesFromSettings();
        registerLinkProviders();

        //Link Providers need to be already registered
        addConnectionListener(deviceListener);
        startDiscovery();

    }

    @Override
    public void onDestroy() {
        Log.i("BackgroundService", "Destroying");
        stopDiscovery();
        super.onDestroy();
    }

    @Override
    public IBinder onBind (Intent intent) {
        return new Binder();
    }


    //To use the service from the gui

    public interface InstanceCallback {
        void onServiceStart(BackgroundService service);
    }

    private static ArrayList<InstanceCallback> callbacks = new ArrayList<InstanceCallback>();

    public static void Start(Context c) {
        RunCommand(c, null);
    }

    public static void RunCommand(Context c, final InstanceCallback callback) {
        if (callback != null) callbacks.add(callback);
        Intent serviceIntent = new Intent(c, BackgroundService.class);
        c.startService(serviceIntent);
    }


    Handler mainHandler = new Handler(Looper.getMainLooper());
    private void runOnMainThread(Runnable runnable) {
        mainHandler.post(runnable);
    }

}
