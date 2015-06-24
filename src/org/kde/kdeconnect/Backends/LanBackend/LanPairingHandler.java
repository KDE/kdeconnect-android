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

public class LanPairingHandler extends BasePairingHandler{

    @Override
    public void packageReceived(Device device, NetworkPackage np) throws Exception{
        try {
            String publicKeyContent = np.getString("publicKey").replace("-----BEGIN PUBLIC KEY-----\n","").replace("-----END PUBLIC KEY-----\n", "");
            byte[] publicKeyBytes = Base64.decode(publicKeyContent, 0);
            device.publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(publicKeyBytes));
        } catch(Exception e) {
            e.printStackTrace();
            Log.e("KDE/Device","Pairing exception: Received incorrect key");
            throw e;
        }
    }

    @Override
    public void requestPairing(Device device, NetworkPackage np) {
        SharedPreferences globalSettings = PreferenceManager.getDefaultSharedPreferences(device.getContext());
        String publicKey = "-----BEGIN PUBLIC KEY-----\n" + globalSettings.getString("publicKey", "").trim()+ "\n-----END PUBLIC KEY-----\n";
        np.set("publicKey", publicKey);
    }

    @Override
    public void accept_pairing(Device device, NetworkPackage np) {
        SharedPreferences globalSettings = PreferenceManager.getDefaultSharedPreferences(device.getContext());
        String publicKey = "-----BEGIN PUBLIC KEY-----\n" + globalSettings.getString("publicKey", "").trim()+ "\n-----END PUBLIC KEY-----\n";
        np.set("publicKey", publicKey);
    }

    @Override
    public void pairingDone(Device device) {
        //Store device information needed to create a Device object in a future
        SharedPreferences.Editor editor = device.getContext().getSharedPreferences(device.getDeviceId(), Context.MODE_PRIVATE).edit();

        editor.putString("deviceName", device.getName());
        editor.putString("deviceType", device.getDeviceType().toString());
        String encodedPublicKey = Base64.encodeToString(device.publicKey.getEncoded(), 0);
        editor.putString("publicKey", encodedPublicKey);
        try {
            String encodedCertificate = Base64.encodeToString(device.certificate.getEncoded(), 0);
            editor.putString("certificate", encodedCertificate);
        } catch (NullPointerException n) {
            Log.e("KDE/PairingDone", "Certificate is null, remote device does not support ssl");
        } catch (CertificateEncodingException c) {
            Log.e("KDE/PairingDOne", "Error encoding certificate");
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("KDE/Pairng", "Some exception while encoding string");
        }
        editor.apply();

    }
}
