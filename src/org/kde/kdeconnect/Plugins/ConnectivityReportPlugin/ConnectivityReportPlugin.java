/*
 * SPDX-FileCopyrightText: 2021 David Shlemayev <david.shlemayev@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.ConnectivityReportPlugin;

import android.Manifest;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.RequiresApi;

import org.json.JSONException;
import org.json.JSONObject;
import org.kde.kdeconnect.Helpers.SMSHelper;
import org.kde.kdeconnect.Helpers.TelephonyHelper;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect_tp.R;

import java.util.HashMap;
import java.util.Objects;

@PluginFactory.LoadablePlugin
public class ConnectivityReportPlugin extends Plugin {

    /**
     * Packet used to report the current connectivity state
     * <p>
     * The body should contain a key "signalStrengths" which has a dict that maps
     * a SubscriptionID (opaque value) to a dict with the connection info (See below)
     * <p>
     * For example:
     * {
     *     "signalStrengths": {
     *         "6": {
     *             "networkType": "4G",
     *             "signalStrength": 3
     *         },
     *         "17": {
     *             "networkType": "HSPA",
     *             "signalStrength": 2
     *         },
     *         ...
     *     }
     * }
     */
    private final static String PACKET_TYPE_CONNECTIVITY_REPORT = "kdeconnect.connectivity_report";

    /**
     * Packet sent to request the current connectivity state
     * <p>
     * The request packet shall contain no body
     */
    private final static String PACKET_TYPE_CONNECTIVITY_REPORT_REQUEST = "kdeconnect.connectivity_report.request";

    private final NetworkPacket connectivityInfo = new NetworkPacket(PACKET_TYPE_CONNECTIVITY_REPORT);

    OnSubscriptionsChangedListener subListener = null;
    private final HashMap<Integer, PhoneStateListener> listeners = new HashMap<>();
    private final HashMap<Integer, SubscriptionState> states = new HashMap<>();

    @Override
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_connectivity_report);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_connectivity_report_desc);
    }

    private String networkTypeToString(int networkType) {
        switch (networkType) {
            case TelephonyManager.NETWORK_TYPE_NR:
                return "5G";
            case TelephonyManager.NETWORK_TYPE_LTE:
                return "LTE";
            case TelephonyManager.NETWORK_TYPE_CDMA:
            case TelephonyManager.NETWORK_TYPE_TD_SCDMA:
                return "CDMA";
            case TelephonyManager.NETWORK_TYPE_EDGE:
                return "EDGE";
            case TelephonyManager.NETWORK_TYPE_GPRS:
                return "GPRS";
            case TelephonyManager.NETWORK_TYPE_GSM:
                return "GSM";
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
                return "HSPA";
            case TelephonyManager.NETWORK_TYPE_UMTS:
                return "UMTS";
            case TelephonyManager.NETWORK_TYPE_EHRPD:
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
            case TelephonyManager.NETWORK_TYPE_1xRTT:
                return "CDMA2000";
            case TelephonyManager.NETWORK_TYPE_IDEN:
                return "iDEN";
            case TelephonyManager.NETWORK_TYPE_IWLAN:
            case TelephonyManager.NETWORK_TYPE_UNKNOWN:
            default:
                return "Unknown";
        }
    }

    private void serializeSignalStrengths() {
        JSONObject signalStrengths = new JSONObject();
        for (Integer subID : states.keySet()) {
            try {
                JSONObject subInfo = new JSONObject();
                SubscriptionState subscriptionState = Objects.requireNonNull(states.get(subID));
                subInfo.put("networkType", subscriptionState.networkType);
                subInfo.put("signalStrength", subscriptionState.signalStrength);

                signalStrengths.put(subID.toString(), subInfo);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        connectivityInfo.set("signalStrengths", signalStrengths);
    }

    private void runWithLooper(Runnable r) {
        // We use the MessageLooper to avoid creating an extra thread for this
        new Handler(Objects.requireNonNull(SMSHelper.MessageLooper.getLooper())).post(r);
    }

    private PhoneStateListener createListener(Integer subID) {
        return new PhoneStateListener() {
            @Override
            public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                int level = ASUUtils.signalStrengthToLevel(signalStrength);
                SubscriptionState state = states.get(subID);

                if (state != null) {
                    state.signalStrength = level;
                }

                serializeSignalStrengths();
                device.sendPacket(connectivityInfo);
                Log.i("ConnectivityReport", "signalStrength of #" + subID + " updated to " + level);
            }

            @Override
            public void onDataConnectionStateChanged(int _state, int networkType) {
                SubscriptionState state = states.get(subID);

                if (state != null) {
                    state.networkType = networkTypeToString(networkType);
                }

                serializeSignalStrengths();
                device.sendPacket(connectivityInfo);
                Log.i("ConnectivityReport", "networkType of #" + subID + " updated to " + networkTypeToString(networkType));
            }
        };
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void subscriptionsListen() {
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        subListener = TelephonyHelper.listenActiveSubscriptionIDs(
                context,
                subID -> {
                    TelephonyManager subTm = tm.createForSubscriptionId(subID);
                    Log.i("ConnectivityReport", "Added subscription ID " + subID);

                    states.put(subID, new SubscriptionState(subID));
                    PhoneStateListener listener = createListener(subID);
                    listeners.put(subID, listener);
                    subTm.listen(
                            listener,
                            PhoneStateListener.LISTEN_SIGNAL_STRENGTHS | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
                    );
                },
                subID -> {
                    Log.i("ConnectivityReport", "Removed subscription ID " + subID);
                    tm.listen(listeners.get(subID), PhoneStateListener.LISTEN_NONE);
                    listeners.remove(subID);
                    states.remove(subID);
                }
        );
    }

    @Override
    public boolean onCreate() {
        serializeSignalStrengths();

        runWithLooper(() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Multi-SIM supported on Nougat+
                subscriptionsListen();
            } else {
                // Fallback to single SIM
                listeners.put(0, createListener(0));
                states.put(0, new SubscriptionState(0));
            }
        });

        return true;
    }

    @Override
    public void onDestroy() {
        runWithLooper(() -> {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                TelephonyHelper.cancelActiveSubscriptionIDsListener(context, subListener);
            }
            for (Integer subID : listeners.keySet()) {
                Log.i("ConnectivityReport", "Removed subscription ID " + subID);
                tm.listen(listeners.get(subID), PhoneStateListener.LISTEN_NONE);
            }
        });
    }

    @Override
    public boolean onPacketReceived(NetworkPacket np) {
        if (PACKET_TYPE_CONNECTIVITY_REPORT_REQUEST.equals(np.getType())) {
            Log.i("ConnectivityReport", "Requested");
            serializeSignalStrengths();
            device.sendPacket(connectivityInfo);
        }

        return true;
    }

    @Override
    public String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_CONNECTIVITY_REPORT_REQUEST};
    }

    @Override
    public String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_CONNECTIVITY_REPORT};
    }

    @Override
    public String[] getRequiredPermissions() {
        return new String[]{
                Manifest.permission.READ_PHONE_STATE,
        };
    }

}
