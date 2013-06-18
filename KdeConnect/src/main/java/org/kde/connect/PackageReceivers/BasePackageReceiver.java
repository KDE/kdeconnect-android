package org.kde.connect.PackageReceivers;

import org.kde.connect.ComputerLink;
import org.kde.connect.Types.NetworkPackage;

import java.util.ArrayList;

public interface BasePackageReceiver {

    public void receivePackage(NetworkPackage np);

}
