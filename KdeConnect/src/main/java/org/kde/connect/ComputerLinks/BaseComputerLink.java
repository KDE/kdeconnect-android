package org.kde.connect.ComputerLinks;

import org.kde.connect.Device;
import org.kde.connect.LinkProviders.BaseLinkProvider;
import org.kde.connect.PackageReceivers.BasePackageReceiver;
import org.kde.connect.NetworkPackage;

import java.util.ArrayList;


public abstract class BaseComputerLink {

    private BaseLinkProvider linkProvider;

    private String deviceId;

    public BaseComputerLink(BaseLinkProvider linkProvider) {
        this.linkProvider = linkProvider;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public BaseLinkProvider getLinkProvider() {
        return linkProvider;
    }


    ArrayList<BasePackageReceiver> receivers = new ArrayList<BasePackageReceiver>();

    public void addPackageReceiver(BasePackageReceiver pr) {
        receivers.add(pr);
    }
    public void removePackageReceiver(BasePackageReceiver pr) {
        receivers.remove(pr);
    }

    //Should be called from a background thread listening to packages
    protected void packageReceived(Device d, NetworkPackage np) {
        for(BasePackageReceiver pr : receivers) {
            pr.onPackageReceived(d, np);
        }
    }

    //TO OVERRIDE
    public abstract boolean sendPackage(NetworkPackage np);    //Should be async

}
