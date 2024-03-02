/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import org.kde.kdeconnect.Backends.BaseLink;
import org.kde.kdeconnect.Backends.BaseLinkProvider;
import org.kde.kdeconnect.Helpers.DeviceHelper;
import org.kde.kdeconnect.Helpers.LifecycleHelper;
import org.kde.kdeconnect.Helpers.NotificationHelper;
import org.kde.kdeconnect.Helpers.SecurityHelpers.RsaHelper;
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect.Plugins.SharePlugin.SharePlugin;
import org.kde.kdeconnect.UserInterface.ThemeUtil;
import org.kde.kdeconnect_tp.BuildConfig;
import org.slf4j.impl.HandroidLoggerAdapter;

import java.net.URISyntaxException;
import java.security.cert.CertificateException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/*
 * This class holds all the active devices and makes them accessible from every other class.
 * It also takes care of initializing all classes that need so when the app boots.
 * It provides a ConnectionReceiver that the BackgroundService uses to ping this class every time a new DeviceLink is created.
 */
public class KdeConnect extends Application {

    public static final String KEY_UNREACHABLE_URL_LIST = "key_unreachable_url_list";

    private SharedPreferences mSharedPrefs;

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
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences (this);
        setupSL4JLogging();
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

    private void setupSL4JLogging() {
        HandroidLoggerAdapter.DEBUG = BuildConfig.DEBUG;
        HandroidLoggerAdapter.ANDROID_API_LEVEL = Build.VERSION.SDK_INT;
        HandroidLoggerAdapter.APP_NAME = "KDEConnect";
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
        Log.i("MainActivity","Device list changed, notifying "+ deviceListChangedCallbacks.size() +" observers.");
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
                try {
                    Device device = new Device(this, deviceId);
                    devices.put(deviceId, device);
                    device.addPairingCallback(devicePairingCallback);
                } catch (CertificateException e) {
                    Log.w("KdeConnect", "Couldn't load the certificate for a remembered device. Removing from trusted list.");
                    e.printStackTrace();
                    preferences.edit().remove(deviceId).apply();
                }
            }
        }
    }

    private final PairingHandler.PairingCallback devicePairingCallback = new PairingHandler.PairingCallback() {
        @Override
        public void incomingPairRequest() {
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
        public void onConnectionReceived(@NonNull final BaseLink link) {
            Device device = devices.get(link.getDeviceId());
            if (device != null) {
                device.addLink(link);
                // Deliver URLs previously shared to this device now that it's connected
                deliverPreviouslySentIntents(device);
            } else {
                device = new Device(KdeConnect.this, link);
                devices.put(link.getDeviceId(), device);
                device.addPairingCallback(devicePairingCallback);
            }
            onDeviceListChanged();
        }

        @Override
        public void onConnectionLost(BaseLink link) {
            Device device = devices.get(link.getDeviceId());
            Log.i("KDE/onConnectionLost", "removeLink, deviceId: " + link.getDeviceId());
            if (device != null) {
                device.removeLink(link);
                if (!device.isReachable() && !device.isPaired()) {
                    //Log.e("onConnectionLost","Removing connection device because it was not paired");
                    devices.remove(link.getDeviceId());
                    device.removePairingCallback(devicePairingCallback);
                }
            } else {
                Log.d("KDE/onConnectionLost","Removing connection to unknown device");
            }
            onDeviceListChanged();
        }
    };

    private void deliverPreviouslySentIntents(Device device) {
        Set<String> currentUrlSet = mSharedPrefs.getStringSet(KEY_UNREACHABLE_URL_LIST + device.getDeviceId(), null);
        SharePlugin plugin = getDevicePlugin(device.getDeviceId(), SharePlugin.class);
        if (currentUrlSet != null && plugin != null) {
            for (String url : currentUrlSet) {
                Intent intent;
                try {
                    intent = Intent.parseUri(url, 0);
                } catch (URISyntaxException ex) {
                    Log.e("KDE/deliverPreviouslySentIntents", "Malformed URI");
                    continue;
                }
                if (intent != null) {
                    plugin.share(intent);
                }
            }
            mSharedPrefs.edit().putStringSet(KEY_UNREACHABLE_URL_LIST + device.getDeviceId(), null).apply();
        }
    }

    public BaseLinkProvider.ConnectionReceiver getConnectionListener() {
        return connectionListener;
    }

}
