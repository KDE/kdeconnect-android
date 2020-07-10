package org.kde.kdeconnect.Helpers;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import org.apache.commons.lang3.ArrayUtils;

import java.util.List;

public class TrustedNetworkHelper {

    private static final String KEY_CUSTOM_TRUSTED_NETWORKS = "trusted_network_preference";
    private static final String KEY_CUSTOM_TRUST_ALL_NETWORKS = "trust_all_network_preference";
    private static final String NETWORK_SSID_DELIMITER = "#_#";
    private static final String NOT_AVAILABLE_SSID_RESULT = "<unknown ssid>";


    private final Context context;

    public TrustedNetworkHelper(Context context) {
        this.context = context;
    }

    public String[] read() {
        String serializeTrustedNetwork = PreferenceManager.getDefaultSharedPreferences(context).getString(
                KEY_CUSTOM_TRUSTED_NETWORKS, "");
        if (serializeTrustedNetwork.isEmpty())
            return ArrayUtils.EMPTY_STRING_ARRAY;
        return serializeTrustedNetwork.split(NETWORK_SSID_DELIMITER);
    }

    public void update(List<String> trustedNetworks) {
        String serialized = TextUtils.join(NETWORK_SSID_DELIMITER, trustedNetworks);
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(
                KEY_CUSTOM_TRUSTED_NETWORKS, serialized).apply();
    }

    public boolean allAllowed() {
        if (!hasPermissions()) {
            return true;
        }
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

    public boolean hasPermissions() {
        int result = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION);
        return (result == PackageManager.PERMISSION_GRANTED);
    }

    public String currentSSID() {
        WifiManager wifiManager = ContextCompat.getSystemService(context.getApplicationContext(),
                WifiManager.class);
        if (wifiManager == null) return "";
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo.getSupplicantState() != SupplicantState.COMPLETED) {
            return "";
        }
        String ssid = wifiInfo.getSSID();
        if (ssid.equalsIgnoreCase(NOT_AVAILABLE_SSID_RESULT)){
            return "";
        }
        return ssid;
    }

    public static boolean isTrustedNetwork(Context context) {
        TrustedNetworkHelper trustedNetworkHelper = new TrustedNetworkHelper(context);
        if (trustedNetworkHelper.allAllowed()){
            return true;
        }
        return ArrayUtils.contains(trustedNetworkHelper.read(), trustedNetworkHelper.currentSSID());
    }
}
