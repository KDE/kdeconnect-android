package org.kde.connect.Locators;

import org.kde.connect.ComputerLinks.BaseComputerLink;

public interface BaseLocator {

    public interface ConnectionReceiver {
        public void onConnectionAccepted(BaseComputerLink link);
    }

    //To override
    public void reachComputers(ConnectionReceiver cr);


}
