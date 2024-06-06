/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Backends;

import android.net.Network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.kde.kdeconnect.DeviceInfo;

import java.util.concurrent.CopyOnWriteArrayList;

public abstract class BaseLinkProvider {

    public interface ConnectionReceiver {
        void onConnectionReceived(@NonNull final BaseLink link);
        void onDeviceInfoUpdated(@NonNull final DeviceInfo deviceInfo);
        void onConnectionLost(BaseLink link);
    }

    private final CopyOnWriteArrayList<ConnectionReceiver> connectionReceivers = new CopyOnWriteArrayList<>();

    public void addConnectionReceiver(ConnectionReceiver cr) {
        connectionReceivers.add(cr);
    }

    public boolean removeConnectionReceiver(ConnectionReceiver cr) {
        return connectionReceivers.remove(cr);
    }

    /**
     * To be called from the child classes when a link to a new device is established
     */
    protected void onConnectionReceived(@NonNull final BaseLink link) {
        //Log.i("KDE/LinkProvider", "onConnectionReceived");
        for(ConnectionReceiver cr : connectionReceivers) {
            cr.onConnectionReceived(link);
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

    /**
     * To be called from the child classes when we discover new DeviceInfo for an already linked device.
     */
    protected void onDeviceInfoUpdated(@NonNull final DeviceInfo deviceInfo) {
        for(ConnectionReceiver cr : connectionReceivers) {
            cr.onDeviceInfoUpdated(deviceInfo);
        }
    }

    public abstract void onStart();
    public abstract void onStop();
    public abstract void onNetworkChange(@Nullable Network network);
    public abstract String getName();

    public abstract int getPriority();
}
