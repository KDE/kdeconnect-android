/*
 * SPDX-FileCopyrightText: 2015 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Helpers.SecurityHelpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public class RsaHelper {

    public static void initialiseRsaKeys(Context context) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);

        if (!settings.contains("publicKey") || !settings.contains("privateKey")) {

            KeyPair keyPair;
            try {
                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
                keyGen.initialize(2048);
                keyPair = keyGen.genKeyPair();
            } catch (Exception e) {
                Log.e("KDE/initializeRsaKeys", "Exception", e);
                return;
            }

            byte[] publicKey = keyPair.getPublic().getEncoded();
            byte[] privateKey = keyPair.getPrivate().getEncoded();

            SharedPreferences.Editor edit = settings.edit();
            edit.putString("publicKey", Base64.encodeToString(publicKey, 0).trim() + "\n");
            edit.putString("privateKey", Base64.encodeToString(privateKey, 0));
            edit.apply();

        }

    }

    public static PublicKey getPublicKey(Context context) throws GeneralSecurityException {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        byte[] publicKeyBytes = Base64.decode(settings.getString("publicKey", ""), 0);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(publicKeyBytes));
    }

    public static PrivateKey getPrivateKey(Context context) throws GeneralSecurityException {
        SharedPreferences globalSettings = PreferenceManager.getDefaultSharedPreferences(context);
        byte[] privateKeyBytes = Base64.decode(globalSettings.getString("privateKey", ""), 0);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
    }



}
