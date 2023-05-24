package org.kde.kdeconnect;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.kde.kdeconnect.Backends.BaseLink;
import org.kde.kdeconnect.Backends.BaseLinkProvider;
import org.kde.kdeconnect.Helpers.DeviceHelper;
import org.kde.kdeconnect.Helpers.LifecycleHelper;
import org.kde.kdeconnect.Helpers.NotificationHelper;
import org.kde.kdeconnect.Helpers.SecurityHelpers.RsaHelper;
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect.UserInterface.ThemeUtil;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/*
 * This class holds all the active devices and makes them accessible from every other class.
 * It also takes care of initializing all classes that need so when the app boots.
 * It provides a ConnectionReceiver that the BackgroundService uses to ping this class every time a new DeviceLink is created.
 */
public class KdeConnect extends Application {

    public interface DeviceListChangedCallback {
        void onDeviceListChanged();
    }

    private static KdeConnect instance = null;

    private final ConcurrentHashMap<String, Device> devices = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, DeviceListChangedCallback> deviceListChangedCallbacks = new ConcurrentHashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.d("KdeConnect/Application", "onCreate");
        ThemeUtil.setUserPreferredTheme(this);
        DeviceHelper.initializeDeviceId(this);
        RsaHelper.initialiseRsaKeys(this);
        SslHelper.initialiseCertificate(this);
        PluginFactory.initPluginInfo(this);
        NotificationHelper.initializeChannels(this);
        LifecycleHelper.initializeObserver();
        loadRememberedDevicesFromSettings();
    }

    @Override
    public void onTerminate() {
        Log.d("KdeConnect/Application", "onTerminate");
        super.onTerminate();
    }

    public void addDeviceListChangedCallback(String key, DeviceListChangedCallback callback) {
        deviceListChangedCallbacks.put(key, callback);
    }

    public void removeDeviceListChangedCallback(String key) {
        deviceListChangedCallbacks.remove(key);
    }

    private void onDeviceListChanged() {
        for (DeviceListChangedCallback callback : deviceListChangedCallbacks.values()) {
            callback.onDeviceListChanged();
        }
    }

    public ConcurrentHashMap<String, Device> getDevices() {
        return devices;
    }

    public Device getDevice(String id) {
        if (id == null) {
            return null;
        }
        return devices.get(id);
    }

    public <T extends Plugin> T getDevicePlugin(String deviceId, Class<T> pluginClass) {
        if (deviceId == null) {
            return null;
        }
        Device device = devices.get(deviceId);
        if (device == null) {
            return null;
        }
        return device.getPlugin(pluginClass);
    }

    public static KdeConnect getInstance() {
        return instance;
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

    private final BaseLinkProvider.ConnectionReceiver connectionListener = new BaseLinkProvider.ConnectionReceiver() {
        @Override
        public void onConnectionReceived(final NetworkPacket identityPacket, final BaseLink link) {
            String deviceId = identityPacket.getString("deviceId");
            Device device = devices.get(deviceId);
            if (device != null) {
                Log.i("KDE/Application", "addLink, known device: " + deviceId);
                device.addLink(identityPacket, link);
            } else {
                Log.i("KDE/Application", "addLink,unknown device: " + deviceId);
                device = new Device(KdeConnect.this, identityPacket, link);
                devices.put(deviceId, device);
                device.addPairingCallback(devicePairingCallback);
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
    public BaseLinkProvider.ConnectionReceiver getConnectionListener() {
        return connectionListener;
    }

}
