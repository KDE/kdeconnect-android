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

package org.kde.kdeconnect.Plugins.TelephonyPlugin;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.kde.kdeconnect.Helpers.ContactsHelper;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect_tp.BuildConfig;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class TelephonyPlugin extends Plugin {

    private final static String PACKET_TYPE_TELEPHONY = "kdeconnect.telephony";
    public final static String PACKET_TYPE_TELEPHONY_REQUEST = "kdeconnect.telephony.request";
    private static final String KEY_PREF_BLOCKED_NUMBERS = "telephony_blocked_numbers";

    private int lastState = TelephonyManager.CALL_STATE_IDLE;
    private NetworkPacket lastPacket = null;
    private boolean isMuted = false;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();

            //Log.e("TelephonyPlugin","Telephony event: " + action);

            if ("android.provider.Telephony.SMS_RECEIVED".equals(action)) {

                final Bundle bundle = intent.getExtras();
                if (bundle == null) return;
                final Object[] pdus = (Object[]) bundle.get("pdus");
                ArrayList<SmsMessage> messages = new ArrayList<>();

                for (Object pdu : pdus) {
                    // I hope, but am not sure, that the pdus array is in the order that the parts
                    // of the SMS message should be
                    // If it is not, I believe the pdu contains the information necessary to put it
                    // in order, but in my testing the order seems to be correct, so I won't worry
                    // about it now.
                    messages.add(SmsMessage.createFromPdu((byte[]) pdu));
                }

                smsBroadcastReceived(messages);

            } else if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action)) {

                String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                int intState = TelephonyManager.CALL_STATE_IDLE;
                if (state.equals(TelephonyManager.EXTRA_STATE_RINGING))
                    intState = TelephonyManager.CALL_STATE_RINGING;
                else if (state.equals(TelephonyManager.EXTRA_STATE_OFFHOOK))
                    intState = TelephonyManager.CALL_STATE_OFFHOOK;

                String number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
                if (number == null)
                    number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);

                final int finalIntState = intState;
                final String finalNumber = number;

                callBroadcastReceived(finalIntState, finalNumber);

            }
        }
    };

    @Override
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_telephony);
    }

    @Override
    public String getDescription() {
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
                        if (base64photo != null && !base64photo.isEmpty()) {
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
                if (isMuted) {
                    AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        am.setStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0);
                    } else {
                        am.setStreamMute(AudioManager.STREAM_RING, false);
                    }
                    isMuted = false;
                }
                np.set("event", "ringing");
                device.sendPacket(np);
                break;

            case TelephonyManager.CALL_STATE_OFFHOOK: //Ongoing call
                np.set("event", "talking");
                device.sendPacket(np);
                break;

            case TelephonyManager.CALL_STATE_IDLE:

                if (lastState != TelephonyManager.CALL_STATE_IDLE && lastPacket != null) {

                    //Resend a cancel of the last event (can either be "ringing" or "talking")
                    lastPacket.set("isCancel", "true");
                    device.sendPacket(lastPacket);

                    if (isMuted) {
                        Timer timer = new Timer();
                        timer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                if (isMuted) {
                                    AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        am.setStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0);
                                    } else {
                                        am.setStreamMute(AudioManager.STREAM_RING, false);
                                    }
                                    isMuted = false;
                                }
                            }
                        }, 500);
                    }

                    //Emit a missed call notification if needed
                    if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                        np.set("event", "missedCall");
                        np.set("phoneNumber", lastPacket.getString("phoneNumber", null));
                        np.set("contactName", lastPacket.getString("contactName", null));
                        device.sendPacket(np);
                    }

                }

                break;

        }

        lastPacket = np;
        lastState = state;
    }

    private void smsBroadcastReceived(ArrayList<SmsMessage> messages) {

        if (BuildConfig.DEBUG) {
            if (!(messages.size() > 0)) {
                throw new AssertionError("This method requires at least one message");
            }
        }

        NetworkPacket np = new NetworkPacket(PACKET_TYPE_TELEPHONY);

        np.set("event", "sms");

        StringBuilder messageBody = new StringBuilder();
        for (int index = 0; index < messages.size(); index++) {
            messageBody.append(messages.get(index).getMessageBody());
        }
        np.set("messageBody", messageBody.toString());

        String phoneNumber = messages.get(0).getOriginatingAddress();

        if (isNumberBlocked(phoneNumber))
            return;

        int permissionCheck = ContextCompat.checkSelfPermission(context,
                Manifest.permission.READ_CONTACTS);

        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            Map<String, String> contactInfo = ContactsHelper.phoneNumberLookup(context, phoneNumber);

            if (contactInfo.containsKey("name")) {
                np.set("contactName", contactInfo.get("name"));
            }

            if (contactInfo.containsKey("photoID")) {
                np.set("phoneThumbnail", ContactsHelper.photoId64Encoded(context, contactInfo.get("photoID")));
            }
        }
        if (phoneNumber != null) {
            np.set("phoneNumber", phoneNumber);
        }


        device.sendPacket(np);
    }

    @Override
    public boolean onCreate() {
        IntentFilter filter = new IntentFilter("android.provider.Telephony.SMS_RECEIVED");
        filter.setPriority(500);
        filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        context.registerReceiver(receiver, filter);
        permissionExplanation = R.string.telephony_permission_explanation;
        optionalPermissionExplanation = R.string.telephony_optional_permission_explanation;
        return true;
    }

    @Override
    public void onDestroy() {
        context.unregisterReceiver(receiver);
    }

    @Override
    public boolean onPacketReceived(NetworkPacket np) {
        if (np.getString("action").equals("mute")) {
            if (!isMuted) {
                AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0);
                } else {
                    am.setStreamMute(AudioManager.STREAM_RING, true);
                }
                isMuted = true;
            }
        }
        //Do nothing
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
    public String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_TELEPHONY_REQUEST};
    }

    @Override
    public String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_TELEPHONY};
    }

    @Override
    public String[] getRequiredPermissions() {
        return new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_SMS};
    }

    @Override
    public String[] getOptionalPermissions() {
        return new String[]{Manifest.permission.READ_CONTACTS};
    }

    @Override
    public boolean hasSettings() {
        return true;
    }
}
