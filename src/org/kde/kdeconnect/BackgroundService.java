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

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

import org.kde.kdeconnect.Backends.BaseLink;
import org.kde.kdeconnect.Backends.BaseLinkProvider;
import org.kde.kdeconnect.Backends.LanBackend.LanLinkProvider;
import org.kde.kdeconnect.Backends.LoopbackBackend.LoopbackLinkProvider;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class BackgroundService extends Service {

    public interface DeviceListChangedCallback {
        void onDeviceListChanged();
    }

    private final ConcurrentHashMap<String, DeviceListChangedCallback> deviceListChangedCallbacks = new ConcurrentHashMap<>();

    private final ArrayList<BaseLinkProvider> linkProviders = new ArrayList<>();

    private final ConcurrentHashMap<String, Device> devices = new ConcurrentHashMap<>();

    private final HashSet<Object> discoveryModeAcquisitions = new HashSet<>();

    public boolean acquireDiscoveryMode(Object key) {
        boolean wasEmpty = discoveryModeAcquisitions.isEmpty();
        discoveryModeAcquisitions.add(key);
        if (wasEmpty) {
            onNetworkChange();
        }
        return wasEmpty;
    }

    public void releaseDiscoveryMode(Object key) {
        boolean removed = discoveryModeAcquisitions.remove(key);
        if (removed && discoveryModeAcquisitions.isEmpty()) {
            cleanDevices();
        }
    }

    public static void addGuiInUseCounter(Context activity) {
        addGuiInUseCounter(activity, false);
    }

    public static void addGuiInUseCounter(final Context activity, final boolean forceNetworkRefresh) {
        BackgroundService.RunCommand(activity, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                boolean refreshed = service.acquireDiscoveryMode(activity);
                if (!refreshed && forceNetworkRefresh) {
                    service.onNetworkChange();
                }
            }
        });
    }

    public static void removeGuiInUseCounter(final Context activity) {
        BackgroundService.RunCommand(activity, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                //If no user interface is open, close the connections open to other devices
                service.releaseDiscoveryMode(activity);
            }
        });
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

    private void onDeviceListChanged() {
        for(DeviceListChangedCallback callback : deviceListChangedCallbacks.values()) {
            callback.onDeviceListChanged();
        }
    }

    private void loadRememberedDevicesFromSettings() {
        //Log.e("BackgroundService", "Loading remembered trusted devices");
        SharedPreferences preferences = getSharedPreferences("trusted_devices", Context.MODE_PRIVATE);
        Set<String> trustedDevices = preferences.getAll().keySet();
        for(String deviceId : trustedDevices) {
            //Log.e("BackgroundService", "Loading device "+deviceId);
            if (preferences.getBoolean(deviceId, false)) {
                Device device = new Device(this, deviceId);
                devices.put(deviceId,device);
                device.addPairingCallback(devicePairingCallback);
            }
        }
    }

    private void registerLinkProviders() {

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

        //if (settings.getBoolean("loopback_link", true)) {
        //    linkProviders.add(new LoopbackLinkProvider(this));
        //}

        if (settings.getBoolean("lan_link", true)) {
            linkProviders.add(new LanLinkProvider(this));
        }

    }

    public Device getDevice(String id) {
        return devices.get(id);
    }

    private void cleanDevices() {
        for(Device d : devices.values()) {
            if (!d.isPaired() && !d.isPairRequested() && !d.isPairRequestedByOtherEnd() && d.getConnectionSource() == BaseLink.ConnectionStarted.Remotely) {
                d.disconnect();
            }
        }
    }

    private final BaseLinkProvider.ConnectionReceiver deviceListener = new BaseLinkProvider.ConnectionReceiver() {
        @Override
        public void onConnectionReceived(final NetworkPackage identityPackage, final BaseLink link) {

            String deviceId = identityPackage.getString("deviceId");

            Device device = devices.get(deviceId);

            if (device != null) {
                Log.i("KDE/BackgroundService", "addLink, known device: " + deviceId);
                device.addLink(identityPackage, link);
            } else {
                Log.i("KDE/BackgroundService", "addLink,unknown device: " + deviceId);
                device = new Device(BackgroundService.this, identityPackage, link);
                if (device.isPaired() || device.isPairRequested() || device.isPairRequestedByOtherEnd()
                        || link.getConnectionSource() == BaseLink.ConnectionStarted.Locally
                        ||!discoveryModeAcquisitions.isEmpty() )
                {
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

        // Register screen on listener
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        registerReceiver(new KdeConnectBroadcastReceiver(), filter);

        Log.i("KDE/BackgroundService", "Service not started yet, initializing...");

        initializeRsaKeys();
        loadRememberedDevicesFromSettings();
        registerLinkProviders();

        //Link Providers need to be already registered
        addConnectionListener(deviceListener);

        for (BaseLinkProvider a : linkProviders) {
            a.onStart();
        }

    }

    private void initializeRsaKeys() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

        if (!settings.contains("publicKey") || !settings.contains("privateKey")) {

            KeyPair keyPair;
            try {
                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
                keyGen.initialize(2048);
                keyPair = keyGen.genKeyPair();
            } catch(Exception e) {
                e.printStackTrace();
                Log.e("KDE/initializeRsaKeys","Exception");
                return;
            }

            byte[] publicKey = keyPair.getPublic().getEncoded();
            byte[] privateKey = keyPair.getPrivate().getEncoded();

            SharedPreferences.Editor edit = settings.edit();
            edit.putString("publicKey",Base64.encodeToString(publicKey, 0).trim()+"\n");
            edit.putString("privateKey",Base64.encodeToString(privateKey, 0));
            edit.apply();

        }


/*
        // Encryption and decryption test
        //================================

        try {

            NetworkPackage np = NetworkPackage.createIdentityPackage(this);

            SharedPreferences globalSettings = PreferenceManager.getDefaultSharedPreferences(this);

            byte[] publicKeyBytes = Base64.decode(globalSettings.getString("publicKey",""), 0);
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(publicKeyBytes));

            np.encrypt(publicKey);

            byte[] privateKeyBytes = Base64.decode(globalSettings.getString("privateKey",""), 0);
            PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));

            NetworkPackage decrypted = np.decrypt(privateKey);
            Log.e("ENCRYPTION AND DECRYPTION TEST", decrypted.serialize());
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("ENCRYPTION AND DECRYPTION TEST","Exception: "+e);
        }
*/

    }

    @Override
    public void onDestroy() {
        for (BaseLinkProvider a : linkProviders) {
            a.onStop();
        }
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
        return Service.START_STICKY;
    }

    public static void Start(Context c) {
        RunCommand(c, null);
    }

    public static void RunCommand(final Context c, final InstanceCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (callback != null) {
                    mutex.lock();
                    try {
                        callbacks.add(callback);
                    } finally {
                        mutex.unlock();
                    }
                }
                Intent serviceIntent = new Intent(c, BackgroundService.class);
                c.startService(serviceIntent);
            }
        }).start();
    }

}
