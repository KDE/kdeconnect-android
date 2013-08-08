package org.kde.connect.LinkProviders;

import android.util.Log;

import org.kde.connect.ComputerLinks.BaseComputerLink;
import org.kde.connect.NetworkPackage;

import java.util.ArrayList;

public abstract class BaseLinkProvider {

    private ArrayList<ConnectionReceiver> connectionReceivers = new ArrayList<ConnectionReceiver>();

    public interface ConnectionReceiver {
        public void onConnectionAccepted(NetworkPackage identityPackage, BaseComputerLink link);
        public void onConnectionLost(BaseComputerLink link);
    }

    public void addConnectionReceiver(ConnectionReceiver cr) {
        connectionReceivers.add(cr);
    }

    public boolean removeConnectionReceiver(ConnectionReceiver cr) {
        return connectionReceivers.remove(cr);
    }

    //These two should be called when the provider links to a new computer
    protected void connectionAccepted(NetworkPackage identityPackage, BaseComputerLink link) {
        Log.e("LinkProvider", "connectionAccepted");
        for(ConnectionReceiver cr : connectionReceivers) {
            cr.onConnectionAccepted(identityPackage,link);
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
    public abstract void onNetworkChange();

    public abstract int getPriority();
    public abstract String getName();

}
