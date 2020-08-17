/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Backends;

import org.kde.kdeconnect.NetworkPacket;

import java.util.concurrent.CopyOnWriteArrayList;

public abstract class BaseLinkProvider {

    private final CopyOnWriteArrayList<ConnectionReceiver> connectionReceivers = new CopyOnWriteArrayList<>();

    public interface ConnectionReceiver {
        void onConnectionReceived(NetworkPacket identityPacket, BaseLink link);
        void onConnectionLost(BaseLink link);
    }

    public void addConnectionReceiver(ConnectionReceiver cr) {
        connectionReceivers.add(cr);
    }

    public boolean removeConnectionReceiver(ConnectionReceiver cr) {
        return connectionReceivers.remove(cr);
    }

    //These two should be called when the provider links to a new computer
    protected void connectionAccepted(NetworkPacket identityPacket, BaseLink link) {
        //Log.i("KDE/LinkProvider", "connectionAccepted");
        for(ConnectionReceiver cr : connectionReceivers) {
            cr.onConnectionReceived(identityPacket, link);
        }
    }
    protected void connectionLost(BaseLink link) {
        //Log.i("KDE/LinkProvider", "connectionLost");
        for(ConnectionReceiver cr : connectionReceivers) {
            cr.onConnectionLost(link);
        }
    }

    //To override
    public abstract void onStart();
    public abstract void onStop();
    public abstract void onNetworkChange();

    //public abstract int getPriority();
    public abstract String getName();

}
