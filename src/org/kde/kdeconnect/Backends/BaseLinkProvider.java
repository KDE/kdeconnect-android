/*
 * Copyright 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License or (at your option) version 3 or any later version
 * accepted by the membership of KDE e.V. (or its successor approved
 * by the membership of KDE e.V.), which shall act as a proxy
 * defined in Section 14 of version 3 of the license.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
*/

package org.kde.kdeconnect.Backends;

import org.kde.kdeconnect.NetworkPackage;

import java.util.concurrent.CopyOnWriteArrayList;

public abstract class BaseLinkProvider {

    private final CopyOnWriteArrayList<ConnectionReceiver> connectionReceivers = new CopyOnWriteArrayList<>();

    public interface ConnectionReceiver {
        void onConnectionReceived(NetworkPackage identityPackage, BaseLink link);
        void onConnectionLost(BaseLink link);
    }

    public void addConnectionReceiver(ConnectionReceiver cr) {
        connectionReceivers.add(cr);
    }

    public boolean removeConnectionReceiver(ConnectionReceiver cr) {
        return connectionReceivers.remove(cr);
    }

    //These two should be called when the provider links to a new computer
    protected void connectionAccepted(NetworkPackage identityPackage, BaseLink link) {
        //Log.i("KDE/LinkProvider", "connectionAccepted");
        for(ConnectionReceiver cr : connectionReceivers) {
            cr.onConnectionReceived(identityPackage, link);
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
