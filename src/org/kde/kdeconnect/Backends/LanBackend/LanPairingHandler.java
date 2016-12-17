/*
 * Copyright 2015 Vineet Garg <grg.vineet@gmail.com>
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

package org.kde.kdeconnect.Backends.LanBackend;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

import org.kde.kdeconnect.Backends.BasePairingHandler;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.NetworkPackage;
import org.kde.kdeconnect_tp.R;

import java.security.KeyFactory;
import java.security.cert.CertificateEncodingException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Timer;
import java.util.TimerTask;

public class LanPairingHandler extends BasePairingHandler {

    private Timer mPairingTimer;

    public LanPairingHandler(Device device, final PairingHandlerCallback callback) {
        super(device, callback);

        if (device.isPaired()) {
            mPairStatus = PairStatus.Paired;
        } else {
            mPairStatus = PairStatus.NotPaired;
        }
    }

    private NetworkPackage createPairPackage() {
        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_PAIR);
        np.set("pair", true);
        SharedPreferences globalSettings = PreferenceManager.getDefaultSharedPreferences(mDevice.getContext());
        String publicKey = "-----BEGIN PUBLIC KEY-----\n" + globalSettings.getString("publicKey", "").trim()+ "\n-----END PUBLIC KEY-----\n";
        np.set("publicKey", publicKey);
        return np;
    }

    @Override
    public void packageReceived(NetworkPackage np) throws Exception{

        boolean wantsPair = np.getBoolean("pair");

        if (wantsPair == isPaired()) {
            if (mPairStatus == PairStatus.Requested) {
                //Log.e("Device","Unpairing (pair rejected)");
                mPairStatus = PairStatus.NotPaired;
                hidePairingNotification();
                mCallback.pairingFailed(mDevice.getContext().getString(R.string.error_canceled_by_other_peer));
            }
            return;
        }

        if (wantsPair) {

            //Retrieve their public key
            try {
                String publicKeyContent = np.getString("publicKey").replace("-----BEGIN PUBLIC KEY-----\n","").replace("-----END PUBLIC KEY-----\n", "");
                byte[] publicKeyBytes = Base64.decode(publicKeyContent, 0);
                mDevice.publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(publicKeyBytes));
            } catch (Exception e) {
                //IGNORE
            }

            if (mPairStatus == PairStatus.Requested)  { //We started pairing

                hidePairingNotification();

                pairingDone();

            } else {

                // If device is already paired, accept pairing silently
                if (mDevice.isPaired()) {
                    acceptPairing();
                    return;
                }

                // Pairing notifications are still managed by device as there is no other way to
                // know about notificationId to cancel notification when PairActivity is started
                // Even putting notificationId in intent does not work because PairActivity can be
                // started from MainActivity too, so then notificationId cannot be set
                hidePairingNotification();
                mDevice.displayPairingNotification();

                mPairingTimer = new Timer();

                mPairingTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        Log.w("KDE/Device","Unpairing (timeout B)");
                        mPairStatus = PairStatus.NotPaired;
                        hidePairingNotification();
                    }
                }, 25*1000); //Time to show notification, waiting for user to accept (peer will timeout in 30 seconds)
                mPairStatus = PairStatus.RequestedByPeer;
                mCallback.incomingRequest();

            }
        } else {
            Log.i("KDE/Pairing", "Unpair request");

            if (mPairStatus == PairStatus.Requested) {
                hidePairingNotification();
                mCallback.pairingFailed(mDevice.getContext().getString(R.string.error_canceled_by_other_peer));
            } else if (mPairStatus == PairStatus.Paired) {
                mCallback.unpaired();
            }

            mPairStatus = PairStatus.NotPaired;

        }

    }

    @Override
    public void requestPairing() {

        Device.SendPackageStatusCallback statusCallback = new Device.SendPackageStatusCallback() {
            @Override
            protected void onSuccess() {
                hidePairingNotification(); //Will stop the pairingTimer if it was running
                mPairingTimer = new Timer();
                mPairingTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        mCallback.pairingFailed(mDevice.getContext().getString(R.string.error_timed_out));
                        Log.w("KDE/Device","Unpairing (timeout A)");
                        mPairStatus = PairStatus.NotPaired;
                    }
                }, 30*1000); //Time to wait for the other to accept
                mPairStatus = PairStatus.Requested;
            }

            @Override
            protected void onFailure(Throwable e) {
                mCallback.pairingFailed(mDevice.getContext().getString(R.string.error_could_not_send_package));
            }
        };
        mDevice.sendPackage(createPairPackage(), statusCallback);
    }

    public void hidePairingNotification() {
        mDevice.hidePairingNotification();
        if (mPairingTimer != null) {
            mPairingTimer .cancel();
        }
    }

    @Override
    public void acceptPairing() {
        hidePairingNotification();
        Device.SendPackageStatusCallback statusCallback = new Device.SendPackageStatusCallback() {
            @Override
            protected void onSuccess() {
                pairingDone();
            }

            @Override
            protected void onFailure(Throwable e) {
                mCallback.pairingFailed(mDevice.getContext().getString(R.string.error_not_reachable));
            }
        };
        mDevice.sendPackage(createPairPackage(), statusCallback);
    }

    @Override
    public void rejectPairing() {
        hidePairingNotification();
        mPairStatus = PairStatus.NotPaired;
        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_PAIR);
        np.set("pair", false);
        mDevice.sendPackage(np);
    }

    public void pairingDone() {
        // Store device information needed to create a Device object in a future
        //Log.e("KDE/PairingDone", "Pairing Done");
        SharedPreferences.Editor editor = mDevice.getContext().getSharedPreferences(mDevice.getDeviceId(), Context.MODE_PRIVATE).edit();

        if (mDevice.publicKey != null) {
            try {
                String encodedPublicKey = Base64.encodeToString(mDevice.publicKey.getEncoded(), 0);
                editor.putString("publicKey", encodedPublicKey);
            } catch (Exception e) {
                Log.e("KDE/PairingDone", "Error encoding public key");
            }
        }

        try {
            String encodedCertificate = Base64.encodeToString(mDevice.certificate.getEncoded(), 0);
            editor.putString("certificate", encodedCertificate);
        } catch (NullPointerException n) {
            Log.w("KDE/PairingDone", "Certificate is null, remote device does not support ssl");
        } catch (CertificateEncodingException c) {
            Log.e("KDE/PairingDOne", "Error encoding certificate");
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("KDE/Pairng", "Exception");
        }
        editor.apply();

        mPairStatus = PairStatus.Paired;
        mCallback.pairingDone();

    }

    @Override
    public void unpair() {
        mPairStatus = PairStatus.NotPaired;
        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_PAIR);
        np.set("pair", false);
        mDevice.sendPackage(np);
    }
}
