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

import java.util.concurrent.CopyOnWriteArrayList;

public abstract class BaseLinkProvider {

    private final CopyOnWriteArrayList<ConnectionReceiver> connectionReceivers = new CopyOnWriteArrayList<>();

    public interface ConnectionReceiver {
        void onOfferAdded(DeviceOffer offer);
        void onOfferRemoved(String id);
        void onLinkConnected(DeviceOffer offer, DeviceLink link);
        void onConnectionFailed(DeviceOffer offer, String reason);
        void onLinkDisconnected(DeviceLink link);
    }

    public void addConnectionReceiver(ConnectionReceiver cr) {
        connectionReceivers.add(cr);
    }
    public boolean removeConnectionReceiver(ConnectionReceiver cr) { return connectionReceivers.remove(cr); }

    protected void onOfferAdded(DeviceOffer offer) {
        for(ConnectionReceiver cr : connectionReceivers) {
            cr.onOfferAdded(offer);
        }
    }
    protected void onOfferRemoved(String id) {
        for(ConnectionReceiver cr : connectionReceivers) {
            cr.onOfferRemoved(id);
        }
    }

    protected void onLinkConnected(DeviceOffer offer, DeviceLink link) {
        for(ConnectionReceiver cr : connectionReceivers) {
            cr.onLinkConnected(offer, link);
        }
    }
    protected void onConnectionFailed(DeviceOffer offer, String reason) {
        for(ConnectionReceiver cr : connectionReceivers) {
            cr.onConnectionFailed(offer, reason);
        }
    }
    protected void onLinkDisconnected(DeviceLink link) {
        for(ConnectionReceiver cr : connectionReceivers) {
            cr.onLinkDisconnected(link);
        }
    }

    //To override
    public abstract void refresh();
    public abstract String getName();
    public abstract void connect(DeviceOffer id);
}
