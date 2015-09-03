/*
 * Copyright 2014 Albert Vaca Cintora <albertvaka@gmail.com>
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

package org.kde.kdeconnect;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.kde.kdeconnect.Helpers.DeviceHelper;
import org.kde.kdeconnect.UserInterface.MainSettingsActivity;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;

import javax.crypto.Cipher;

public class NetworkPackage {

    public final static int ProtocolVersion = 5;

    //TODO: Move these to their respective plugins
    public final static String PACKAGE_TYPE_IDENTITY = "kdeconnect.identity";
    public final static String PACKAGE_TYPE_PAIR = "kdeconnect.pair";
    public final static String PACKAGE_TYPE_ENCRYPTED = "kdeconnect.encrypted";
    public final static String PACKAGE_TYPE_PING = "kdeconnect.ping";
    public final static String PACKAGE_TYPE_TELEPHONY = "kdeconnect.telephony";
    public final static String PACKAGE_TYPE_BATTERY = "kdeconnect.battery";
    public final static String PACKAGE_TYPE_SFTP = "kdeconnect.sftp";
    public final static String PACKAGE_TYPE_NOTIFICATION = "kdeconnect.notification";
    public final static String PACKAGE_TYPE_CLIPBOARD = "kdeconnect.clipboard";
    public final static String PACKAGE_TYPE_MPRIS = "kdeconnect.mpris";
    public final static String PACKAGE_TYPE_MOUSEPAD = "kdeconnect.mousepad";
    public final static String PACKAGE_TYPE_SHARE = "kdeconnect.share";

    private long mId;
    private String mType;
    private JSONObject mBody;
    private InputStream mPayload;
    private JSONObject mPayloadTransferInfo;
    private long mPayloadSize;

    private NetworkPackage() {

    }

    public NetworkPackage(String type) {
        mId = System.currentTimeMillis();
        mType = type;
        mBody = new JSONObject();
        mPayload = null;
        mPayloadSize = 0;
        mPayloadTransferInfo = new JSONObject();
    }

    public String getType() {
        return mType;
    }

    public long getId() {
        return mId;
    }

    //Most commons getters and setters defined for convenience
    public String getString(String key) { return mBody.optString(key,""); }
    public String getString(String key, String defaultValue) { return mBody.optString(key,defaultValue); }
    public void set(String key, String value) { if (value == null) return; try { mBody.put(key,value); } catch(Exception e) { } }
    public int getInt(String key) { return mBody.optInt(key,-1); }
    public int getInt(String key, int defaultValue) { return mBody.optInt(key,defaultValue); }
    public long getLong(String key) { return mBody.optLong(key,-1); }
    public long getLong(String key,long defaultValue) { return mBody.optLong(key,defaultValue); }
    public void set(String key, int value) { try { mBody.put(key,value); } catch(Exception e) { } }
    public boolean getBoolean(String key) { return mBody.optBoolean(key,false); }
    public boolean getBoolean(String key, boolean defaultValue) { return mBody.optBoolean(key,defaultValue); }
    public void set(String key, boolean value) { try { mBody.put(key,value); } catch(Exception e) { } }
    public double getDouble(String key) { return mBody.optDouble(key,Double.NaN); }
    public double getDouble(String key, double defaultValue) { return mBody.optDouble(key,defaultValue); }
    public void set(String key, double value) { try { mBody.put(key,value); } catch(Exception e) { } }
    public JSONArray getJSONArray(String key) { return mBody.optJSONArray(key); }
    public void set(String key, JSONArray value) { try { mBody.put(key,value); } catch(Exception e) { } }

    public ArrayList<String> getStringList(String key) {
        JSONArray jsonArray = mBody.optJSONArray(key);
        ArrayList<String> list = new ArrayList<>();
        int length = jsonArray.length();
        for (int i = 0; i < length; i++) {
            try {
                String str = jsonArray.getString(i);
                list.add(str);
            } catch(Exception e) {

            }
        }
        return list;
    }
    public ArrayList<String> getStringList(String key, ArrayList<String> defaultValue) {
        if (mBody.has(key)) return getStringList(key);
        else return defaultValue;
    }
    public void set(String key, ArrayList<String> value) {
        try {
            JSONArray jsonArray = new JSONArray();
            for(String str : value) {
                jsonArray.put(str);
            }
            mBody.put(key,jsonArray);
        } catch(Exception e) {

        }
    }
    public boolean has(String key) { return mBody.has(key); }

    public boolean isEncrypted() { return mType.equals(PACKAGE_TYPE_ENCRYPTED); }

    public String serialize() {
        JSONObject jo = new JSONObject();
        try {
            jo.put("id", mId);
            jo.put("type", mType);
            jo.put("body", mBody);
            if (hasPayload()) {
                jo.put("payloadSize", mPayloadSize);
                jo.put("payloadTransferInfo", mPayloadTransferInfo);
            }
        } catch(Exception e) {
            e.printStackTrace();
            Log.e("NetworkPackage", "Serialization exception");
        }

        //QJSon does not escape slashes, but Java JSONObject does. Converting to QJson format.
        String json = jo.toString().replace("\\/","/")+"\n";

        if (!isEncrypted()) {
            //Log.e("NetworkPackage.serialize", json);
        }

        return json;
    }

    static public NetworkPackage unserialize(String s) {

        NetworkPackage np = new NetworkPackage();
        try {
            JSONObject jo = new JSONObject(s);
            np.mId = jo.getLong("id");
            np.mType = jo.getString("type");
            np.mBody = jo.getJSONObject("body");
            if (jo.has("payloadSize")) {
                np.mPayloadTransferInfo = jo.getJSONObject("payloadTransferInfo");
                np.mPayloadSize = jo.getLong("payloadSize");
            } else {
                np.mPayloadTransferInfo = new JSONObject();
                np.mPayloadSize = 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("NetworkPackage", "Unserialization exception unserializing "+s);
            return null;
        }

        if (!np.isEncrypted()) {
            //Log.e("NetworkPackage.unserialize", s);
        }

        return np;
    }

    public NetworkPackage encrypt(PublicKey publicKey) throws GeneralSecurityException {

        String serialized = serialize();

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
        encrypted.setPayload(mPayload, mPayloadSize);
        return encrypted;

    }

    public NetworkPackage decrypt(PrivateKey privateKey)  throws GeneralSecurityException, JSONException {

        JSONArray chunks = mBody.getJSONArray("data");

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1PADDING");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);

        String decryptedJson = "";
        for (int i = 0; i < chunks.length(); i++) {
            byte[] encryptedChunk = Base64.decode(chunks.getString(i), Base64.NO_WRAP);
            String decryptedChunk = new String(cipher.doFinal(encryptedChunk));
            decryptedJson += decryptedChunk;
        }

        NetworkPackage decrypted = unserialize(decryptedJson);
        decrypted.setPayload(mPayload, mPayloadSize);
        return decrypted;
    }

    static public NetworkPackage createIdentityPackage(Context context) {

        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_IDENTITY);

        String deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        try {
            np.mBody.put("deviceId", deviceId);
            np.mBody.put("deviceName", DeviceHelper.getDeviceName(context));
            np.mBody.put("protocolVersion", NetworkPackage.ProtocolVersion);
            np.mBody.put("deviceType", DeviceHelper.isTablet()? "tablet" : "phone");
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("NetworkPacakge","Exception on createIdentityPackage");
        }

        return np;

    }


    static public NetworkPackage createPublicKeyPackage(Context context) {

        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_PAIR);

        np.set("pair", true);

        SharedPreferences globalSettings = PreferenceManager.getDefaultSharedPreferences(context);
        String publicKey = "-----BEGIN PUBLIC KEY-----\n" + globalSettings.getString("publicKey", "").trim()+ "\n-----END PUBLIC KEY-----\n";
        np.set("publicKey", publicKey);

        return np;

    }

    public void setPayload(byte[] data) {
        setPayload(new ByteArrayInputStream(data), data.length);
    }

    public void setPayload(InputStream stream, long size) {
        mPayload = stream;
        mPayloadSize = size;
    }

    /*public void setPayload(InputStream stream) {
        setPayload(stream, -1);
    }*/

    public InputStream getPayload() {
        return mPayload;
    }

    public long getPayloadSize() {
        return mPayloadSize;
    }

    public boolean hasPayload() {
        return (mPayload != null);
    }

    public boolean hasPayloadTransferInfo() {
        return (mPayloadTransferInfo.length() > 0);
    }

    public JSONObject getPayloadTransferInfo() {
        return mPayloadTransferInfo;
    }

    public void setPayloadTransferInfo(JSONObject payloadTransferInfo) {
        mPayloadTransferInfo = payloadTransferInfo;
    }
}
