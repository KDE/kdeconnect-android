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

public class AvahiTcpLinkProvider extends BaseLinkProvider {

    String serviceType = "_kdeconnect._tcp";

    Context context;
    NsdManager mNsdManager;
    private NsdManager.DiscoveryListener oldListener = null;

    HashMap<String, TcpComputerLink> visibleComputers = new HashMap<String, TcpComputerLink>();

    public AvahiTcpLinkProvider(Context context) {
        this.context = context;
        mNsdManager = (NsdManager)context.getSystemService(Context.NSD_SERVICE);
    }

    @Override
    public void onStart() {

        if (oldListener != null) return;

        Log.e("AvahiTcpLinkProvider", "Discovering computers...");

        final NsdManager.ResolveListener mResolveListener = new NsdManager.ResolveListener() {

            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e("AvahiTcpLinkProvider", "Resolve failed" + errorCode);
            }

            @Override
            public void onServiceResolved(final NsdServiceInfo serviceInfo) {
                Log.e("AvahiTcpLinkProvider", "Resolve Succeeded. " + serviceInfo);

                try {
                    Log.e("AvahiTcpLinkProvider", "Connecting and waiting identity package");
                    final InetAddress host = serviceInfo.getHost();
                    final int port = serviceInfo.getPort();
                    final TcpComputerLink link = new TcpComputerLink(AvahiTcpLinkProvider.this);
                    link.addPackageReceiver(new BaseComputerLink.PackageReceiver() {
                        @Override
                        public void onPackageReceived(NetworkPackage np) {

                            Log.e("AvahiTcpLinkProvider", "Received package: " + np.getType());

                            if (np.getType().equals(NetworkPackage.PACKAGE_TYPE_IDENTITY)) {
                                String id = np.getString("deviceId");
                                String name = np.getString("deviceName");

                                link.setDeviceId(id);
                                link.sendPackage(NetworkPackage.createIdentityPackage(context));
                                if (visibleComputers.containsKey(serviceInfo.toString())) {
                                    //Remove old connection to same host, probably down
                                    connectionLost(visibleComputers.get(serviceInfo.getServiceName()));
                                }
                                visibleComputers.put(serviceInfo.getServiceName(),link);
                                connectionAccepted(id,name,link);
                                link.removePackageReceiver(this);

                            }

                        }
                    });
                    link.connect(host,port);
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
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                Log.e("AvahiTcpLinkProvider", "Service lost: " + serviceInfo.getServiceName());
                TcpComputerLink link = visibleComputers.remove(serviceInfo.getServiceName());
                if (link != null) connectionLost(link);
                else Log.e("AvahiTcpLinkProvider","Host unknown! (?)");
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
    public void onStop() {

        if (oldListener != null) mNsdManager.stopServiceDiscovery(oldListener);
        oldListener = null;
        visibleComputers.clear();

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
