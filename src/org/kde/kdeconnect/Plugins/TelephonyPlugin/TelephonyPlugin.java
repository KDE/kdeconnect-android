/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.TelephonyPlugin;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.apache.commons.lang3.ArrayUtils;
import org.kde.kdeconnect.Helpers.ContactsHelper;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect.UserInterface.PluginSettingsFragment;
import org.kde.kdeconnect_tp.R;

import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

@PluginFactory.LoadablePlugin
public class TelephonyPlugin extends Plugin {


    /**
     * Packet used for simple call events
     * <p>
     * It contains the key "event" which maps to a string indicating the type of event:
     * - "ringing" - A phone call is incoming
     * - "missedCall" - An incoming call was not answered
     * - "sms" - An incoming SMS message
     * - Note: As of this writing (15 May 2018) the SMS interface is being improved and this type of event
     *   is no longer the preferred way of handling SMS. Use the packets defined by the SMS plugin instead.
     * <p>
     * Depending on the event, other fields may be defined
     */
    public final static String PACKET_TYPE_TELEPHONY = "kdeconnect.telephony";

    /**
     * Old-style packet sent to request a simple telephony action
     * <p>
     * The events handled were:
     * - to request the device to mute its ringer
     * - to request an SMS to be sent.
     * <p>
     * In case an SMS was being requested, the body was like so:
     * { "sendSms": true,
     * "phoneNumber": "542904563213",
     * "messageBody": "Hi mom!"
     * }
     * <p>
     * In case a ringer muted was requested, the body looked like so:
     * { "action": "mute" }
     * <p>
     * Ringer mute requests are best handled by PACKET_TYPE_TELEPHONY_REQUEST_MUTE
     * <p>
     * This packet type is retained for backwards-compatibility with old desktop applications,
     * but support should be dropped once those applications are no longer supported. New
     * applications should not use this packet type.
     */
    @Deprecated
    public final static String PACKET_TYPE_TELEPHONY_REQUEST = "kdeconnect.telephony.request";

    /**
     * Packet sent to indicate the user has requested the device mute its ringer
     * <p>
     * The body should be empty
     */
    private final static String PACKET_TYPE_TELEPHONY_REQUEST_MUTE = "kdeconnect.telephony.request_mute";

    private static final String KEY_PREF_BLOCKED_NUMBERS = "telephony_blocked_numbers";
    private int lastState = TelephonyManager.CALL_STATE_IDLE;
    private NetworkPacket lastPacket = null;
    private boolean isMuted = false;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();

            //Log.e("TelephonyPlugin","Telephony event: " + action);

