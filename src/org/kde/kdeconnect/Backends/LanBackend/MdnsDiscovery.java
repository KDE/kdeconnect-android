/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */


package org.kde.kdeconnect.Backends.LanBackend;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
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

    public MdnsDiscovery(Context context, LanLinkProvider lanLinkProvider) {
        this.context = context;
        this.lanLinkProvider = lanLinkProvider;
        mNsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
    }

    void startListening() {
        if (discoveryListener == null) {
            discoveryListener = createDiscoveryListener();
            mNsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
        }
    }

    void stopListening() {
        if (discoveryListener != null) {
            mNsdManager.stopServiceDiscovery(discoveryListener);
            discoveryListener = null;
        }
    }

    void stopAnnouncing() {
        if (registrationListener != null) {
            mNsdManager.unregisterService(registrationListener);
            registrationListener = null;
        }
    }

    void startAnnouncing() {
        if (registrationListener == null) {
            registrationListener = createRegistrationListener();
            NsdServiceInfo serviceInfo = createNsdServiceInfo();
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

    public NsdServiceInfo createNsdServiceInfo() {
        NsdServiceInfo serviceInfo = new NsdServiceInfo();

        InetAddress address = lanLinkProvider.udpServer.getInetAddress();
        int port = lanLinkProvider.udpServer.getLocalPort();
        serviceInfo.setHost(address);
        serviceInfo.setPort(port);

        // iOS seems to need these as a TXT records
        serviceInfo.setAttribute("ip", address.toString());
        serviceInfo.setAttribute("port", Integer.toString(port));

        // The following fields aren't really used for anything, since we can't include enough info
        // for it to be useful (namely: we can't include the device certificate).
        // Each field (key + value) needs to be < 255 bytes. All the fields combined need to be < 1300 bytes.
        // Also, on Android Lollipop those fields aren't resolved.
        String deviceId = DeviceHelper.getDeviceId(context);
        String deviceName = DeviceHelper.getDeviceName(context);
        String deviceType = DeviceHelper.getDeviceType(context).toString();
        String protocolVersion = Integer.toString(DeviceHelper.ProtocolVersion);
        serviceInfo.setAttribute("id", deviceId);
        serviceInfo.setAttribute("name", deviceName);
        serviceInfo.setAttribute("type", deviceType);
        serviceInfo.setAttribute("version", protocolVersion);

        // Without resolving the DNS, the service name is the only info we have so it must be sufficient to identify a device.
        // Also, it must be unique, otherwise it will be automatically renamed. For these reasons we use the deviceId.
        serviceInfo.setServiceName(deviceId);
        serviceInfo.setServiceType(SERVICE_TYPE);

        Log.d(LOG_TAG, "My MDNS info: " + serviceInfo.toString());

        return serviceInfo;
    }

    NsdManager.DiscoveryListener createDiscoveryListener() {
        return new NsdManager.DiscoveryListener() {

            final String myId = DeviceHelper.getDeviceId(context);

            @Override
            public void onDiscoveryStarted(String regType) {
                Log.i(LOG_TAG, "Service discovery started");
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
                mNsdManager.resolveService(serviceInfo, createResolveListener());
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

                InetAddress remoteAddress = serviceInfo.getHost();

                // Let the LanLinkProvider handle the connection
                lanLinkProvider.sendUdpIdentityPacket(Collections.singletonList(remoteAddress));
            }
        };
    }

}
