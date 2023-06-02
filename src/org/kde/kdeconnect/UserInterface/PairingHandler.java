/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.UserInterface;

import android.util.Log;

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect_tp.R;

import java.util.Timer;
import java.util.TimerTask;

public class PairingHandler {

    public enum PairState {
        NotPaired,
        Requested,
        RequestedByPeer,
        Paired
    }

    public interface PairingCallback {
        void incomingPairRequest();

        void pairingFailed(String error);

        void pairingSuccessful();

        void unpaired();
    }

    protected final Device mDevice;
    protected PairState mPairState;
    protected final PairingCallback mCallback;

    public PairState getState() {
        return mPairState;
    }

    private Timer mPairingTimer;

    public PairingHandler(Device device, final PairingCallback callback, PairState initialState) {
        this.mDevice = device;
        this.mCallback = callback;
        this.mPairState = initialState;
    }

    public void packetReceived(NetworkPacket np) {
        cancelTimer();
        boolean wantsPair = np.getBoolean("pair");
        if (wantsPair) {
            switch (mPairState) {
                case Requested: // We started pairing and tis is a confirmation
                    pairingDone();
                    break;
                case RequestedByPeer:
                    Log.w("PairingHandler", "Ignoring second pairing request before the first one timed out");
                    break;
                case Paired:
                    Log.w("PairingHandler", "Auto-accepting pairing request from a device we already trusted");
                    acceptPairing();
                case NotPaired:
                    mPairState = PairState.RequestedByPeer;

                    mPairingTimer = new Timer();
                    mPairingTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            Log.w("PairingHandler", "Unpairing (timeout after we started pairing)");
                            mPairState = PairState.NotPaired;
                            mCallback.pairingFailed(mDevice.getContext().getString(R.string.error_timed_out));
                        }
                    }, 25 * 1000); //Time to show notification, waiting for user to accept (peer will timeout in 30 seconds)

                    mCallback.incomingPairRequest();
                    break;
            }
        } else {
            Log.i("PairingHandler", "Unpair request received");
            switch (mPairState) {
                case NotPaired:
                    Log.i("PairingHandler", "Ignoring unpair request for already unpaired device");
                    break;
                case Requested: // We started pairing and got rejected
                case RequestedByPeer: // They stared pairing, then cancelled
                    mCallback.pairingFailed(mDevice.getContext().getString(R.string.error_canceled_by_other_peer));
                    break;
                case Paired:
                    mCallback.unpaired();
                    break;
            }
            mPairState = PairState.NotPaired;
        }
    }

    public void requestPairing() {
        cancelTimer();

        if (mPairState == PairState.Paired) {
            Log.w("PairingHandler", "requestPairing was called on an already paired device");
            mCallback.pairingFailed(mDevice.getContext().getString(R.string.error_already_paired));
            return;
        }

        if (mPairState == PairState.RequestedByPeer) {
            Log.w("PairingHandler", "Pairing already started by the other end, accepting their request.");
            acceptPairing();
            return;
        }

        if (!mDevice.isReachable()) {
            mCallback.pairingFailed(mDevice.getContext().getString(R.string.error_not_reachable));
            return;
        }

        mPairState = PairState.Requested;

        mPairingTimer = new Timer();
        mPairingTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                Log.w("PairingHandler","Unpairing (timeout after receiving pair request)");
                mPairState = PairState.NotPaired;
                mCallback.pairingFailed(mDevice.getContext().getString(R.string.error_timed_out));
            }
        }, 30*1000); //Time to wait for the other to accept

        Device.SendPacketStatusCallback statusCallback = new Device.SendPacketStatusCallback() {
            @Override
            public void onSuccess() { }

            @Override
            public void onFailure(Throwable e) {
                cancelTimer();
                Log.e("PairingHandler", "Exception sending pairing request", e);
                mPairState = PairState.NotPaired;
                mCallback.pairingFailed(mDevice.getContext().getString(R.string.runcommand_notreachable));
            }
        };
        NetworkPacket np = new NetworkPacket(NetworkPacket.PACKET_TYPE_PAIR);
        np.set("pair", true);
        mDevice.sendPacket(np, statusCallback);
    }

    public void acceptPairing() {
        cancelTimer();
        Device.SendPacketStatusCallback StateCallback = new Device.SendPacketStatusCallback() {
            @Override
            public void onSuccess() {
                pairingDone();
            }

            @Override
            public void onFailure(Throwable e) {
                Log.e("PairingHandler", "Exception sending accept pairing packet", e);
                mPairState = PairState.NotPaired;
                mCallback.pairingFailed(mDevice.getContext().getString(R.string.error_not_reachable));
            }
        };
        NetworkPacket np = new NetworkPacket(NetworkPacket.PACKET_TYPE_PAIR);
        np.set("pair", true);
        mDevice.sendPacket(np, StateCallback);
    }

    public void cancelPairing() {
        cancelTimer();
        mPairState = PairState.NotPaired;
        NetworkPacket np = new NetworkPacket(NetworkPacket.PACKET_TYPE_PAIR);
        np.set("pair", false);
        mDevice.sendPacket(np);
        mCallback.pairingFailed(mDevice.getContext().getString(R.string.error_canceled_by_user));
    }

    private void pairingDone() {
        Log.i("PairingHandler", "Pairing done");
        mPairState = PairState.Paired;
        try {
            mCallback.pairingSuccessful();
        } catch (Exception e) {
            Log.e("PairingHandler", "Exception in pairingSuccessful callback, unpairing");
            e.printStackTrace();
            mPairState = PairState.NotPaired;
        }
    }

    public void unpair() {
        mPairState = PairState.NotPaired;
        NetworkPacket np = new NetworkPacket(NetworkPacket.PACKET_TYPE_PAIR);
        np.set("pair", false);
        mDevice.sendPacket(np);
        mCallback.unpaired();
    }

    private void cancelTimer() {
        if (mPairingTimer != null) {
            mPairingTimer.cancel();
        }
    }
}
