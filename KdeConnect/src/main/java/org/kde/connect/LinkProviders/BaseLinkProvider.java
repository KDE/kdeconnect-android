package org.kde.connect.LinkProviders;

import android.util.Log;

import org.kde.connect.ComputerLinks.BaseComputerLink;

import java.util.ArrayList;

public abstract class BaseLinkProvider {

    ArrayList<ConnectionReceiver> connectionReceivers = new ArrayList<ConnectionReceiver>();

    public interface ConnectionReceiver {
        public void onConnectionAccepted(String deviceId, String name, BaseComputerLink link);
        public void onConnectionLost(BaseComputerLink link);
    }

    public void addConnectionReceiver(ConnectionReceiver cr) {
        connectionReceivers.add(cr);
    }

    public boolean removeConnectionReceiver(ConnectionReceiver cr) {
        return connectionReceivers.remove(cr);
    }

    //These two should be called when the provider links to a new computer
    protected void connectionAccepted(String deviceId, String name, BaseComputerLink link) {
        Log.e("LinkProvider", "connectionAccepted");
        for(ConnectionReceiver cr : connectionReceivers) {
            cr.onConnectionAccepted(deviceId,name,link);
        }
    }
    protected void connectionLost(BaseComputerLink link) {
        Log.e("LinkProvider", "connectionLost");
        for(ConnectionReceiver cr : connectionReceivers) {
            cr.onConnectionLost(link);
        }
    }

    //To override
    public abstract void onStart();
    public abstract void onStop();

    public abstract int getPriority();
    public abstract String getName();

}
