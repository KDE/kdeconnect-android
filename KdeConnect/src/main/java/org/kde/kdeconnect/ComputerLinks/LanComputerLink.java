package org.kde.kdeconnect.ComputerLinks;

import android.util.Log;

import org.apache.mina.core.session.IoSession;
import org.kde.kdeconnect.LinkProviders.BaseLinkProvider;
import org.kde.kdeconnect.NetworkPackage;

public class LanComputerLink extends BaseComputerLink {

    private IoSession session = null;

    public void disconnect() {
        Log.i("LanComputerLink","Disconnect: "+session.getRemoteAddress().toString());
        session.close(true);
    }

    public LanComputerLink(IoSession session, String deviceId, BaseLinkProvider linkProvider) {
        super(deviceId, linkProvider);
        this.session = session;
    }

    @Override
    public boolean sendPackage(NetworkPackage np) {
        if (session == null) {
            Log.e("LanComputerLink","sendPackage failed: not yet connected");
            return false;
        } else {
            session.write(np.serialize());
            return true;
        }
    }

    public void injectNetworkPackage(NetworkPackage np) {
        packageReceived(np);
    }
}
