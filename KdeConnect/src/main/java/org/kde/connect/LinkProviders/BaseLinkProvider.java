package org.kde.connect.LinkProviders;

import org.kde.connect.ComputerLinks.BaseComputerLink;

public interface BaseLinkProvider {

    public interface ConnectionReceiver {
        public void onConnectionAccepted(String deviceId, String name, BaseComputerLink link);
        public void onConnectionLost(BaseComputerLink link);
    }

    //To override
    public void reachComputers(ConnectionReceiver cr);
    public int getPriority();
    public String getName();

}
