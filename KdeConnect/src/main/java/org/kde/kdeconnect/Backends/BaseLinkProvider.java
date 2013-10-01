package org.kde.kdeconnect.Backends;

import android.util.Log;

import org.kde.kdeconnect.Backends.BaseLink;
import org.kde.kdeconnect.NetworkPackage;

import java.util.ArrayList;

public abstract class BaseLinkProvider {

    private ArrayList<ConnectionReceiver> connectionReceivers = new ArrayList<ConnectionReceiver>();

    public interface ConnectionReceiver {
        public void onConnectionReceived(NetworkPackage identityPackage, BaseLink link);
        public void onConnectionLost(BaseLink link);
    }

    public void addConnectionReceiver(ConnectionReceiver cr) {
        connectionReceivers.add(cr);
    }

    public boolean removeConnectionReceiver(ConnectionReceiver cr) {
        return connectionReceivers.remove(cr);
    }

    //These two should be called when the provider links to a new computer
    protected void connectionAccepted(NetworkPackage identityPackage, BaseLink link) {
        Log.i("LinkProvider", "connectionAccepted");
        for(ConnectionReceiver cr : connectionReceivers) {
            cr.onConnectionReceived(identityPackage, link);
        }
    }
    protected void connectionLost(BaseLink link) {
        Log.i("LinkProvider", "connectionLost");
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
