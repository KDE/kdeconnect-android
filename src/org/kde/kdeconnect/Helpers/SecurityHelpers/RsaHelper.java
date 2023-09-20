/*
 * SPDX-FileCopyrightText: 2015 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Helpers.SecurityHelpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public class RsaHelper {

    public static void initialiseRsaKeys(Context context) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);

        if (!settings.contains("publicKey") || !settings.contains("privateKey")) {

            KeyPair keyPair;
            String keyAlgorithm;
            try {
                KeyPairGenerator keyGen;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    keyAlgorithm = KeyProperties.KEY_ALGORITHM_EC;
                    keyGen = KeyPairGenerator.getInstance(keyAlgorithm);
                    ECGenParameterSpec spec = new ECGenParameterSpec("secp256r1");
                    keyGen.initialize(spec);
                } else {
                    keyAlgorithm = "RSA";
                    keyGen = KeyPairGenerator.getInstance(keyAlgorithm);
                    keyGen.initialize(2048);
                }
                keyPair = keyGen.generateKeyPair();
            } catch (Exception e) {
                Log.e("KDE/initializeRsaKeys", "Exception", e);
                return;
            }

            byte[] publicKey = keyPair.getPublic().getEncoded();
            byte[] privateKey = keyPair.getPrivate().getEncoded();

            SharedPreferences.Editor edit = settings.edit();
            edit.putString("publicKey", Base64.encodeToString(publicKey, 0).trim() + "\n");
            edit.putString("privateKey", Base64.encodeToString(privateKey, 0));
            edit.putString("keyAlgorithm", keyAlgorithm);
            edit.apply();

        }

    }

    public static PublicKey getPublicKey(Context context) throws GeneralSecurityException {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        byte[] publicKeyBytes = Base64.decode(settings.getString("publicKey", ""), 0);
        // For backwards compat: if no keyAlgorithm setting is set, it means it was generated using RSA
        String keyAlgorithm = settings.getString("keyAlgorithm", "RSA");
        return KeyFactory.getInstance(keyAlgorithm).generatePublic(new X509EncodedKeySpec(publicKeyBytes));
    }

    public static PrivateKey getPrivateKey(Context context) throws GeneralSecurityException {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        byte[] privateKeyBytes = Base64.decode(settings.getString("privateKey", ""), 0);
        // For backwards compat: if no keyAlgorithm setting is set, it means it was generated using RSA
        String keyAlgorithm = settings.getString("keyAlgorithm", "RSA");
        return KeyFactory.getInstance(keyAlgorithm).generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
    }



}
