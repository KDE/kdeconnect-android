package org.kde.connect.LinkProviders;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import org.kde.connect.ComputerLinks.BaseComputerLink;
import org.kde.connect.ComputerLinks.UdpComputerLink;
import org.kde.connect.Device;
import org.kde.connect.NetworkPackage;

import java.lang.Override;
import java.util.ArrayList;

public class AvahiLinkProvider implements BaseLinkProvider {

    String serviceType = "_kdeconnect._udp";

    NsdManager mNsdManager;

    ArrayList<UdpComputerLink> visibleComputers = new ArrayList<UdpComputerLink>();

    Context ctx;

    private NsdManager.DiscoveryListener oldListener = null;

    public AvahiLinkProvider(Context context) {
        mNsdManager = (NsdManager)context.getSystemService(Context.NSD_SERVICE);
        ctx = context;
    }

    @Override
    public void reachComputers(final ConnectionReceiver cr) {

        visibleComputers.clear();

        if (oldListener != null) mNsdManager.stopServiceDiscovery(oldListener);
        
        Log.e("AvahiLinkProvider", "Discovering computers...");

        final NsdManager.ResolveListener mResolveListener = new NsdManager.ResolveListener() {

            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e("AvahiLinkProvider", "Resolve failed: " + errorCode);
            }

            @Override
            public void onServiceResolved(final NsdServiceInfo serviceInfo) {
                Log.e("AvahiLinkProvider", "Resolve Succeeded: " + serviceInfo);

                //Handshake link
                try {
                    UdpComputerLink link = new UdpComputerLink(AvahiLinkProvider.this,serviceInfo.getHost(),serviceInfo.getPort());
                    link.addPackageReceiver(new BaseComputerLink.PackageReceiver() {
                        @Override
                        public void onPackageReceived(NetworkPackage np) {
                            Log.e("AvahiLinkProvider","Received reply");
                            if (np.getType().equals(NetworkPackage.PACKAGE_TYPE_IDENTITY)) {
                                String id = np.getString("deviceId");
                                String name = np.getString("deviceName");
                                //Real data link
                                try {
                                    UdpComputerLink link2 = new UdpComputerLink(AvahiLinkProvider.this,serviceInfo.getHost(),10603);
                                    visibleComputers.add(link2);
                                    cr.onConnectionAccepted(id, name, link2);
                                } catch (Exception e) {

                                }
                            } else {
                                Log.e("AvahiLinkProvider","Received non-identity package");
                            }
                        }
                    });
                    link.startReceivingPackages();
                    Log.e("AvahiLinkProvider","Sending identity package");
                    NetworkPackage np = NetworkPackage.createIdentityPackage(ctx);
                    link.sendPackage(np);
                } catch (Exception e) {

                }
            }

        };

        NsdManager.DiscoveryListener mDiscoveryListener = new NsdManager.DiscoveryListener() {

            @Override
            public void onDiscoveryStarted(String regType) {
                Log.e("AvahiLinkProvider", "Service discovery started");
            }

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                Log.e("AvahiLinkProvider", "Service discovery success" + service);

                if (!service.getServiceType().startsWith(serviceType)) {
                    Log.e("AvahiLinkProvider", "Unknown Service Type: " + service.getServiceType());
                } else  {
                    Log.e("AvahiLinkProvider", "Computer found, resolving...");
                    mNsdManager.resolveService(service, mResolveListener);
                }

            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
                Log.e("AvahiLinkProvider", "Service lost: " + service);
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.e("AvahiLinkProvider", "Discovery stopped: " + serviceType);
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e("AvahiLinkProvider", "Discovery failed: Error code:" + errorCode);
                mNsdManager.stopServiceDiscovery(this);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e("AvahiLinkProvider", "Discovery failed: Error code:" + errorCode);
                mNsdManager.stopServiceDiscovery(this);
            }
        };

        oldListener = mDiscoveryListener;
        mNsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);

    }


    @Override
    public int getPriority() {
        return 100;
    }


    @Override
    public String getName() {
        return "AvahiUdpLinkProvider";
    }

}
