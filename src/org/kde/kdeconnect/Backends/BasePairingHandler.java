/*
 * SPDX-FileCopyrightText: 2015 Vineet Garg <grg.vineet@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Backends;

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.NetworkPacket;

/**
 * This class separates the pairing interface for each type of link.
 * Since different links can pair via different methods, like for LanLink certificate and public key should be shared,
 * for Bluetooth link they should be paired via bluetooth etc.
 * Each "Device" instance maintains a hash map for these pairing handlers so that there can be single pairing handler per
 * per link type per device.
 * Pairing handler keeps information about device, latest link, and pair status of the link
 * During first pairing process, the pairing process is nearly same as old process.
 * After that if any one of the link is paired, then we can say that device is paired, so new link will pair automatically
 */

public abstract class BasePairingHandler {

    protected enum PairStatus{
        NotPaired,
        Requested,
        RequestedByPeer,
        Paired
    }

    public interface PairingHandlerCallback {
        void incomingRequest();
        void pairingDone();
        void pairingFailed(String error);
        void unpaired();
    }


    protected final Device mDevice;
    protected PairStatus mPairStatus;
    protected final PairingHandlerCallback mCallback;

    protected BasePairingHandler(Device device, PairingHandlerCallback callback) {
        this.mDevice = device;
        this.mCallback = callback;
    }

    protected boolean isPaired() {
        return mPairStatus == PairStatus.Paired;
    }

    public boolean isPairRequested() {
        return mPairStatus == PairStatus.Requested;
    }

    public boolean isPairRequestedByPeer() {
        return mPairStatus == PairStatus.RequestedByPeer;
    }

    /* To be implemented by respective pairing handler */
    public abstract void packageReceived(NetworkPacket np) throws Exception;
    public abstract void requestPairing();
    public abstract void acceptPairing();
    public abstract void rejectPairing();
    public abstract void unpair();

}
