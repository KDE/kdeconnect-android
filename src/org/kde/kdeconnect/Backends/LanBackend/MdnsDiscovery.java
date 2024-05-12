/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */


package org.kde.kdeconnect.Backends.LanBackend;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import org.kde.kdeconnect.Helpers.DeviceHelper;

import java.net.InetAddress;
import java.util.Collections;

public class MdnsDiscovery {

    static final String LOG_TAG = "MdnsDiscovery";

    static final String SERVICE_TYPE = "_kdeconnect._udp";

    private final Context context;

    private final LanLinkProvider lanLinkProvider;

    private final NsdManager mNsdManager;
    private NsdManager.RegistrationListener registrationListener;
    private NsdManager.DiscoveryListener discoveryListener;

    private WifiManager.MulticastLock multicastLock;

    private NsdResolveQueue mNsdResolveQueue;

    public MdnsDiscovery(Context context, LanLinkProvider lanLinkProvider) {
        this.context = context;
        this.lanLinkProvider = lanLinkProvider;
        this.mNsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        this.mNsdResolveQueue = new NsdResolveQueue(this.mNsdManager);
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        multicastLock = wifiManager.createMulticastLock("kdeConnectMdnsMulticastLock");
    }

    void startDiscovering() {
        if (discoveryListener == null) {
            multicastLock.acquire();
            discoveryListener = createDiscoveryListener();
            mNsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
        }
    }

    void stopDiscovering() {
        try {
            if (discoveryListener != null) {
                mNsdManager.stopServiceDiscovery(discoveryListener);
                multicastLock.release();
            }
        } catch(IllegalArgumentException e) {
            // Ignore "listener not registered" exception
        }
        discoveryListener = null;
    }

    void stopAnnouncing() {
        try {
            if (registrationListener != null) {
                mNsdManager.unregisterService(registrationListener);
            }
        } catch(IllegalArgumentException e) {
            // Ignore "listener not registered" exception
        }
        registrationListener = null;
    }

    void startAnnouncing() {
        if (registrationListener == null) {
            NsdServiceInfo serviceInfo;
            try {
                serviceInfo = createNsdServiceInfo();
            } catch (IllegalAccessException e) {
                Log.w(LOG_TAG, "Couldn't start announcing via MDNS: " + e.getMessage());
                return;
            }
            registrationListener = createRegistrationListener();
            mNsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener);
        }
    }

    NsdManager.RegistrationListener createRegistrationListener() {
        return new NsdManager.RegistrationListener() {

            @Override
            public void onServiceRegistered(NsdServiceInfo serviceInfo) {
                // If Android changed the service name to avoid conflicts, here we can read it.
                Log.i(LOG_TAG, "Registered " + serviceInfo.getServiceName());
            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(LOG_TAG, "Registration failed with: " + errorCode);
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
                Log.d(LOG_TAG, "Service unregistered: " + serviceInfo);
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(LOG_TAG, "Unregister of " + serviceInfo + " failed with: " + errorCode);
            }
        };
    }

    public NsdServiceInfo createNsdServiceInfo() throws IllegalAccessException {
        NsdServiceInfo serviceInfo = new NsdServiceInfo();

        String deviceId = DeviceHelper.getDeviceId(context);
        // Without resolving the DNS, the service name is the only info we have so it must be sufficient to identify a device.
        // Also, it must be unique, otherwise it will be automatically renamed. For these reasons we use the deviceId.
        serviceInfo.setServiceName(deviceId);
        serviceInfo.setServiceType(SERVICE_TYPE);
        serviceInfo.setPort(LanLinkProvider.UDP_PORT);

        // The following fields aren't really used for anything, since we can't include enough info
        // for it to be useful (namely: we can't include the device certificate).
        // Each field (key + value) needs to be < 255 bytes. All the fields combined need to be < 1300 bytes.
        // Also, on Android Lollipop those fields aren't resolved.
        String deviceName = DeviceHelper.getDeviceName(context);
        String deviceType = DeviceHelper.getDeviceType().toString();
        String protocolVersion = Integer.toString(DeviceHelper.ProtocolVersion);
        serviceInfo.setAttribute("id", deviceId);
        serviceInfo.setAttribute("name", deviceName);
        serviceInfo.setAttribute("type", deviceType);
        serviceInfo.setAttribute("protocol", protocolVersion);

        Log.i(LOG_TAG, "My MDNS info: " + serviceInfo);

        return serviceInfo;
    }

    NsdManager.DiscoveryListener createDiscoveryListener() {
        return new NsdManager.DiscoveryListener() {

            final String myId = DeviceHelper.getDeviceId(context);

            @Override
            public void onDiscoveryStarted(String serviceType) {
                Log.i(LOG_TAG, "Service discovery started: " + serviceType);
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                Log.d(LOG_TAG, "Service discovered: " + serviceInfo);

                String deviceId = serviceInfo.getServiceName();

                if (myId.equals(deviceId)) {
                    Log.d(LOG_TAG, "Discovered myself, ignoring.");
                    return;
                }

                if (lanLinkProvider.visibleDevices.containsKey(deviceId)) {
                    Log.i(LOG_TAG, "MDNS discovered " + deviceId + " to which I'm already connected to. Ignoring.");
                    return;
                }

                // We use a queue because only one service can be resolved at
                // a time, otherwise we get error 3 (already active) in onResolveFailed.
                mNsdResolveQueue.resolveOrEnqueue(serviceInfo, createResolveListener());
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                Log.w(LOG_TAG, "Service lost: " + serviceInfo);
                // We can't see this device via mdns. This probably means it's not reachable anymore
                // but we do nothing here since we have other ways to do detect unreachable devices
                // that hopefully will also trigger.
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.i(LOG_TAG, "MDNS discovery stopped: " + serviceType);
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(LOG_TAG, "MDNS discovery start failed: " + errorCode);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(LOG_TAG, "MDNS discovery stop failed: " + errorCode);
            }
        };
    }


    /**
     * Returns a new listener instance since NsdManager wants a different listener each time you call resolveService
     */
    NsdManager.ResolveListener createResolveListener() {
        return new NsdManager.ResolveListener() {
            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.w(LOG_TAG, "MDNS error " + errorCode + " resolving service: " + serviceInfo);
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                Log.i(LOG_TAG, "MDNS successfully resolved " + serviceInfo);

                // Let the LanLinkProvider handle the connection
                InetAddress remoteAddress = serviceInfo.getHost();
                lanLinkProvider.sendUdpIdentityPacket(Collections.singletonList(remoteAddress), null);
            }
        };
    }

}
