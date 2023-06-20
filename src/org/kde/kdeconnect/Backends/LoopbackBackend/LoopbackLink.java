/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Backends.LoopbackBackend;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.kde.kdeconnect.Backends.BaseLink;
import org.kde.kdeconnect.Backends.BaseLinkProvider;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.DeviceInfo;
import org.kde.kdeconnect.Helpers.DeviceHelper;
import org.kde.kdeconnect.NetworkPacket;

public class LoopbackLink extends BaseLink {

    public LoopbackLink(Context context, BaseLinkProvider linkProvider) {
        super(context, linkProvider);
    }

    @Override
    public String getName() {
        return "LoopbackLink";
    }

    @WorkerThread
    @Override
    public boolean sendPacket(@NonNull NetworkPacket in, @NonNull Device.SendPacketStatusCallback callback, boolean sendPayloadFromSameThread) {
        packetReceived(in);
        if (in.hasPayload()) {
            callback.onPayloadProgressChanged(0);
            in.setPayload(in.getPayload());
            callback.onPayloadProgressChanged(100);
        }
        callback.onSuccess();
        return true;
    }

    @Override
    public DeviceInfo getDeviceInfo() {
        return DeviceHelper.getDeviceInfo(context);
    }

}
