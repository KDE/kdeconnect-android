package org.kde.kdeconnect.Backends.LoopbackBackend;

import org.kde.kdeconnect.Backends.BaseComputerLink;
import org.kde.kdeconnect.Backends.BaseLinkProvider;
import org.kde.kdeconnect.NetworkPackage;

public class LoopbackComputerLink extends BaseComputerLink {

    public LoopbackComputerLink(BaseLinkProvider linkProvider) {
        super("loopback", linkProvider);
    }

    @Override
    public boolean sendPackage(NetworkPackage in) {
        String s = in.serialize();
        NetworkPackage out= NetworkPackage.unserialize(s);
        packageReceived(out);
        return true;
    }

}
