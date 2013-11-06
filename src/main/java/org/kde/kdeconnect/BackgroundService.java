package org.kde.kdeconnect;

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
import org.kde.kdeconnect.UserInterface.MainSettingsActivity;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class BackgroundService extends Service {

    private ArrayList<BaseLinkProvider> linkProviders = new ArrayList<BaseLinkProvider>();

    private HashMap<String, Device> devices = new HashMap<String, Device>();

    private Device.PairingCallback devicePairingCallback = new Device.PairingCallback() {
        @Override
        public void incomingRequest() {
            if (deviceListChangedCallback != null) deviceListChangedCallback.onDeviceListChanged();
        }
        @Override
        public void pairingSuccessful() {
            if (deviceListChangedCallback != null) deviceListChangedCallback.onDeviceListChanged();
        }
        @Override
        public void pairingFailed(String error) {
            if (deviceListChangedCallback != null) deviceListChangedCallback.onDeviceListChanged();
        }
        @Override
        public void unpaired() {
            if (deviceListChangedCallback != null) deviceListChangedCallback.onDeviceListChanged();
        }
    };

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

    public void registerLinkProviders() {

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

    private BaseLinkProvider.ConnectionReceiver deviceListener = new BaseLinkProvider.ConnectionReceiver() {
        @Override
        public void onConnectionReceived(final NetworkPackage identityPackage, final BaseLink link) {

            Log.i("BackgroundService", "Connection accepted!");

            String deviceId = identityPackage.getString("deviceId");

            Device device = devices.get(deviceId);

            if (device != null) {
                Log.i("BackgroundService", "addLink, known device: " + deviceId);
                device.addLink(identityPackage, link);
            } else {
                Log.i("BackgroundService", "addLink,unknown device: " + deviceId);
                String name = identityPackage.getString("deviceName");
                device = new Device(BackgroundService.this, identityPackage, link);
                devices.put(deviceId, device);
                device.addPairingCallback(devicePairingCallback);
            }

            if (deviceListChangedCallback != null) deviceListChangedCallback.onDeviceListChanged();
        }

        @Override
        public void onConnectionLost(BaseLink link) {
            Device d = devices.get(link.getDeviceId());
            Log.i("onConnectionLost", "removeLink, deviceId: " + link.getDeviceId());
            if (d != null) {
                d.removeLink(link);
                if (!d.isReachable() && !d.isPaired()) {
                    //Log.e("onConnectionLost","Removing connection device because it was not paired");
                    devices.remove(link.getDeviceId());
                    d.removePairingCallback(devicePairingCallback);
                }
            } else {
                Log.e("onConnectionLost","Removing connection to unknown device, this should not happen");
            }
            if (deviceListChangedCallback != null) deviceListChangedCallback.onDeviceListChanged();
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

    public interface DeviceListChangedCallback {
        void onDeviceListChanged();
    }
    private DeviceListChangedCallback deviceListChangedCallback = null;
    public void setDeviceListChangedCallback(DeviceListChangedCallback callback) {
        this.deviceListChangedCallback = callback;
    }


    //This will called only once, even if we launch the service intent several times
    @Override
    public void onCreate() {
        super.onCreate();

        // Register screen on listener
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        registerReceiver(new KdeConnectBroadcastReceiver(), filter);

        Log.i("BackgroundService","Service not started yet, initializing...");

        initializeRsaKeys();
        MainSettingsActivity.initializeDeviceName(this);
        loadRememberedDevicesFromSettings();
        registerLinkProviders();

        //Link Providers need to be already registered
        addConnectionListener(deviceListener);
        startDiscovery();

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
                Log.e("initializeRsaKeys","Exception");
                return;
            }

            byte[] publicKey = keyPair.getPublic().getEncoded();
            byte[] privateKey = keyPair.getPrivate().getEncoded();

            SharedPreferences.Editor edit = settings.edit();
            edit.putString("publicKey",Base64.encodeToString(publicKey, 0).trim()+"\n");
            edit.putString("privateKey",Base64.encodeToString(privateKey, 0));
            edit.commit();

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

    private static final Lock mutex = new ReentrantLock(true);

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
