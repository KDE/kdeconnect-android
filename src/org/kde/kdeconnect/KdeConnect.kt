/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect

import android.app.Application
import android.os.Build
import android.util.Log
import org.kde.kdeconnect.Backends.BaseLink
import org.kde.kdeconnect.Backends.BaseLinkProvider.ConnectionReceiver
import org.kde.kdeconnect.Helpers.DeviceHelper
import org.kde.kdeconnect.Helpers.LifecycleHelper
import org.kde.kdeconnect.Helpers.NotificationHelper
import org.kde.kdeconnect.Helpers.SecurityHelpers.RsaHelper
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper
import org.kde.kdeconnect.PairingHandler.PairingCallback
import org.kde.kdeconnect.Plugins.Plugin
import org.kde.kdeconnect.Plugins.PluginFactory
import org.kde.kdeconnect.UserInterface.ThemeUtil
import org.kde.kdeconnect_tp.BuildConfig
import org.slf4j.impl.HandroidLoggerAdapter
import java.security.cert.CertificateException
import java.util.concurrent.ConcurrentHashMap

/*
 * This class holds all the active devices and makes them accessible from every other class.
 * It also takes care of initializing all classes that need so when the app boots.
 * It provides a ConnectionReceiver that the BackgroundService uses to ping this class every time a new DeviceLink is created.
 */
class KdeConnect : Application() {
    fun interface DeviceListChangedCallback {
        fun onDeviceListChanged()
    }

    val devices: ConcurrentHashMap<String, Device> = ConcurrentHashMap()

    private val deviceListChangedCallbacks = ConcurrentHashMap<String, DeviceListChangedCallback>()

    override fun onCreate() {
        super.onCreate()
        _instance = this
        setupSL4JLogging()
        Log.d("KdeConnect/Application", "onCreate")
        ThemeUtil.setUserPreferredTheme(this)
        DeviceHelper.initializeDeviceId(this)
        RsaHelper.initialiseRsaKeys(this)
        SslHelper.initialiseCertificate(this)
        PluginFactory.initPluginInfo(this)
        NotificationHelper.initializeChannels(this)
        LifecycleHelper.initializeObserver()
        loadRememberedDevicesFromSettings()
    }

    private fun setupSL4JLogging() {
        HandroidLoggerAdapter.DEBUG = BuildConfig.DEBUG
        HandroidLoggerAdapter.ANDROID_API_LEVEL = Build.VERSION.SDK_INT
        HandroidLoggerAdapter.APP_NAME = "KDEConnect"
    }

    override fun onTerminate() {
        Log.d("KdeConnect/Application", "onTerminate")
        super.onTerminate()
    }

    fun addDeviceListChangedCallback(key: String, callback: DeviceListChangedCallback) {
        deviceListChangedCallbacks[key] = callback
    }

    fun removeDeviceListChangedCallback(key: String) {
        deviceListChangedCallbacks.remove(key)
    }

    private fun onDeviceListChanged() {
        Log.i("MainActivity", "Device list changed, notifying ${deviceListChangedCallbacks.size} observers.")
        deviceListChangedCallbacks.values.forEach(DeviceListChangedCallback::onDeviceListChanged)
    }

    fun getDevice(id: String?): Device? {
        if (id == null) {
            return null
        }
        return devices[id]
    }

    fun <T : Plugin> getDevicePlugin(deviceId: String?, pluginClass: Class<T>): T? {
        val device = getDevice(deviceId)
        return device?.getPlugin(pluginClass)
    }

    private fun loadRememberedDevicesFromSettings() {
        // Log.e("BackgroundService", "Loading remembered trusted devices");
        val preferences = getSharedPreferences("trusted_devices", MODE_PRIVATE)
        val trustedDevices: Set<String> = preferences.all.keys
        trustedDevices.map { id ->
            Log.d("KdeConnect", "Loading device $id")
            id
        }.filter { preferences.getBoolean(it, false) }.forEach {
            try {
                val device = Device(applicationContext, it)
                devices[it] = device
                device.addPairingCallback(devicePairingCallback)
            } catch (e: CertificateException) {
                Log.w(
                    "KdeConnect",
                    "Couldn't load the certificate for a remembered device. Removing from trusted list.", e
                )
                preferences.edit().remove(it).apply()
            }
        }
    }

    private val devicePairingCallback: PairingCallback = object : PairingCallback {
        override fun incomingPairRequest() {
            onDeviceListChanged()
        }

        override fun pairingSuccessful() {
            onDeviceListChanged()
        }

        override fun pairingFailed(error: String) {
            onDeviceListChanged()
        }

        override fun unpaired() {
            onDeviceListChanged()
        }
    }

    val connectionListener: ConnectionReceiver = object : ConnectionReceiver {
        override fun onConnectionReceived(link: BaseLink) {
            var device = devices[link.deviceId]
            if (device != null) {
                device.addLink(link)
            } else {
                device = Device(this@KdeConnect, link)
                devices[link.deviceId] = device
                device.addPairingCallback(devicePairingCallback)
            }
            onDeviceListChanged()
        }

        override fun onConnectionLost(link: BaseLink) {
            val device = devices[link.deviceId]
            Log.i("KDE/onConnectionLost", "removeLink, deviceId: ${link.deviceId}")
            if (device != null) {
                device.removeLink(link)
                // FIXME: I commented out the code below that removes the Device from the `devices` array
                //        because it didn't succeed in getting the Device garbage collected anyway. Ideally,
                //        the `devices` array should be the only reference to each Device so that they get
                //        GC'd after removing them from here, but there seem to be references leaking from
                //        PairingFragment and PairingHandler that keep it alive. At least now, by keeping
                //        them in `devices`, we reuse the same Device instance across discoveries of the
                //        same device instead of creating a new object each time.
                //        Also, if we ever fix this, there are two cases were we should be removing devices:
                //            - When a device becomes unreachable, if it's unpaired (this case here)
                //            - When a device becomes unpaired, if it's unreachable (not implemented ATM)
                // if (!device.isReachable && !device.isPaired) {
                //     Log.i("onConnectionLost","Removing device because it was not paired");
                //     devices.remove(link.deviceId)
                //     device.removePairingCallback(devicePairingCallback)
                // }
            } else {
                Log.d("KDE/onConnectionLost", "Removing connection to unknown device")
            }
            onDeviceListChanged()
        }

        override fun onDeviceInfoUpdated(deviceInfo: DeviceInfo) {
            val device = devices[deviceInfo.id]
            if (device == null) {
                Log.e("KdeConnect", "onDeviceInfoUpdated for an unknown device")
                return
            }
            val hasChanges = device.updateDeviceInfo(deviceInfo)
            if (hasChanges) {
                onDeviceListChanged()
            }
        }
    }

    companion object {
        @JvmStatic
        private lateinit var _instance: KdeConnect

        @JvmStatic
        fun getInstance(): KdeConnect = _instance
    }
}
