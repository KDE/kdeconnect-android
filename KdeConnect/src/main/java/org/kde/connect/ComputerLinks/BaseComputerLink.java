package org.kde.connect.ComputerLinks;

import org.kde.connect.Types.NetworkPackage;

import java.util.ArrayList;


public abstract class BaseComputerLink {

    public interface PackageReceiver {
        public void onPackageReceived(NetworkPackage np);
    }

    ArrayList<PackageReceiver> receivers = new ArrayList<PackageReceiver>();

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

    //TO OVERRIDE


    //Should be async
    public abstract void sendPackage(NetworkPackage np);


}
