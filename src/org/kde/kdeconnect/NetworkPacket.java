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
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NetworkPacket {

    public final static int ProtocolVersion = 7;

    public final static String PACKET_TYPE_IDENTITY = "kdeconnect.identity";
    public final static String PACKET_TYPE_PAIR = "kdeconnect.pair";

    public final static int PACKET_REPLACEID_MOUSEMOVE = 0;
    public final static int PACKET_REPLACEID_PRESENTERPOINTER = 1;

    public static Set<String> protocolPacketTypes = new HashSet<String>() {{
        add(PACKET_TYPE_IDENTITY);
        add(PACKET_TYPE_PAIR);
    }};

    private long mId;
    String mType;
    private JSONObject mBody;
    private Payload mPayload;
    private JSONObject mPayloadTransferInfo;
    private volatile boolean canceled;

    private NetworkPacket() {

    }

    public NetworkPacket(String type) {
        mId = System.currentTimeMillis();
        mType = type;
        mBody = new JSONObject();
        mPayload = null;
        mPayloadTransferInfo = new JSONObject();
    }

    public boolean isCanceled() { return canceled; }
    public void cancel() { canceled = true; }

    public String getType() {
        return mType;
    }

    public long getId() {
        return mId;
    }

    //Most commons getters and setters defined for convenience
    public String getString(String key) {
        return mBody.optString(key, "");
    }

    public String getString(String key, String defaultValue) {
        return mBody.optString(key, defaultValue);
    }

    public void set(String key, String value) {
        if (value == null) return;
        try {
            mBody.put(key, value);
        } catch (Exception ignored) {
        }
    }

    public int getInt(String key) {
        return mBody.optInt(key, -1);
    }

    public int getInt(String key, int defaultValue) {
        return mBody.optInt(key, defaultValue);
    }

    public void set(String key, int value) {
        try {
            mBody.put(key, value);
        } catch (Exception ignored) {
        }
    }

    public long getLong(String key) {
        return mBody.optLong(key, -1);
    }

    public long getLong(String key, long defaultValue) {
        return mBody.optLong(key, defaultValue);
    }

    public void set(String key, long value) {
        try {
            mBody.put(key, value);
        } catch (Exception ignored) {
        }
    }

    public boolean getBoolean(String key) {
        return mBody.optBoolean(key, false);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return mBody.optBoolean(key, defaultValue);
    }

    public void set(String key, boolean value) {
        try {
            mBody.put(key, value);
        } catch (Exception ignored) {
        }
    }

    public double getDouble(String key) {
        return mBody.optDouble(key, Double.NaN);
    }

    public double getDouble(String key, double defaultValue) {
        return mBody.optDouble(key, defaultValue);
    }

    public void set(String key, double value) {
        try {
            mBody.put(key, value);
        } catch (Exception ignored) {
        }
    }

    public JSONArray getJSONArray(String key) {
        return mBody.optJSONArray(key);
    }

    public void set(String key, JSONArray value) {
        try {
            mBody.put(key, value);
        } catch (Exception ignored) {
        }
    }

    public JSONObject getJSONObject(String key) {
        return mBody.optJSONObject(key);
    }

    public void set(String key, JSONObject value) {
        try {
            mBody.put(key, value);
        } catch (JSONException ignored) {
        }
    }

    private Set<String> getStringSet(String key) {
        JSONArray jsonArray = mBody.optJSONArray(key);
        if (jsonArray == null) return null;
        Set<String> list = new HashSet<>();
        int length = jsonArray.length();
        for (int i = 0; i < length; i++) {
            try {
                String str = jsonArray.getString(i);
                list.add(str);
            } catch (Exception ignored) {
            }
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
            for (String str : value) {
                jsonArray.put(str);
            }
            mBody.put(key, jsonArray);
        } catch (Exception ignored) {
        }
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
            } catch (Exception ignored) {
            }
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
            for (String str : value) {
                jsonArray.put(str);
            }
            mBody.put(key, jsonArray);
        } catch (Exception ignored) {
        }
    }

    public boolean has(String key) {
        return mBody.has(key);
    }

    public String serialize() throws JSONException {
        JSONObject jo = new JSONObject();
        jo.put("id", mId);
        jo.put("type", mType);
        jo.put("body", mBody);
        if (hasPayload()) {
            jo.put("payloadSize", mPayload.payloadSize);
            jo.put("payloadTransferInfo", mPayloadTransferInfo);
        }
        //QJSon does not escape slashes, but Java JSONObject does. Converting to QJson format.
        return jo.toString().replace("\\/", "/") + "\n";
    }

    static public NetworkPacket unserialize(String s) throws JSONException {

        NetworkPacket np = new NetworkPacket();
        JSONObject jo = new JSONObject(s);
        np.mId = jo.getLong("id");
        np.mType = jo.getString("type");
        np.mBody = jo.getJSONObject("body");
        if (jo.has("payloadSize")) {
            np.mPayloadTransferInfo = jo.getJSONObject("payloadTransferInfo");
            np.mPayload = new Payload(jo.getLong("payloadSize"));
        } else {
            np.mPayloadTransferInfo = new JSONObject();
            np.mPayload = new Payload(0);
        }
        return np;
    }

    static public NetworkPacket createIdentityPacket(Context context) {

        NetworkPacket np = new NetworkPacket(NetworkPacket.PACKET_TYPE_IDENTITY);

        String deviceId = DeviceHelper.getDeviceId(context);
        try {
            np.mBody.put("deviceId", deviceId);
            np.mBody.put("deviceName", DeviceHelper.getDeviceName(context));
            np.mBody.put("protocolVersion", NetworkPacket.ProtocolVersion);
            np.mBody.put("deviceType", DeviceHelper.getDeviceType(context).toString());
            np.mBody.put("incomingCapabilities", new JSONArray(PluginFactory.getIncomingCapabilities()));
            np.mBody.put("outgoingCapabilities", new JSONArray(PluginFactory.getOutgoingCapabilities()));
        } catch (Exception e) {
            Log.e("NetworkPackage", "Exception on createIdentityPacket", e);
        }

        return np;

    }

    public void setPayload(Payload payload) { mPayload = payload; }

    public Payload getPayload() {
        return mPayload;
    }

    public long getPayloadSize() {
        return mPayload == null ? 0 : mPayload.payloadSize;
    }

    public boolean hasPayload() {
        return (mPayload != null && mPayload.payloadSize != 0);
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

    public static class Payload {
        private InputStream inputStream;
        private Socket inputSocket;
        private long payloadSize;

        public Payload(long payloadSize) {
            this((InputStream)null, payloadSize);
        }

        public Payload(byte[] data) {
            this(new ByteArrayInputStream(data), data.length);
        }

        /**
         * <b>NOTE: Do not use this to set an SSLSockets InputStream as the payload, use Payload(Socket, long) instead because of this <a href="https://issuetracker.google.com/issues/37018094">bug</a></b>
         */
        public Payload(InputStream inputStream, long payloadSize) {
            this.inputSocket = null;
            this.inputStream = inputStream;
            this.payloadSize = payloadSize;
        }

        public Payload(Socket inputSocket, long payloadSize) throws IOException {
            this.inputSocket = inputSocket;
            this.inputStream = inputSocket.getInputStream();
            this.payloadSize = payloadSize;
        }

        /**
         * <b>NOTE: Do not close the InputStream directly call Payload.close() instead, this is because of this <a href="https://issuetracker.google.com/issues/37018094">bug</a></b>
         */
        public InputStream getInputStream() { return inputStream; }
        long getPayloadSize() { return payloadSize; }

        public void close() {
            //TODO: If socket only close socket if that also closes the streams that is
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch(IOException ignored) {}

            try {
                if (inputSocket != null) {
                    inputSocket.close();
                }
            } catch (IOException ignored) {}
        }
    }
}
