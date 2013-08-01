package org.kde.connect.PackageInterfaces;

import android.content.Context;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.kde.connect.ContactsHelper;
import org.kde.connect.Device;
import org.kde.connect.NetworkPackage;


public class CallNotificationPackageInterface extends BasePackageInterface {

    private Context context;

    private PhoneStateListener callStateListener = new PhoneStateListener() {

        int lastState = TelephonyManager.CALL_STATE_IDLE;
        NetworkPackage lastPackage = null;

        @Override
        public void onCallStateChanged(int state, String phoneNumber) {

            switch (state) {

                case TelephonyManager.CALL_STATE_RINGING:

                    //Log.i("IncomingCall", ":" + phoneNumber);

                    lastPackage = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_NOTIFICATION);

                    lastPackage.set("notificationType", "ringing");
                    if (phoneNumber != null && !phoneNumber.isEmpty()) {
                        phoneNumber = ContactsHelper.phoneNumberLookup(context,phoneNumber);
                        lastPackage.set("phoneNumber", phoneNumber);
                    }

                    sendPackage(lastPackage);

                    break;

                case TelephonyManager.CALL_STATE_OFFHOOK: //Ongoing call

                    //Log.i("OngoingCall", ":"+phoneNumber);

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
                    if (phoneNumber != null && !phoneNumber.isEmpty()) {
                        phoneNumber = ContactsHelper.phoneNumberLookup(context,phoneNumber);
                        lastPackage.set("phoneNumber", phoneNumber);
                    }
                    sendPackage(lastPackage);

                    break;

                case TelephonyManager.CALL_STATE_IDLE:

                    if (lastState != TelephonyManager.CALL_STATE_IDLE && lastPackage != null) {

                        //Log.i("EndedCall", ":"+phoneNumber);

                        //End last notification (can either be a ring notification or a call event)
                        lastPackage.set("isCancel","true");
                        sendPackage(lastPackage);

                        if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                            //Emit a missed call notification
                            NetworkPackage missed = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_NOTIFICATION);
                            missed.set("notificationType","missedCall");
                            if (phoneNumber != null && !phoneNumber.isEmpty()) {
                                missed.set("phoneNumber", lastPackage.getString("phoneNumber"));
                            }
                            sendPackage(missed);
                        }

                        lastPackage = null;

                    }

                    break;

            }

            lastState = state;

        }
    };

    @Override
    public boolean onCreate(final Context context) {

        this.context = context;
        
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        tm.listen(callStateListener, PhoneStateListener.LISTEN_CALL_STATE);

        return true;

    }

    @Override
    public void onDestroy() {
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        tm.listen(callStateListener, PhoneStateListener.LISTEN_NONE);
    }

    @Override
    public boolean onPackageReceived(Device d, NetworkPackage np) {
        //Do nothing
        return false;
    }

    public boolean onDeviceConnected(Device d) {
        //Do nothing
        return false;
    }

}
