package org.kde.connect.ComputerLinks;

import org.kde.connect.PackageReceivers.BasePackageReceiver;
import org.kde.connect.NetworkPackage;

import java.util.ArrayList;


public abstract class BaseComputerLink {

    ArrayList<BasePackageReceiver> receivers = new ArrayList<BasePackageReceiver>();

    public void addPackageReceiver(BasePackageReceiver pr) {
        receivers.add(pr);
    }
    public void removePackageReceiver(BasePackageReceiver pr) {
        receivers.remove(pr);
    }

    //Should be called from a background thread listening to packages
    protected void packageReceived(NetworkPackage np) {
        for(BasePackageReceiver pr : receivers) {
            pr.onPackageReceived(np);
        }
    }

    //TO OVERRIDE


    //Should be async
    public abstract void sendPackage(NetworkPackage np);


}
