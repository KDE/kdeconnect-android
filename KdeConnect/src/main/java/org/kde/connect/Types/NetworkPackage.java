package org.kde.connect.Types;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.TimeZone;

public class NetworkPackage {

    public enum Type {
        UNKNOWN,
        IDENTIFY,
        PAIR_REQUEST,
        RING,
        MISSED,
        SMS,
        BATTERY,
        NOTIFY,
        PING,
        TYPE_SIZE
    }

    private long mId;
    private String mDeviceId;
    private long mTime; //since epoch, utc
    private Type mType;
    private String mBody;
    private boolean mIsCancel;
    private JSONObject mExtras; //JSON

    public NetworkPackage(Context context) {
        final TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        mDeviceId = tm.getDeviceId();
        mTime = Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTimeInMillis() / 1000L;
        mId = System.currentTimeMillis();
        mType = Type.UNKNOWN;
        mBody = "";
        mIsCancel = false;
        mExtras = new JSONObject();
    }

    public void setType(Type type) {
        mType = type;
    }

    public void setBody(String body) {
        mBody = body;
    }

    public String getBody() {
        return mBody;
    }

    public Type getType() {
        return mType;
    }

    public void updateTime() {
        mTime = Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTimeInMillis() / 1000L;
    }

    public void cancel() {
        mIsCancel = true;
    }

    public String toString() {

        StringBuilder sb = new StringBuilder();

        sb.append(mId);

        sb.append(' ');

        sb.append(mDeviceId.length());
        sb.append(' ');
        sb.append(mDeviceId);

        sb.append(' ');

        sb.append(mTime);

        sb.append(' ');

        sb.append(mType.toString());

        sb.append(' ');

        sb.append(mBody.length());
        sb.append(' ');
        sb.append(mBody);

        sb.append(' ');

        sb.append(mIsCancel?'1':'0');

        sb.append(' ');

        sb.append(mExtras.length());
        sb.append(' ');
        sb.append(mExtras);

        sb.append("END");

        return sb.toString();

    }


    static public NetworkPackage fromString(String s, Context context) {
        Log.i("NetworkPackage.fromString",s);

        //TODO: Implement
        NetworkPackage np = new NetworkPackage(context);
        np.mType = Type.PAIR_REQUEST;
        return np;
    }


}
