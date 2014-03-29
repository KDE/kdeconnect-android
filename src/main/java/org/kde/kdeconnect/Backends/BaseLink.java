package org.kde.kdeconnect.Backends;

import org.kde.kdeconnect.NetworkPackage;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;


public abstract class BaseLink {

    private final BaseLinkProvider linkProvider;
    private final String deviceId;
    private final ArrayList<PackageReceiver> receivers = new ArrayList<PackageReceiver>();
    protected PrivateKey privateKey;

    protected BaseLink(String deviceId, BaseLinkProvider linkProvider) {
        this.linkProvider = linkProvider;
        this.deviceId = deviceId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setPrivateKey(PrivateKey key) {
        privateKey = key;
    }

    public BaseLinkProvider getLinkProvider() {
        return linkProvider;
    }


    public interface PackageReceiver {
        public void onPackageReceived(NetworkPackage np);
    }

    public void addPackageReceiver(PackageReceiver pr) {
        receivers.add(pr);
    }
    public void removePackageReceiver(PackageReceiver pr) {
        receivers.remove(pr);
    }

    //Should be called from a background thread listening to packages
    protected void packageReceived(NetworkPackage np) {
        for(PackageReceiver pr : receivers) {
            pr.onPackageReceived(np);
        }
    }

    //TO OVERRIDE, should be sync
    public abstract boolean sendPackage(NetworkPackage np);
    public abstract boolean sendPackageEncrypted(NetworkPackage np, PublicKey key);

}
