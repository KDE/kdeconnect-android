package org.kde.connect;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.Charset;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;

public class NetworkPackage {

    public final static int ProtocolVersion = 3;

    public final static String PACKAGE_TYPE_IDENTITY = "kdeconnect.identity";
    public final static String PACKAGE_TYPE_PAIR = "kdeconnect.pair";
    public final static String PACKAGE_TYPE_ENCRYPTED = "kdeconnect.encrypted";
    public final static String PACKAGE_TYPE_PING = "kdeconnect.ping";
    public final static String PACKAGE_TYPE_TELEPHONY = "kdeconnect.telephony";
    public final static String PACKAGE_TYPE_BATTERY = "kdeconnect.battery";
    public final static String PACKAGE_TYPE_NOTIFICATION = "kdeconnect.notification";
    public final static String PACKAGE_TYPE_CLIPBOARD = "kdeconnect.clipboard";
    public final static String PACKAGE_TYPE_MPRIS = "kdeconnect.mpris";

    private long mId;
    private String mType;
    private JSONObject mBody;

    private NetworkPackage() {
    }

    public NetworkPackage(String type) {
        mId = System.currentTimeMillis();
        mType = type;
        mBody = new JSONObject();
    }

    public String getType() {
        return mType;
    }

    //Most commons getters and setters defined for convenience
    public String getString(String key) { return mBody.optString(key,""); }
    public String getString(String key, String defaultValue) { return mBody.optString(key,defaultValue); }
    public void set(String key, String value) { if (value == null) return; try { mBody.put(key,value); } catch(Exception e) { } }
    public int getInt(String key) { return mBody.optInt(key,-1); }
    public int getInt(String key, int defaultValue) { return mBody.optInt(key,defaultValue); }
    public void set(String key, int value) { try { mBody.put(key,value); } catch(Exception e) { } }
    public boolean getBoolean(String key) { return mBody.optBoolean(key,false); }
    public boolean getBoolean(String key, boolean defaultValue) { return mBody.optBoolean(key,defaultValue); }
    public void set(String key, boolean value) { try { mBody.put(key,value); } catch(Exception e) { } }
    public double getDouble(String key) { return mBody.optDouble(key,Double.NaN); }
    public double getDouble(String key, double defaultValue) { return mBody.optDouble(key,defaultValue); }
    public void set(String key, double value) { try { mBody.put(key,value); } catch(Exception e) { } }
    public ArrayList<String> getStringList(String key) {
        JSONArray jsonArray = mBody.optJSONArray(key);
        ArrayList<String> list = new ArrayList<String>();
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

    public String serialize() {
        JSONObject jo = new JSONObject();
        try {
            jo.put("id",mId);
            jo.put("type",mType);
            jo.put("body",mBody);
        } catch(Exception e) {
        }
        //QJSon does not escape slashes, but Java JSONObject does. Converting to QJson format.
        String json = jo.toString().replace("\\/","/")+"\n";
        Log.e("NetworkPackage.serialize",json);
        return json;
    }

    static public NetworkPackage unserialize(String s) {
        Log.e("NetworkPackage.unserialize", s);
        NetworkPackage np = new NetworkPackage();
        try {
            JSONObject jo = new JSONObject(s);
            np.mId = jo.getLong("id");
            np.mType = jo.getString("type");
            np.mBody = jo.getJSONObject("body");
        } catch (Exception e) {
            return null;
        }
        return np;
    }



    public void encrypt(PublicKey publicKey) throws Exception {

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

        mId = System.currentTimeMillis();
        mType = NetworkPackage.PACKAGE_TYPE_ENCRYPTED;
        mBody = new JSONObject();
        try {
            mBody.put("data", chunks);
        }catch(Exception e){
            e.printStackTrace();
            Log.e("NetworkPackage","Exception");
        }

        Log.e("NetworkPackage", "Encrypted " + chunks.length()+" chunks");

    }

    public NetworkPackage decrypt(PrivateKey privateKey) throws Exception {

        JSONArray chunks = mBody.getJSONArray("data");

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1PADDING");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);

        String decryptedJson = "";
        for (int i = 0; i < chunks.length(); i++) {
            byte[] encryptedChunk = Base64.decode(chunks.getString(i), Base64.NO_WRAP);
            String decryptedChunk = new String(cipher.doFinal(encryptedChunk));
            decryptedJson += decryptedChunk;
        }

        return unserialize(decryptedJson);
    }


    static public NetworkPackage createIdentityPackage(Context context) {

        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_IDENTITY);

        String deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        try {
            np.mBody.put("deviceId", deviceId);
            np.mBody.put("deviceName", HumanDeviceNames.getDeviceName());
            np.mBody.put("protocolVersion", NetworkPackage.ProtocolVersion);
        } catch (JSONException e) {
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

}
