package org.kde.kdeconnect.ComputerLinks;

import android.util.Log;

import org.apache.mina.core.session.IoSession;
import org.kde.kdeconnect.LinkProviders.BaseLinkProvider;
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
