package org.kde.connect.Receivers;

import android.content.Context;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.kde.connect.DesktopCommunication;
import org.kde.connect.Types.NetworkPackage;


public class PhoneCallReceiver implements BaseReceiver {

    public PhoneCallReceiver(final Context ctx, final DesktopCommunication dc) {

        Log.i("PhoneCallReceiver", "Registered");

        PhoneStateListener callStateListener = new PhoneStateListener() {

            int lastState = TelephonyManager.CALL_STATE_IDLE;
            NetworkPackage lastPackage;

            @Override
            public void onCallStateChanged(int state, String incomingNumber) {

                switch (state) {

                    case TelephonyManager.CALL_STATE_RINGING:

                        Log.i("IncomingCall", incomingNumber);

                        lastPackage = new NetworkPackage(System.currentTimeMillis());

                        lastPackage.setType(NetworkPackage.Type.RING);
                        lastPackage.setBody(incomingNumber);

                        dc.asyncSend(lastPackage.toString());

                        break;

                    case TelephonyManager.CALL_STATE_OFFHOOK: //Ongoing call

                        if (lastPackage != null) {
                            lastPackage.cancel();
                            dc.asyncSend(lastPackage.toString());
                            lastPackage = null;
                        }

                        break;

                    case TelephonyManager.CALL_STATE_IDLE:

                        if (lastState != TelephonyManager.CALL_STATE_IDLE && lastPackage != null) {

                            lastPackage.cancel();
                            dc.asyncSend(lastPackage.toString());

                            if (lastState == TelephonyManager.CALL_STATE_RINGING) {

                                String number = lastPackage.getBody();

                                lastPackage = new NetworkPackage(System.currentTimeMillis());

                                lastPackage.setType(NetworkPackage.Type.MISSED);
                                lastPackage.setBody(incomingNumber);

                                dc.asyncSend(lastPackage.toString());

                            }

                        }

                        lastPackage = null;

                        break;

                }

                lastState = state;

            }
        };

        TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
        tm.listen(callStateListener, PhoneStateListener.LISTEN_CALL_STATE);

    }

}
