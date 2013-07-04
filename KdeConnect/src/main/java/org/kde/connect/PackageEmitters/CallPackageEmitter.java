package org.kde.connect.PackageEmitters;

import android.content.Context;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.kde.connect.Types.NetworkPackage;


public class CallPackageEmitter extends BasePackageEmitter {

    public CallPackageEmitter(final Context ctx) {

        Log.i("CallPackageEmitter", "Registered");

        PhoneStateListener callStateListener = new PhoneStateListener() {

            int lastState = TelephonyManager.CALL_STATE_IDLE;
            NetworkPackage lastPackage;

            @Override
            public void onCallStateChanged(int state, String phoneNumber) {

                switch (state) {

                    case TelephonyManager.CALL_STATE_RINGING:

                        Log.e("IncomingCall", ":"+phoneNumber);

                        lastPackage = new NetworkPackage("kdeconnect.notification");

                        lastPackage.set("notificationType","ringing");
                        lastPackage.set("phoneNumber",phoneNumber);

                        sendPackage(lastPackage);

                        break;

                    case TelephonyManager.CALL_STATE_OFFHOOK: //Ongoing call

                        Log.e("OngoingCall", ":"+phoneNumber);

                        if (lastState == TelephonyManager.CALL_STATE_RINGING && lastPackage != null) {
                            //Cancel previous ringing notification
                            lastPackage.set("isCancel","true");
                            sendPackage(lastPackage);
                        }

                        //Emit a "call" package
                        lastPackage = new NetworkPackage("kdeconnect.call");
                        lastPackage.set("phoneNumber",phoneNumber);
                        sendPackage(lastPackage);

                        break;

                    case TelephonyManager.CALL_STATE_IDLE:

                        Log.e("EndedCall", ":"+phoneNumber);

                        if (lastState != TelephonyManager.CALL_STATE_IDLE && lastPackage != null) {

                            //End last notification (can either be a ring notification or a call event)
                            lastPackage.set("isCancel","true");
                            sendPackage(lastPackage);

                            if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                                //Emit a missed call notification
                                NetworkPackage missed = new NetworkPackage("kdeconnect.notification");
                                missed.set("notificationType","missedCall");
                                missed.set("phoneNumber",lastPackage.getString("phoneNumber"));
                                sendPackage(missed);
                            }

                            lastPackage = null;

                        }

                        break;

                }

                lastState = state;

            }
        };

        TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
        tm.listen(callStateListener, PhoneStateListener.LISTEN_CALL_STATE);

    }

}
