/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Backends;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.DeviceInfo;
import org.kde.kdeconnect.NetworkPacket;

import java.io.IOException;
import java.util.ArrayList;


public abstract class BaseLink {

    public interface PacketReceiver {
        void onPacketReceived(@NonNull NetworkPacket np);
    }

    protected final Context context;
    private final BaseLinkProvider linkProvider;
    private final ArrayList<PacketReceiver> receivers = new ArrayList<>();

    protected BaseLink(@NonNull Context context, @NonNull BaseLinkProvider linkProvider) {
        this.context = context;
        this.linkProvider = linkProvider;
    }

    /* To be implemented by each link for pairing handlers */
    public abstract String getName();

    public abstract DeviceInfo getDeviceInfo();

    public String getDeviceId() {
        return getDeviceInfo().id;
    }

    public BaseLinkProvider getLinkProvider() {
        return linkProvider;
    }

    public void addPacketReceiver(@NonNull PacketReceiver pr) {
        receivers.add(pr);
    }
    public void removePacketReceiver(@NonNull PacketReceiver pr) {
        receivers.remove(pr);
    }

    //Should be called from a background thread listening for packets
    public void packetReceived(@NonNull NetworkPacket np) {
        for(PacketReceiver pr : receivers) {
            pr.onPacketReceived(np);
        }
    }

    public void disconnect() {
        linkProvider.onConnectionLost(this);
    }

    //TO OVERRIDE, should be sync. If sendPayloadFromSameThread is false, it should only block to send the packet but start a separate thread to send the payload.
    @WorkerThread
    public abstract boolean sendPacket(@NonNull NetworkPacket np, @NonNull Device.SendPacketStatusCallback callback, boolean sendPayloadFromSameThread) throws IOException;
}
