package org.kde.connect.LinkProviders;

import org.kde.connect.ComputerLinks.BaseComputerLink;

public interface BaseLinkProvider {

    public interface ConnectionReceiver {
        public void onConnectionAccepted(BaseComputerLink link);
    }

    //To override
    public void reachComputers(ConnectionReceiver cr);


}
