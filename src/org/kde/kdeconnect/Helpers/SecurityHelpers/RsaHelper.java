/*
 * Copyright 2015 Albert Vaca Cintora <albertvaka@gmail.com>
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

package org.kde.kdeconnect.Helpers.SecurityHelpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.kde.kdeconnect.NetworkPackage;

import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

public class RsaHelper {

    public static void initialiseRsaKeys(Context context) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);

        if (!settings.contains("publicKey") || !settings.contains("privateKey")) {

            KeyPair keyPair;
            try {
                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
                keyGen.initialize(2048);
                keyPair = keyGen.genKeyPair();
            } catch(Exception e) {
                e.printStackTrace();
                Log.e("KDE/initializeRsaKeys", "Exception");
                return;
            }

            byte[] publicKey = keyPair.getPublic().getEncoded();
            byte[] privateKey = keyPair.getPrivate().getEncoded();

            SharedPreferences.Editor edit = settings.edit();
            edit.putString("publicKey", Base64.encodeToString(publicKey, 0).trim()+"\n");
            edit.putString("privateKey",Base64.encodeToString(privateKey, 0));
            edit.apply();

        }


/*
        // Encryption and decryption test
        //================================

        try {

            NetworkPackage np = NetworkPackage.createIdentityPackage(this);

            SharedPreferences globalSettings = PreferenceManager.getDefaultSharedPreferences(this);

            byte[] publicKeyBytes = Base64.decode(globalSettings.getString("publicKey",""), 0);
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(publicKeyBytes));

            np.encrypt(publicKey);

            byte[] privateKeyBytes = Base64.decode(globalSettings.getString("privateKey",""), 0);
            PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));

            NetworkPackage decrypted = np.decrypt(privateKey);
            Log.e("ENCRYPTION AND DECRYPTION TEST", decrypted.serialize());
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("ENCRYPTION AND DECRYPTION TEST","Exception: "+e);
        }
*/

    }

    public static PublicKey getPublicKey (Context context) throws Exception{
        try {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            byte[] publicKeyBytes = Base64.decode(settings.getString("publicKey", ""), 0);
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(publicKeyBytes));
            return publicKey;
        }catch (Exception e){
            throw e;
        }
    }

    public static PublicKey getPublicKey(Context context, String deviceId) throws Exception{
        try {
            SharedPreferences settings = context.getSharedPreferences(deviceId, Context.MODE_PRIVATE);
            byte[] publicKeyBytes = Base64.decode(settings.getString("publicKey", ""), 0);
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(publicKeyBytes));
            return publicKey;
        } catch (Exception e) {
            throw e;
        }
    }

    public static PrivateKey getPrivateKey(Context context) throws Exception{

        try {
            SharedPreferences globalSettings = PreferenceManager.getDefaultSharedPreferences(context);
            byte[] privateKeyBytes = Base64.decode(globalSettings.getString("privateKey", ""), 0);
            PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
            return privateKey;
        } catch (Exception e) {
            throw e;
        }

    }

    public static NetworkPackage encrypt(NetworkPackage np, PublicKey publicKey) throws GeneralSecurityException {

        String serialized = np.serialize();

        int chunkSize = 128;

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1PADDING");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);

        JSONArray chunks = new JSONArray();
        while (serialized.length() > 0) {
            if (serialized.length() < chunkSize) {
                chunkSize = serialized.length();
            }
            String chunk = serialized.substring(0, chunkSize);
            serialized = serialized.substring(chunkSize);
            byte[] chunkBytes = chunk.getBytes(Charset.defaultCharset());
            byte[] encryptedChunk;
            encryptedChunk = cipher.doFinal(chunkBytes);
            chunks.put(Base64.encodeToString(encryptedChunk, Base64.NO_WRAP));
        }

        //Log.i("NetworkPackage", "Encrypted " + chunks.length()+" chunks");

        NetworkPackage encrypted = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_ENCRYPTED);
        encrypted.set("data", chunks);
        encrypted.setPayload(np.getPayload(), np.getPayloadSize());
        return encrypted;

    }

    public static NetworkPackage decrypt(NetworkPackage np, PrivateKey privateKey)  throws GeneralSecurityException, JSONException {

        JSONArray chunks = np.getJSONArray("data");

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1PADDING");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);

        String decryptedJson = "";
        for (int i = 0; i < chunks.length(); i++) {
            byte[] encryptedChunk = Base64.decode(chunks.getString(i), Base64.NO_WRAP);
            String decryptedChunk = new String(cipher.doFinal(encryptedChunk));
            decryptedJson += decryptedChunk;
        }

        NetworkPackage decrypted = np.unserialize(decryptedJson);
        decrypted.setPayload(np.getPayload(), np.getPayloadSize());
        return decrypted;
    }


}
