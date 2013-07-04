package org.kde.connect.PackageReceivers;

import org.kde.connect.NetworkPackage;

public interface BasePackageReceiver {

    public void onPackageReceived(NetworkPackage np);

}
