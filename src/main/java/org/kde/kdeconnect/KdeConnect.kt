/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect

import android.app.Application
import android.os.Build
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.StrictMode.VmPolicy
import android.util.Log
import androidx.annotation.WorkerThread
import org.kde.kdeconnect.backends.BaseLink
import org.kde.kdeconnect.backends.BaseLinkProvider.ConnectionReceiver
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.helpers.LifecycleHelper
import org.kde.kdeconnect.helpers.NotificationHelper
import org.kde.kdeconnect.helpers.security.RsaHelper
import org.kde.kdeconnect.helpers.security.SslHelper
import org.kde.kdeconnect.helpers.ThreadHelper
import org.kde.kdeconnect.helpers.TrustedDevices
import org.kde.kdeconnect.PairingHandler.PairingCallback
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginFactory
import org.kde.kdeconnect.ui.ThemeUtil
import org.kde.kdeconnect_tp.BuildConfig
import org.slf4j.impl.HandroidLoggerAdapter
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Date
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

        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            StrictMode.setVmPolicy(
                VmPolicy.Builder(StrictMode.getVmPolicy())
                    .detectActivityLeaks()
                    .detectLeakedClosableObjects()
                    .detectLeakedRegistrationObjects()
                    .detectFileUriExposure()
                    .detectContentUriWithoutPermission()
                    .detectCredentialProtectedWhileLocked()
                    .detectIncorrectContextUse()
                    .detectUnsafeIntentLaunch()
                    //.detectBlockedBackgroundActivityLaunch()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setThreadPolicy(
                ThreadPolicy.Builder(StrictMode.getThreadPolicy())
                    .detectUnbufferedIo()
                    .detectResourceMismatches()
                    .penaltyLog()
                    .build()
            )
        }
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
        // Log.e("BackgroundService", "Loading remembered trusted devices")
        val trustedDevices = TrustedDevices.getAllTrustedDevices(this)
        trustedDevices.asSequence()
            .onEach { Log.d("KdeConnect", "Loading device $it") }
            .forEach {
                try {
                    val device = Device(applicationContext, it)
                    val now = Date()
                    val x509Cert = device.certificate as X509Certificate
                    if(now < x509Cert.notBefore) {
                        throw CertificateException("Certificate not effective yet: "+x509Cert.notBefore)
                    }
                    else if(now > x509Cert.notAfter) {
                        throw CertificateException("Certificate already expired: "+x509Cert.notAfter)
                    }
                    devices[it] = device
                    device.addPairingCallback(devicePairingCallback)
                } catch (e: CertificateException) {
                    Log.w(
                        "KdeConnect",
                        "Couldn't load the certificate for a remembered device. Removing from trusted list.", e
                    )
                    TrustedDevices.removeTrustedDevice(this, it)
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

        override fun unpaired(device: Device) {
            onDeviceListChanged()
            if (!device.isReachable) {
                scheduleForDeletion(device)
            }
        }
    }

    val connectionListener: ConnectionReceiver = object : ConnectionReceiver {
        @WorkerThread
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

        @WorkerThread
        override fun onConnectionLost(link: BaseLink) {
            val device = devices[link.deviceId]
            Log.i("KDE/onConnectionLost", "removeLink, deviceId: ${link.deviceId}")
            if (device != null) {
                device.removeLink(link)
                if (!device.isReachable && !device.isPaired) {
                    scheduleForDeletion(device)
                }
            } else {
                Log.d("KDE/onConnectionLost", "Removing connection to unknown device")
            }
            onDeviceListChanged()
        }

        @WorkerThread
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

    fun scheduleForDeletion(device: Device) {
        // Note: By this point the `devices` map should be the only reference to `device`, so it
        //       should get garbage collected after removing it here. However, it's easy to leak
        //       references to Device from views, preventing them from being freed. You can use the
        //       debugger to check for alive instances of `Device` after a device is supposed to be
        //       destroyed to make sure we don't have leaks. Note that you might need to trigger
        //       `Runtime.getRuntime().gc()` to actually make them disappear. If we have leaks,
        //       deleting devices from the map is actually counterproductive because each time we
        //       detect the same device, a new Device object will be created (and leaked again).
        Log.i("KdeConnect", "Scheduled for deletion: $device, paired: ${device.isPaired}, reachable: ${device.isReachable}")
        ThreadHelper.execute {
            try {Thread.sleep(1000)} catch (_: InterruptedException) { }
            if (device.isReachable) {
                Log.i("KdeConnect", "Not deleting device since it's reachable again: $device")
                return@execute
            }
            if (device.isPaired) {
                Log.i("KdeConnect", "Not deleting device since it's still paired: $device")
                return@execute
            }
            Log.i("KdeConnect", "Deleting unpaired and unreachable device: $device")
            device.removePairingCallback(devicePairingCallback)
            devices.remove(device.deviceId)
        }
    }

    companion object {
        @JvmStatic
        private lateinit var _instance: KdeConnect

        @JvmStatic
        fun getInstance(): KdeConnect = _instance
    }
}
