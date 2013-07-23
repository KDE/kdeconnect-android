package org.kde.connect.PackageReceivers;

import org.kde.connect.Device;
import org.kde.connect.NetworkPackage;

public interface BasePackageReceiver {

    public void onPackageReceived(Device d, NetworkPackage np);

}
