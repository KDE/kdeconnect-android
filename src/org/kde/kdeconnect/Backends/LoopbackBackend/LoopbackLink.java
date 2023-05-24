/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Backends.LoopbackBackend;

import android.content.Context;

import androidx.annotation.WorkerThread;

import org.kde.kdeconnect.Backends.BaseLink;
import org.kde.kdeconnect.Backends.BaseLinkProvider;
import org.kde.kdeconnect.Backends.BasePairingHandler;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.NetworkPacket;

public class LoopbackLink extends BaseLink {

    public LoopbackLink(Context context, BaseLinkProvider linkProvider) {
        super(context, "loopback", linkProvider);
    }

    @Override
    public String getName() {
        return "LoopbackLink";
    }

    @Override
    public BasePairingHandler getPairingHandler(Device device, BasePairingHandler.PairingHandlerCallback callback) {
        return new LoopbackPairingHandler(device, callback);
    }

    @WorkerThread
    @Override
    public boolean sendPacket(NetworkPacket in, Device.SendPacketStatusCallback callback, boolean sendPayloadFromSameThread) {
        packetReceived(in);
        if (in.hasPayload()) {
            callback.onPayloadProgressChanged(0);
            in.setPayload(in.getPayload());
            callback.onPayloadProgressChanged(100);
        }
        callback.onSuccess();
        return true;
    }

}
