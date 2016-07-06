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
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.kde.kdeconnect.Helpers.DeviceHelper;
import org.kde.kdeconnect.Plugins.PluginFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NetworkPackage {

    public final static int ProtocolVersion = 7;

    public final static String PACKAGE_TYPE_IDENTITY = "kdeconnect.identity";
    public final static String PACKAGE_TYPE_PAIR = "kdeconnect.pair";
    public final static String PACKAGE_TYPE_ENCRYPTED = "kdeconnect.encrypted";

    public static Set<String> protocolPackageTypes = new HashSet<String>() {{
        add(PACKAGE_TYPE_IDENTITY);
        add(PACKAGE_TYPE_PAIR);
        add(PACKAGE_TYPE_ENCRYPTED);
    }};

    private long mId;
    String mType;
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

    public Set<String> getStringSet(String key) {
        JSONArray jsonArray = mBody.optJSONArray(key);
        if (jsonArray == null) return null;
        Set<String> list = new HashSet<>();
        int length = jsonArray.length();
        for (int i = 0; i < length; i++) {
            try {
                String str = jsonArray.getString(i);
                list.add(str);
            } catch(Exception e) { }
        }
        return list;
    }
    public Set<String> getStringSet(String key, Set<String> defaultValue) {
        if (mBody.has(key)) return getStringSet(key);
        else return defaultValue;
    }
    public void set(String key, Set<String> value) {
        try {
            JSONArray jsonArray = new JSONArray();
            for(String str : value) {
                jsonArray.put(str);
            }
            mBody.put(key,jsonArray);
        } catch(Exception e) { }
    }

    public List<String> getStringList(String key) {
        JSONArray jsonArray = mBody.optJSONArray(key);
        if (jsonArray == null) return null;
        List<String> list = new ArrayList<>();
        int length = jsonArray.length();
        for (int i = 0; i < length; i++) {
            try {
                String str = jsonArray.getString(i);
                list.add(str);
            } catch(Exception e) { }
        }
        return list;
    }
    public List<String> getStringList(String key, List<String> defaultValue) {
        if (mBody.has(key)) return getStringList(key);
        else return defaultValue;
    }
    public void set(String key, List<String> value) {
        try {
            JSONArray jsonArray = new JSONArray();
            for(String str : value) {
                jsonArray.put(str);
            }
            mBody.put(key,jsonArray);
        } catch(Exception e) { }
    }
    public boolean has(String key) { return mBody.has(key); }

    public String serialize() throws JSONException {
        JSONObject jo = new JSONObject();
        jo.put("id", mId);
        jo.put("type", mType);
        jo.put("body", mBody);
        if (hasPayload()) {
            jo.put("payloadSize", mPayloadSize);
            jo.put("payloadTransferInfo", mPayloadTransferInfo);
        }
        //QJSon does not escape slashes, but Java JSONObject does. Converting to QJson format.
        String json = jo.toString().replace("\\/","/")+"\n";
        return json;
    }

    static public NetworkPackage unserialize(String s) throws JSONException {

        NetworkPackage np = new NetworkPackage();
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
        return np;
    }

    static public NetworkPackage createIdentityPackage(Context context) {

        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_IDENTITY);

        String deviceId = DeviceHelper.getDeviceId(context);
        try {
            np.mBody.put("deviceId", deviceId);
            np.mBody.put("deviceName", DeviceHelper.getDeviceName(context));
            np.mBody.put("protocolVersion", NetworkPackage.ProtocolVersion);
            np.mBody.put("deviceType", DeviceHelper.isTablet()? "tablet" : "phone");
            np.mBody.put("incomingCapabilities", new JSONArray(PluginFactory.getIncomingCapabilities(context)));
            np.mBody.put("outgoingCapabilities", new JSONArray(PluginFactory.getOutgoingCapabilities(context)));
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("NetworkPacakge","Exception on createIdentityPackage");
        }

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
