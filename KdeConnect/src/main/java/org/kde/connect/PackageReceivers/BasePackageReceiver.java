package org.kde.connect.PackageReceivers;

import org.kde.connect.Types.NetworkPackage;

public interface BasePackageReceiver {

    public void receivePackage(NetworkPackage np);

}
