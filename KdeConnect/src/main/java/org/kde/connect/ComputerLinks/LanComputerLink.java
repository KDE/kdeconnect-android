package org.kde.connect.ComputerLinks;

import android.util.Log;

import org.apache.mina.core.session.IoSession;
import org.kde.connect.LinkProviders.BaseLinkProvider;
import org.kde.connect.NetworkPackage;

public class LanComputerLink extends BaseComputerLink {

    private IoSession session = null;

    public void disconnect() {
        Log.e("NioSessionComputerLink","Disconnect: "+session.getRemoteAddress().toString());
        session.close(true);
    }

    public LanComputerLink(IoSession session, String deviceId, BaseLinkProvider linkProvider) {
        super(deviceId, linkProvider);
        this.session = session;
    }

    @Override
    public boolean sendPackage(NetworkPackage np) {
        Log.e("TcpComputerLink", "sendPackage");
        if (session == null) {
            Log.e("TcpComputerLink","not yet connected");
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
