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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.kde.kdeconnect.Helpers.ContactsHelper;
import org.kde.kdeconnect.NetworkPackage;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect_tp.R;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class TelephonyPlugin extends Plugin {

    public final static String PACKAGE_TYPE_TELEPHONY = "kdeconnect.telephony";
    public final static String PACKAGE_TYPE_TELEPHONY_REQUEST = "kdeconnect.telephony.request";

    private int lastState = TelephonyManager.CALL_STATE_IDLE;
    private NetworkPackage lastPackage = null;
    private boolean isMuted = false;

    @Override
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_telephony);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_telephony_desc);
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();

            //Log.e("TelephonyPlugin","Telephony event: " + action);

            if("android.provider.Telephony.SMS_RECEIVED".equals(action)) {

                final Bundle bundle = intent.getExtras();
                if (bundle == null) return;
                final Object[] pdus = (Object[]) bundle.get("pdus");
                for (Object pdu : pdus) {
                    SmsMessage message = SmsMessage.createFromPdu((byte[])pdu);
                    smsBroadcastReceived(message);
                }

            } else if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action)) {

                String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                int intState =  TelephonyManager.CALL_STATE_IDLE;
                if (state.equals(TelephonyManager.EXTRA_STATE_RINGING))
                    intState = TelephonyManager.CALL_STATE_RINGING;
                else if (state.equals(TelephonyManager.EXTRA_STATE_OFFHOOK))
                    intState = TelephonyManager.CALL_STATE_OFFHOOK;

                String number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
                if (number == null) number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);

                final int finalIntState = intState;
                final String finalNumber = number;

                callBroadcastReceived(finalIntState, finalNumber);

            }
        }
    };

    private void callBroadcastReceived(int state, String phoneNumber) {

        //Log.e("TelephonyPlugin", "callBroadcastReceived");

        Map<String, String> contactInfo = ContactsHelper.phoneNumberLookup(context, phoneNumber);
        NetworkPackage np = new NetworkPackage(PACKAGE_TYPE_TELEPHONY);

        if (phoneNumber != null) {
            np.set("phoneNumber", phoneNumber);
        }

        if (contactInfo.containsKey("name")) {
            np.set("contactName", contactInfo.get("name"));
        } else {
            np.set("contactName",  phoneNumber);
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
                device.sendPackage(np);
                break;

            case TelephonyManager.CALL_STATE_OFFHOOK: //Ongoing call
                np.set("event", "talking");
                device.sendPackage(np);
                break;

            case TelephonyManager.CALL_STATE_IDLE:

                if (lastState != TelephonyManager.CALL_STATE_IDLE && lastPackage != null) {

                    //Resend a cancel of the last event (can either be "ringing" or "talking")
                    lastPackage.set("isCancel","true");
                    device.sendPackage(lastPackage);

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
                        np.set("event","missedCall");
                        np.set("phoneNumber", lastPackage.getString("phoneNumber",null));
                        np.set("contactName", lastPackage.getString("contactName",null));
                        device.sendPackage(np);
                    }

                }

                break;

        }

        lastPackage = np;
        lastState = state;
    }

    private void smsBroadcastReceived(SmsMessage message) {

        //Log.e("SmsBroadcastReceived", message.toString());

        NetworkPackage np = new NetworkPackage(PACKAGE_TYPE_TELEPHONY);

        np.set("event","sms");

        String messageBody = message.getMessageBody();
        if (messageBody != null) {
            np.set("messageBody",messageBody);
        }

        String phoneNumber = message.getOriginatingAddress();
        Map<String, String> contactInfo = ContactsHelper.phoneNumberLookup(context, phoneNumber);
        if (phoneNumber != null) {
            np.set("phoneNumber", phoneNumber);
        }

        if (contactInfo.containsKey("name")) {
            np.set("contactName", contactInfo.get("name"));
        }

        if (contactInfo.containsKey("photoID")) {
            np.set("phoneThumbnail", ContactsHelper.photoId64Encoded(context, contactInfo.get("photoID")));
        }

        device.sendPackage(np);
    }

    @Override
    public boolean onCreate() {
        //Log.e("TelephonyPlugin", "onCreate");
        IntentFilter filter = new IntentFilter("android.provider.Telephony.SMS_RECEIVED");
        filter.setPriority(500);
        filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        context.registerReceiver(receiver, filter);
        return true;
    }

    @Override
    public void onDestroy() {
        context.unregisterReceiver(receiver);
    }

    @Override
    public boolean onPackageReceived(NetworkPackage np) {
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
            //Log.e("TelephonyPlugin", "mute");
        }
        //Do nothing
        return true;
    }

    @Override
    public String[] getSupportedPackageTypes() {
        return new String[]{PACKAGE_TYPE_TELEPHONY_REQUEST};
    }

    @Override
    public String[] getOutgoingPackageTypes() {
        return new String[]{PACKAGE_TYPE_TELEPHONY};
    }

}
