package org.kde.connect;

import org.kde.connect.Types.NetworkPackage;

import java.util.ArrayList;


public abstract class ComputerLink {

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

    protected void packageReceived(NetworkPackage np) {
        for(PackageReceiver pr : receivers) {
            pr.onPackageReceived(np);
        }
    }

    //TO OVERRIDE

    //Should set up a listener that calls packageReceived(NetworkPackage)
    public abstract void startListening();

    //Should be async
    public abstract void sendPackage(NetworkPackage np);


}
