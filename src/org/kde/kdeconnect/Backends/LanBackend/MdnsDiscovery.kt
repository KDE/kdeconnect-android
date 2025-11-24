/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.Backends.LanBackend

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdManager.DiscoveryListener
import android.net.nsd.NsdManager.RegistrationListener
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.MulticastLock
import android.util.Log
import androidx.preference.PreferenceManager
import org.kde.kdeconnect.Helpers.DeviceHelper
import org.kde.kdeconnect.Helpers.DeviceHelper.deviceType
import org.kde.kdeconnect.Helpers.DeviceHelper.getDeviceId
import org.kde.kdeconnect.Helpers.DeviceHelper.getDeviceName
import org.kde.kdeconnect.UserInterface.SettingsFragment
import java.net.InetAddress

class MdnsDiscovery {
    private val context: Context
    private val lanLinkProvider: LanLinkProvider
    private val mNsdManager: NsdManager
    private var registrationListener: RegistrationListener? = null
    private var discoveryListener: DiscoveryListener? = null
    private val multicastLock: MulticastLock
    private val mNsdResolveQueue: NsdResolveQueue

    constructor(context: Context, lanLinkProvider: LanLinkProvider) {
        this.context = context
        this.lanLinkProvider = lanLinkProvider
        this.mNsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        this.mNsdResolveQueue = NsdResolveQueue(this.mNsdManager)
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifiManager.createMulticastLock("kdeConnectMdnsMulticastLock")
    }

    fun startDiscovering() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        if (!preferences.getBoolean(SettingsFragment.KEY_MDNS_DISCOVERY_ENABLED, false)) {
            Log.i("MdnsDiscovery", "MDNS discovery is disabled in settings. Skipping.")
            return
        }
        if (discoveryListener == null) {
            multicastLock.acquire()
            discoveryListener = createDiscoveryListener()
            mNsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }
    }

    fun stopDiscovering() {
        try {
            if (discoveryListener != null) {
                mNsdManager.stopServiceDiscovery(discoveryListener)
                multicastLock.release()
            }
        } catch (_: IllegalArgumentException) {
            // Ignore "listener not registered" exception
        }
        discoveryListener = null
    }

    fun startAnnouncing() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        if (!preferences.getBoolean(SettingsFragment.KEY_MDNS_DISCOVERY_ENABLED, false)) {
            Log.i("MdnsDiscovery", "MDNS discovery is disabled in settings. Skipping.")
            return
        }
        if (registrationListener == null) {
            val serviceInfo: NsdServiceInfo?
            try {
                serviceInfo = createNsdServiceInfo()
            } catch (e: IllegalAccessException) {
                Log.w(LOG_TAG, "Couldn't start announcing via MDNS: " + e.message)
                return
            }
            registrationListener = createRegistrationListener()
            mNsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        }
    }

    fun stopAnnouncing() {
        try {
            if (registrationListener != null) {
                mNsdManager.unregisterService(registrationListener)
            }
        } catch (_: IllegalArgumentException) {
            // Ignore "listener not registered" exception
        }
        registrationListener = null
    }

    fun createRegistrationListener() = object : RegistrationListener {
        override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
            // If Android changed the service name to avoid conflicts, here we can read it.
            Log.i(LOG_TAG, "Registered ${serviceInfo.serviceName}")
        }

        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
            Log.e(LOG_TAG, "Registration failed with: $errorCode")
        }

        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {
            Log.d(LOG_TAG, "Service unregistered: $serviceInfo")
        }

        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
            Log.e(LOG_TAG, "Unregister of $serviceInfo failed with: $errorCode")
        }
    }

    @Throws(IllegalAccessException::class)
    fun createNsdServiceInfo(): NsdServiceInfo {
        val serviceInfo = NsdServiceInfo()

        val deviceId = getDeviceId(context)
        // Without resolving the DNS, the service name is the only info we have so it must be sufficient to identify a device.
        // Also, it must be unique, otherwise it will be automatically renamed. For these reasons we use the deviceId.
        serviceInfo.serviceName = deviceId
        serviceInfo.serviceType = SERVICE_TYPE
        serviceInfo.port = LanLinkProvider.UDP_PORT

        // The following fields aren't really used for anything, since we can't include enough info
        // for it to be useful (namely: we can't include the device certificate).
        // Each field (key + value) needs to be < 255 bytes. All the fields combined need to be < 1300 bytes.
        val deviceName = getDeviceName(context)
        val deviceType = deviceType.toString()
        val protocolVersion = DeviceHelper.PROTOCOL_VERSION.toString()
        serviceInfo.setAttribute("id", deviceId)
        serviceInfo.setAttribute("name", deviceName)
        serviceInfo.setAttribute("type", deviceType)
        serviceInfo.setAttribute("protocol", protocolVersion)

        Log.i(LOG_TAG, "My MDNS info: $serviceInfo")

        return serviceInfo
    }

    fun createDiscoveryListener() = object : DiscoveryListener {
        val myId: String = getDeviceId(context)

        override fun onDiscoveryStarted(serviceType: String?) {
            Log.i(LOG_TAG, "Service discovery started: $serviceType")
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            Log.d(LOG_TAG, "Service discovered: $serviceInfo")

            val deviceId = serviceInfo.serviceName

            if (myId == deviceId) {
                Log.d(LOG_TAG, "Discovered myself, ignoring.")
                return
            }

            if (lanLinkProvider.visibleDevices.containsKey(deviceId)) {
                Log.i(LOG_TAG, "MDNS discovered $deviceId to which I'm already connected to. Ignoring.")
                return
            }

            // We use a queue because only one service can be resolved at
            // a time, otherwise we get error 3 (already active) in onResolveFailed.
            mNsdResolveQueue.resolveOrEnqueue(serviceInfo, createResolveListener())
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
            Log.w(LOG_TAG, "Service lost: $serviceInfo")
            // We can't see this device via mdns. This probably means it's not reachable anymore
            // but we do nothing here since we have other ways to do detect unreachable devices
            // that hopefully will also trigger.
        }

        override fun onDiscoveryStopped(serviceType: String?) {
            Log.i(LOG_TAG, "MDNS discovery stopped: $serviceType")
        }

        override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
            Log.e(LOG_TAG, "MDNS discovery start failed: $errorCode")
        }

        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
            Log.e(LOG_TAG, "MDNS discovery stop failed: $errorCode")
        }
    }

    /**
     * Returns a new listener instance since NsdManager wants a different listener each time you call resolveService
     */
    fun createResolveListener() = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
            Log.w(LOG_TAG, "MDNS error $errorCode resolving service: $serviceInfo")
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            Log.i(LOG_TAG, "MDNS successfully resolved $serviceInfo")

            // Let the LanLinkProvider handle the connection
            val remoteAddress = serviceInfo.host
            // TODO: In protocol version 8 we should be able to call "identityPacketReceived"
            //       here, since we already have all the info we need to start a connection
            //       and the remaining identity info will be exchanged later.
            lanLinkProvider.sendUdpIdentityPacket(mutableListOf<InetAddress?>(remoteAddress), null)
        }
    }

    companion object {
        const val LOG_TAG: String = "MdnsDiscovery"

        const val SERVICE_TYPE: String = "_kdeconnect._udp"
    }
}
