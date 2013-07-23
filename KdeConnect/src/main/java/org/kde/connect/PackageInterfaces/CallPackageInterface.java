package org.kde.connect.PackageInterfaces;

import android.content.Context;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.kde.connect.Device;
import org.kde.connect.NetworkPackage;


public class CallPackageInterface extends BasePackageInterface {

    public CallPackageInterface(final Context ctx) {

        Log.i("CallPackageInterface", "Registered");

        PhoneStateListener callStateListener = new PhoneStateListener() {

            int lastState = TelephonyManager.CALL_STATE_IDLE;
            NetworkPackage lastPackage = null;

            @Override
            public void onCallStateChanged(int state, String phoneNumber) {

                switch (state) {

                    case TelephonyManager.CALL_STATE_RINGING:

                        Log.e("IncomingCall", ":" + phoneNumber);

                        lastPackage = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_NOTIFICATION);

                        lastPackage.set("notificationType", "ringing");
                        lastPackage.set("phoneNumber", phoneNumber);

                        sendPackage(lastPackage);

                        break;

                    case TelephonyManager.CALL_STATE_OFFHOOK: //Ongoing call

                        Log.e("OngoingCall", ":"+phoneNumber);

                        /*
                        //Actually we do not want to cancel it
                        if (lastState == TelephonyManager.CALL_STATE_RINGING && lastPackage != null) {
                            //Cancel previous ringing notification
                            lastPackage.set("isCancel","true");
                            sendPackage(lastPackage);
                        }
                        */

                        //Emit a "call" package
                        lastPackage = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_CALL);
                        lastPackage.set("phoneNumber",phoneNumber);
                        sendPackage(lastPackage);

                        break;

                    case TelephonyManager.CALL_STATE_IDLE:

                        if (lastState != TelephonyManager.CALL_STATE_IDLE && lastPackage != null) {

                            Log.e("EndedCall", ":"+phoneNumber);

                            //End last notification (can either be a ring notification or a call event)
                            lastPackage.set("isCancel","true");
                            sendPackage(lastPackage);

                            if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                                //Emit a missed call notification
                                NetworkPackage missed = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_NOTIFICATION);
                                missed.set("notificationType","missedCall");
                                missed.set("phoneNumber", lastPackage.getString("phoneNumber"));
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

    @Override
    public void onPackageReceived(Device d, NetworkPackage np) {
        //Do nothing
    }
}
