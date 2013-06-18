package org.kde.connect.Locators;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.util.Log;

import org.kde.connect.ComputerLinks.BaseComputerLink;
import org.kde.connect.ComputerLinks.UdpComputerLink;
import org.kde.connect.Types.NetworkPackage;

import java.lang.Override;
import java.util.ArrayList;

public class AvahiLocator implements BaseLocator {

    String serviceType = "_kdeconnect._udp";

    NsdManager mNsdManager;

    ArrayList<UdpComputerLink> visibleComputers = new ArrayList<UdpComputerLink>();


    public AvahiLocator(Context context) {
        mNsdManager = (NsdManager)context.getSystemService(Context.NSD_SERVICE);
    }

    @Override
    public void reachComputers(final ConnectionReceiver cr) {

        Log.e("AvahiLocator", "Discovering computers...");

        final NsdManager.ResolveListener mResolveListener = new NsdManager.ResolveListener() {

            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e("AvahiLocator", "Resolve failed" + errorCode);
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                Log.e("AvahiLocator", "Resolve Succeeded. " + serviceInfo);

                //Computer found

                //Handshake link
                UdpComputerLink link = new UdpComputerLink(serviceInfo.getHost(),serviceInfo.getPort());
                NetworkPackage np = new NetworkPackage(System.currentTimeMillis());
                np.setType(NetworkPackage.Type.IDENTIFY);
                np.setBody(Build.BRAND + " - " + Build.MODEL);
                link.sendPackage(np);

                //TODO: Wait for computer confirmation and ensure this is the device we want (requiring user interaction)
                //link.addPackageReceiver();

                //Real data link
                UdpComputerLink link2 = new UdpComputerLink(serviceInfo.getHost(),10600);
                visibleComputers.add(link2);
                cr.onConnectionAccepted(link2);

            }

        };

        NsdManager.DiscoveryListener mDiscoveryListener = new NsdManager.DiscoveryListener() {

            @Override
            public void onDiscoveryStarted(String regType) {
                Log.e("AvahiLocator", "Service discovery started");
            }

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                Log.e("AvahiLocator", "Service discovery success" + service);

                if (!service.getServiceType().startsWith(serviceType)) {
                    Log.e("AvahiLocator", "Unknown Service Type: " + service.getServiceType());
                } else  {
                    Log.e("AvahiLocator", "Computer found, resolving...");
                    mNsdManager.resolveService(service, mResolveListener);
                }

            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
                Log.e("AvahiLocator", "service lost" + service);
                visibleComputers.remove(service);
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.e("AvahiLocator", "Discovery stopped: " + serviceType);
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e("AvahiLocator", "Discovery failed: Error code:" + errorCode);
                mNsdManager.stopServiceDiscovery(this);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e("AvahiLocator", "Discovery failed: Error code:" + errorCode);
                mNsdManager.stopServiceDiscovery(this);
            }
        };

        mNsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);

    }


}
