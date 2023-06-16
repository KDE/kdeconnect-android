/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Backends;

import androidx.annotation.NonNull;

import org.kde.kdeconnect.NetworkPacket;

import java.security.cert.Certificate;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class BaseLinkProvider {

    private final CopyOnWriteArrayList<ConnectionReceiver> connectionReceivers = new CopyOnWriteArrayList<>();

    public interface ConnectionReceiver {
        void onConnectionReceived(@NonNull final String deviceId,
                                  @NonNull final Certificate certificate,
                                  @NonNull final NetworkPacket identityPacket,
                                  @NonNull final BaseLink link);
        void onConnectionLost(BaseLink link);
    }

    public void addConnectionReceiver(ConnectionReceiver cr) {
        connectionReceivers.add(cr);
    }

    public boolean removeConnectionReceiver(ConnectionReceiver cr) {
        return connectionReceivers.remove(cr);
    }

    /**
     * To be called from the child classes when a link to a new device is established
     */
    protected void onConnectionReceived(@NonNull final String deviceId,
                                      @NonNull final Certificate certificate,
                                      @NonNull final NetworkPacket identityPacket,
                                      @NonNull final BaseLink link) {
        //Log.i("KDE/LinkProvider", "onConnectionReceived");
        for(ConnectionReceiver cr : connectionReceivers) {
            cr.onConnectionReceived(deviceId, certificate, identityPacket, link);
        }
    }

    /**
     * To be called from the child classes when a link to an existing device is disconnected
     */
    public void onConnectionLost(BaseLink link) {
        //Log.i("KDE/LinkProvider", "connectionLost");
        for(ConnectionReceiver cr : connectionReceivers) {
            cr.onConnectionLost(link);
        }
    }

    public abstract void onStart();
    public abstract void onStop();
    public abstract void onNetworkChange();
    public abstract String getName();

}
