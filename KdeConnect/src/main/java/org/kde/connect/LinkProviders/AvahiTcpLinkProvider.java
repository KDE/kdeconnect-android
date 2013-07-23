package org.kde.connect.LinkProviders;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import org.kde.connect.ComputerLinks.BaseComputerLink;
import org.kde.connect.ComputerLinks.TcpComputerLink;
import org.kde.connect.Device;
import org.kde.connect.NetworkPackage;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class AvahiTcpLinkProvider implements BaseLinkProvider {

    String serviceType = "_kdeconnect._tcp";

    NsdManager mNsdManager;

    HashMap<InetAddress, TcpComputerLink> visibleComputers = new HashMap<InetAddress, TcpComputerLink>();

    Context ctx;
    private NsdManager.DiscoveryListener oldListener = null;

    public AvahiTcpLinkProvider(Context context) {
        mNsdManager = (NsdManager)context.getSystemService(Context.NSD_SERVICE);
        ctx = context;
    }

    @Override
    public void reachComputers(final ConnectionReceiver cr) {

        visibleComputers.clear();

        if (oldListener != null) mNsdManager.stopServiceDiscovery(oldListener);

        Log.e("AvahiTcpLinkProvider", "Discovering computers...");

        final NsdManager.ResolveListener mResolveListener = new NsdManager.ResolveListener() {

            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e("AvahiTcpLinkProvider", "Resolve failed" + errorCode);
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                Log.e("AvahiTcpLinkProvider", "Resolve Succeeded. " + serviceInfo);

                try {
                    Log.e("AvahiTcpLinkProvider", "Creating link");
                    final InetAddress host = serviceInfo.getHost();
                    final int port = serviceInfo.getPort();
                    final TcpComputerLink link = new TcpComputerLink(AvahiTcpLinkProvider.this,host,port);
                    Log.e("AvahiTcpLinkProvider", "Waiting identity package");
                    link.addPackageReceiver(new BaseComputerLink.PackageReceiver() {
                        @Override
                        public void onPackageReceived(NetworkPackage np) {

                            Log.e("AvahiTcpLinkProvider", "Received package: " + np.getType());

                            if (np.getType().equals(NetworkPackage.PACKAGE_TYPE_IDENTITY)) {
                                String id = np.getString("deviceId");
                                String name = np.getString("deviceName");

                                link.setDeviceId(id);
                                link.sendPackage(NetworkPackage.createIdentityPackage(ctx));
                                if (visibleComputers.containsKey(host)) {
                                    //Remove old connection to same host, probably down
                                    cr.onConnectionLost(visibleComputers.get(host));
                                }
                                visibleComputers.put(host,link);
                                cr.onConnectionAccepted(id,name,link);

                            }

                        }
                    });
                    link.startReceivingPackages();
                } catch (Exception e) {
                    Log.e("AvahiTcpLinkProvider","Exception");
                    e.printStackTrace();
                }

            }

        };

        NsdManager.DiscoveryListener mDiscoveryListener = new NsdManager.DiscoveryListener() {

            @Override
            public void onDiscoveryStarted(String regType) {
                Log.e("AvahiTcpLinkProvider", "Service discovery started");
            }

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                Log.e("AvahiTcpLinkProvider", "Service discovery success" + service);

                if (!service.getServiceType().startsWith(serviceType)) {
                    Log.e("AvahiTcpLinkProvider", "Unknown Service Type: " + service.getServiceType());
                } else  {
                    Log.e("AvahiTcpLinkProvider", "Computer found, resolving...");
                    mNsdManager.resolveService(service, mResolveListener);
                }

            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
                Log.e("AvahiTcpLinkProvider", "service lost" + service);
                TcpComputerLink link = visibleComputers.remove(service.getHost());
                if (link != null) cr.onConnectionLost(link);
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.e("AvahiTcpLinkProvider", "Discovery stopped: " + serviceType);
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e("AvahiTcpLinkProvider", "Discovery failed: Error code:" + errorCode);
                mNsdManager.stopServiceDiscovery(this);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e("AvahiTcpLinkProvider", "Discovery failed: Error code:" + errorCode);
                mNsdManager.stopServiceDiscovery(this);
            }
        };

        oldListener = mDiscoveryListener;
        mNsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);

    }

    @Override
    public int getPriority() {
        return 101;
    }

    @Override
    public String getName() {
        return "AvahiTcpLinkProvider";
    }
}