            if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action)) {

                String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                int intState = TelephonyManager.CALL_STATE_IDLE;
                if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
                    intState = TelephonyManager.CALL_STATE_RINGING;
                } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
                    intState = TelephonyManager.CALL_STATE_OFFHOOK;
                }

                // We will get a second broadcast with the phone number https://developer.android.com/reference/android/telephony/TelephonyManager#ACTION_PHONE_STATE_CHANGED
                if (!intent.hasExtra(TelephonyManager.EXTRA_INCOMING_NUMBER))
                    return;
                String number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);

                if (intState != lastState) {
                    lastState = intState;
                    callBroadcastReceived(intState, number);
                }
            }
        }
    };

    @Override
    public @NonNull String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_telephony);
    }

    @Override
    public @NonNull String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_telephony_desc);
    }

    private void callBroadcastReceived(int state, String phoneNumber) {

        if (isNumberBlocked(phoneNumber))
            return;

        NetworkPacket np = new NetworkPacket(PACKET_TYPE_TELEPHONY);

        int permissionCheck = ContextCompat.checkSelfPermission(context,
                Manifest.permission.READ_CONTACTS);

        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {

            Map<String, String> contactInfo = ContactsHelper.phoneNumberLookup(context, phoneNumber);

            if (contactInfo.containsKey("name")) {
                np.set("contactName", contactInfo.get("name"));
            }

            if (contactInfo.containsKey("photoID")) {
                String photoUri = contactInfo.get("photoID");
                if (photoUri != null) {
                    try {
                        String base64photo = ContactsHelper.photoId64Encoded(context, photoUri);
                        if (!TextUtils.isEmpty(base64photo)) {
                            np.set("phoneThumbnail", base64photo);
                        }
                    } catch (Exception e) {
                        Log.e("TelephonyPlugin", "Failed to get contact photo");
                    }
                }

            }

        } else {
            np.set("contactName", phoneNumber);
        }

        if (phoneNumber != null) {
            np.set("phoneNumber", phoneNumber);
        }

        switch (state) {
            case TelephonyManager.CALL_STATE_RINGING:
                unmuteRinger();
                np.set("event", "ringing");
                getDevice().sendPacket(np);
                break;

            case TelephonyManager.CALL_STATE_OFFHOOK: //Ongoing call
                np.set("event", "talking");
                getDevice().sendPacket(np);
                break;

            case TelephonyManager.CALL_STATE_IDLE:
                if (lastPacket != null) {

                    //Resend a cancel of the last event (can either be "ringing" or "talking")
                    lastPacket.set("isCancel", "true");
                    getDevice().sendPacket(lastPacket);

                    if (isMuted) {
                        Timer timer = new Timer();
                        timer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                unmuteRinger();
                            }
                        }, 500);
                    }

                    //Emit a missed call notification if needed
                    if ("ringing".equals(lastPacket.getString("event"))) {
                        np.set("event", "missedCall");
                        np.set("phoneNumber", lastPacket.getStringOrNull("phoneNumber"));
                        np.set("contactName", lastPacket.getStringOrNull("contactName"));
                        getDevice().sendPacket(np);
                    }
                }
                break;
        }

        lastPacket = np;
    }

    private void unmuteRinger() {
        if (isMuted) {
            AudioManager am = ContextCompat.getSystemService(context, AudioManager.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0);
            } else {
                am.setStreamMute(AudioManager.STREAM_RING, false);
            }
            isMuted = false;
        }
    }

    private void muteRinger() {
        if (!isMuted) {
            AudioManager am = ContextCompat.getSystemService(context, AudioManager.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0);
            } else {
                am.setStreamMute(AudioManager.STREAM_RING, true);
            }
            isMuted = true;
        }
    }

    @Override
    protected int getPermissionExplanation() {
        return R.string.telephony_permission_explanation;
    }

    @Override
    protected int getOptionalPermissionExplanation() {
        return R.string.telephony_optional_permission_explanation;
    }

    @Override
    public boolean onCreate() {
        IntentFilter filter = new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        filter.setPriority(500);
        context.registerReceiver(receiver, filter);
        return true;
    }

    @Override
    public void onDestroy() {
        context.unregisterReceiver(receiver);
    }

    @Override
    public boolean onPacketReceived(@NonNull NetworkPacket np) {

        switch (np.getType()) {
            case PACKET_TYPE_TELEPHONY_REQUEST:
                if (np.getString("action").equals("mute")) {
                    muteRinger();
                }
                break;
            case PACKET_TYPE_TELEPHONY_REQUEST_MUTE:
                muteRinger();
                break;
        }
        return true;
    }

    private boolean isNumberBlocked(String number) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        String[] blockedNumbers = sharedPref.getString(KEY_PREF_BLOCKED_NUMBERS, "").split("\n");

        for (String s : blockedNumbers) {
            if (PhoneNumberUtils.compare(number, s))
                return true;
        }

        return false;
    }

    @Override
    public @NonNull String[] getSupportedPacketTypes() {
        return new String[]{
                PACKET_TYPE_TELEPHONY_REQUEST,
                PACKET_TYPE_TELEPHONY_REQUEST_MUTE,
        };
    }

    @Override
    public @NonNull String[] getOutgoingPacketTypes() {
        return new String[]{
                PACKET_TYPE_TELEPHONY
        };
    }

    @Override
    public @NonNull String[] getRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return new String[]{
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_CALL_LOG,
            };
        } else {
            return ArrayUtils.EMPTY_STRING_ARRAY;
        }
    }

    @Override
    public @NonNull String[] getOptionalPermissions() {
        return new String[]{
                Manifest.permission.READ_CONTACTS,
        };
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public PluginSettingsFragment getSettingsFragment(@NonNull Activity activity) {
        return PluginSettingsFragment.newInstance(getPluginKey(), R.xml.telephonyplugin_preferences);
    }
}
