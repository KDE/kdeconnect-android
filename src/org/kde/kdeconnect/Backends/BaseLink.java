/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Backends;

import android.content.Context;

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.NetworkPacket;

import java.security.PrivateKey;
import java.util.ArrayList;

import androidx.annotation.WorkerThread;


public abstract class BaseLink {

    protected final Context context;

    public interface PacketReceiver {
        void onPacketReceived(NetworkPacket np);
    }

    private final BaseLinkProvider linkProvider;
    private final String deviceId;
    private final ArrayList<PacketReceiver> receivers = new ArrayList<>();
    protected PrivateKey privateKey;

    protected BaseLink(Context context, String deviceId, BaseLinkProvider linkProvider) {
        this.context = context;        
        this.linkProvider = linkProvider;
        this.deviceId = deviceId;
    }

    /* To be implemented by each link for pairing handlers */
    public abstract String getName();
    public abstract BasePairingHandler getPairingHandler(Device device, BasePairingHandler.PairingHandlerCallback callback);

    public String getDeviceId() {
        return deviceId;
    }

    public void setPrivateKey(PrivateKey key) {
        privateKey = key;
    }

    public BaseLinkProvider getLinkProvider() {
        return linkProvider;
    }

    //The daemon will periodically destroy unpaired links if this returns false
    public boolean linkShouldBeKeptAlive() {
        return false;
    }

    public void addPacketReceiver(PacketReceiver pr) {
        receivers.add(pr);
    }
    public void removePacketReceiver(PacketReceiver pr) {
        receivers.remove(pr);
    }

    //Should be called from a background thread listening to packages
    protected void packageReceived(NetworkPacket np) {
        for(PacketReceiver pr : receivers) {
            pr.onPacketReceived(np);
        }
    }

    public void disconnect() {
        linkProvider.connectionLost(this);
    }

    //TO OVERRIDE, should be sync
    @WorkerThread
    public abstract boolean sendPacket(NetworkPacket np, Device.SendPacketStatusCallback callback);
}
