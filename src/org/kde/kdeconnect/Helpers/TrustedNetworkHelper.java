package org.kde.kdeconnect.Helpers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import android.content.Context;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

public class TrustedNetworkHelper {

    private static final String KEY_CUSTOM_TRUSTED_NETWORKS = "trusted_network_preference";
    private static final String KEY_CUSTOM_TRUST_ALL_NETWORKS = "trust_all_network_preference";
    private static final String NETWORK_SSID_DELIMITER = "#_#";
    private static final String NOT_AVAILABLE_SSID_RESULT = "<unknown ssid>";


    private final Context context;

    public TrustedNetworkHelper(Context context) {
        this.context = context;
    }

    public List<String> read() {
        String serializeTrustedNetwork = PreferenceManager.getDefaultSharedPreferences(context).getString(
                KEY_CUSTOM_TRUSTED_NETWORKS, "");
        if (serializeTrustedNetwork.isEmpty())
            return Collections.emptyList();
        return Arrays.asList(serializeTrustedNetwork.split(NETWORK_SSID_DELIMITER));
    }

    public void update(List<String> trustedNetworks) {
        String serialized = TextUtils.join(NETWORK_SSID_DELIMITER, trustedNetworks);
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(
                KEY_CUSTOM_TRUSTED_NETWORKS, serialized).apply();
    }

    public Boolean allAllowed() {
        return PreferenceManager
                .getDefaultSharedPreferences(context)
                .getBoolean(KEY_CUSTOM_TRUST_ALL_NETWORKS, Boolean.TRUE);
    }

    public void allAllowed(boolean isChecked) {
        PreferenceManager
                .getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(KEY_CUSTOM_TRUST_ALL_NETWORKS, isChecked)
                .apply();
    }

    public String currentSSID() {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        Log.d("Fou", "get");
        if (wifiManager == null) return "";
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo.getSupplicantState() != SupplicantState.COMPLETED) {
            Log.d("Fou", "fooo");
            return "";
        }
        String ssid = wifiInfo.getSSID();
        if (ssid.equalsIgnoreCase(NOT_AVAILABLE_SSID_RESULT)){
            Log.d("Fou", "navail");
            return "";
        }
        Log.d("Fou", "retn");
        return ssid;
    }

    public static boolean isNotTrustedNetwork(Context context) {
        TrustedNetworkHelper trustedNetworkHelper = new TrustedNetworkHelper(context);
        if (trustedNetworkHelper.allAllowed()){
            return false;
        }
        return trustedNetworkHelper.read().indexOf(trustedNetworkHelper.currentSSID()) == -1;
    }
}
