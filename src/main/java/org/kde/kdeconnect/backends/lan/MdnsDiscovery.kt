/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.backends.lan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.nsd.NsdManager
import android.net.nsd.NsdManager.DiscoveryListener
import android.net.nsd.NsdManager.RegistrationListener
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.MulticastLock
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.helpers.DeviceHelper.getDeviceId
import org.kde.kdeconnect.helpers.DeviceHelper.getDeviceName
import org.kde.kdeconnect.helpers.ThreadHelper
import org.kde.kdeconnect.helpers.TrustedNetworkHelper
import org.kde.kdeconnect.helpers.isPrivateAddress
import org.kde.kdeconnect.helpers.normalizedMappedIpv4Address
import java.net.Inet4Address
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

    @Synchronized
    fun startDiscovering() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK) != PERMISSION_GRANTED) {
            Log.w("MdnsDiscover", "Will not MDNS discover, missing ACCESS_LOCAL_NETWORK permission")
            return
        }
        if (!TrustedNetworkHelper.isTrustedNetwork(context)) {
            Log.i(TAG, "Will not MDNS discover on an untrusted network")
            return
        }
        if (discoveryListener == null) {
            multicastLock.acquire()
            discoveryListener = createDiscoveryListener()
            mNsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }
    }

    @Synchronized
    fun stopDiscovering() {
        if (discoveryListener != null) {
            try {
                mNsdManager.stopServiceDiscovery(discoveryListener)
            } catch (_: IllegalArgumentException) {
                // Ignore "listener not registered" exception
            }
            discoveryListener = null
            multicastLock.release()
        }
    }

    @Synchronized
    fun startAnnouncing() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK) != PERMISSION_GRANTED) {
            Log.w("MdnsDiscover", "Will not MDNS announce, missing ACCESS_LOCAL_NETWORK permission")
            return
        }
        if (registrationListener == null) {
            val serviceInfo: NsdServiceInfo?
            try {
                serviceInfo = createNsdServiceInfo()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Couldn't start announcing via MDNS: ${e.message}")
                return
            } catch (e: IllegalAccessException) {
                Log.w(TAG, "Couldn't start announcing via MDNS: ${e.message}")
                return
            }
            registrationListener = createRegistrationListener()
            mNsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        }
    }

    @Synchronized
    fun stopAnnouncing() {
        if (registrationListener != null) {
            try {
                mNsdManager.unregisterService(registrationListener)
            } catch (_: IllegalArgumentException) {
                // Ignore "listener not registered" exception
            }
            registrationListener = null
        }
    }

    fun createRegistrationListener() = object : RegistrationListener {
        override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
            // If Android changed the service name to avoid conflicts, here we can read it.
            Log.i(TAG, "Registered ${serviceInfo.serviceName}")
        }

        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
            Log.e(TAG, "Registration failed with: $errorCode")
            synchronized(this@MdnsDiscovery) {
                if (registrationListener === this) {
                    registrationListener = null
                }
            }
        }

        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {
            Log.d(TAG, "Service unregistered: $serviceInfo")
        }

        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
            Log.e(TAG, "Unregister of $serviceInfo failed with: $errorCode")
        }
    }

    @Throws(IllegalAccessException::class, IllegalStateException::class)
    fun createNsdServiceInfo(): NsdServiceInfo {
        val serviceInfo = NsdServiceInfo()

        val deviceId = getDeviceId(context)
        // Without resolving the DNS, the service name is the only info we have so it must be sufficient to identify a device.
        // Also, it must be unique, otherwise it will be automatically renamed. For these reasons we use the deviceId.
        serviceInfo.serviceName = deviceId
        serviceInfo.serviceType = SERVICE_TYPE
        serviceInfo.port = lanLinkProvider.tcpPort ?: throw IllegalStateException("tcpPort is null")

        // The following fields aren't really used for anything, since we can't include enough info
        // for it to be useful (namely: we can't include the device certificate).
        // Each field (key + value) needs to be < 255 bytes. All the fields combined need to be < 1300 bytes.
        val deviceName = getDeviceName(context)
        val protocolVersion = DeviceHelper.PROTOCOL_VERSION.toString()
        serviceInfo.setAttribute("id", deviceId)
        serviceInfo.setAttribute("name", deviceName)
        serviceInfo.setAttribute("protocol", protocolVersion)

        Log.i(TAG, "My MDNS info: $serviceInfo")

        return serviceInfo
    }

    fun createDiscoveryListener() = object : DiscoveryListener {
        val myId: String = getDeviceId(context)

        override fun onDiscoveryStarted(serviceType: String?) {
            Log.i(TAG, "Service discovery started: $serviceType")
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            Log.d(TAG, "Service discovered: $serviceInfo")

            val deviceId = serviceInfo.serviceName

            if (myId == deviceId) {
                Log.d(TAG, "Discovered myself, ignoring.")
                return
            }

            if (lanLinkProvider.hasDevice(deviceId)) {
                Log.i(TAG, "MDNS discovered $deviceId to which I'm already connected to. Ignoring.")
                return
            }

            // We use a queue because only one service can be resolved at
            // a time, otherwise we get error 3 (already active) in onResolveFailed.
            mNsdResolveQueue.resolveOrEnqueue(serviceInfo, createResolveListener())
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
            Log.w(TAG, "Service lost: $serviceInfo")
            // We can't see this device via mdns. This probably means it's not reachable anymore
            // but we do nothing here since we have other ways to do detect unreachable devices
            // that hopefully will also trigger.
        }

        override fun onDiscoveryStopped(serviceType: String?) {
            Log.i(TAG, "MDNS discovery stopped: $serviceType")
        }

        override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
            Log.e(TAG, "MDNS discovery start failed: $errorCode")
            synchronized(this@MdnsDiscovery) {
                if (discoveryListener === this) {
                    discoveryListener = null
                    multicastLock.release()
                }
            }
        }

        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
            Log.e(TAG, "MDNS discovery stop failed: $errorCode")
        }
    }

    /**
     * Returns a new listener instance since NsdManager wants a different listener each time you call resolveService
     */
    fun createResolveListener() = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
            Log.w(TAG, "MDNS error $errorCode resolving service: $serviceInfo")
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            Log.i(TAG, "MDNS successfully resolved $serviceInfo")

            // Discovery can have been stopped while this resolve was in flight.
            // Do not leak our identity to a service discovered on an untrusted network.
            if (!TrustedNetworkHelper.isTrustedNetwork(context)) {
                Log.i(TAG, "Ignoring MDNS result on an untrusted network")
                return
            }

            val deviceId = serviceInfo.serviceName
            val protocolVersion = serviceInfo.attributes["protocol"]?.toString(Charsets.UTF_8)?.toIntOrNull() ?: -1
            val remoteAddress = pickAddress(serviceInfo)
            val port = serviceInfo.port
            // Bind to the network the service was found on: right after connecting to a wifi the
            // default network can still be cellular, which would make the connection fail.
            val network = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) serviceInfo.network else null

            // Let the LanLinkProvider handle the connection. Connecting blocks, so do it on another thread.
            ThreadHelper.runOnNewThread {
                lanLinkProvider.deviceDiscovered(remoteAddress, port, deviceId, protocolVersion, network, true)
            }
        }

        /**
         * Prefer a private address: if we reach the remote device through a public IPv6 it will
         * connect back from it too, and we discard connections from non-private addresses.
         */
        private fun pickAddress(serviceInfo: NsdServiceInfo): InetAddress {
            val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                serviceInfo.hostAddresses.map { it.normalizedMappedIpv4Address() }
            } else {
                emptyList()
            }
            return addresses.firstOrNull { it is Inet4Address && isPrivateAddress(it) }
                ?: addresses.firstOrNull { isPrivateAddress(it) }
                ?: serviceInfo.host.normalizedMappedIpv4Address()
        }
    }

    companion object {
        private const val TAG: String = "MdnsDiscovery"

        private const val SERVICE_TYPE: String = "_kdeconnect._udp"
    }
}
