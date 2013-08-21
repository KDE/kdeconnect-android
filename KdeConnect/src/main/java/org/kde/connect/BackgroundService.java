package org.kde.connect;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import org.kde.connect.ComputerLinks.BaseComputerLink;
import org.kde.connect.LinkProviders.BaseLinkProvider;
import org.kde.connect.LinkProviders.BroadcastTcpLinkProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class BackgroundService extends Service {

    private ArrayList<BaseLinkProvider> linkProviders = new ArrayList<BaseLinkProvider>();

    private HashMap<String, Device> devices = new HashMap<String, Device>();

    private void loadRememberedDevicesFromSettings() {
        SharedPreferences preferences = getSharedPreferences("trusted_devices", Context.MODE_PRIVATE);
        Set<String> trustedDevices = preferences.getAll().keySet();
        for(String deviceId : trustedDevices) {
            if (preferences.getBoolean(deviceId, false)) {
                devices.put(deviceId,new Device(getBaseContext(), deviceId));
            }
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

            Log.e("BackgroundService", "Connection accepted!");

            String deviceId = identityPackage.getString("deviceId");

            Device device = devices.get(deviceId);

            if (device != null) {
                Log.e("BackgroundService", "addLink, known device: "+deviceId);
                if (!device.hasName()) device.setName(identityPackage.getString("deviceName"));
                device.addLink(link);
            } else {
                Log.e("BackgroundService", "addLink,unknown device: "+deviceId);
                String name = identityPackage.getString("deviceName");
                device = new Device(getBaseContext(), deviceId, name, link);
                devices.put(deviceId, device);
            }
        }

        @Override
        public void onConnectionLost(BaseComputerLink link) {
            Device d = devices.get(link.getDeviceId());
            Log.e("onConnectionLost","removeLink, deviceId: "+link.getDeviceId());
            if (d != null) {
                d.removeLink(link);
                if (!d.isReachable() && !d.isTrusted()) {
                    devices.remove(link.getDeviceId());
                }
            } else {
                Log.e("onConnectionLost","Removing connection to unknown device, this should not happen");
            }

        }
    };

    public HashMap<String, Device> getDevices() {
        return devices;
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

    public void removeConnectionListener(BaseLinkProvider.ConnectionReceiver cr) {
        for (BaseLinkProvider a : linkProviders) {
            a.removeConnectionReceiver(cr);
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

    private final Lock mutex = new ReentrantLock(true);

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //This will be called for each intent launch, even if the service is already started and it is reused
        Log.i("BackgroundService","onStartCommand");
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
        Intent serviceIntent = new Intent(c, BackgroundService.class);
        c.startService(serviceIntent);
    }

}
