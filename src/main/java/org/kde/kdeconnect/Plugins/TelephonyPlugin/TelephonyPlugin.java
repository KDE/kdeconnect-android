package org.kde.kdeconnect.Plugins.TelephonyPlugin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Button;

import org.kde.kdeconnect.Helpers.ContactsHelper;
import org.kde.kdeconnect.NetworkPackage;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect_tp.R;

public class TelephonyPlugin extends Plugin {

    /*static {
        PluginFactory.registerPlugin(TelephonyPlugin.class);
    }*/

    @Override
    public String getPluginName() {
        return "plugin_telephony";
    }

    @Override
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_telephony);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_telephony_desc);
    }

    @Override
    public Drawable getIcon() {
        return context.getResources().getDrawable(R.drawable.icon);
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
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



    private int lastState = TelephonyManager.CALL_STATE_IDLE;
    private NetworkPackage lastPackage = null;

    public void callBroadcastReceived(int state, String phoneNumber) {

        //Log.e("TelephonyPlugin", "callBroadcastReceived");

        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_TELEPHONY);
        if (phoneNumber != null) {
            phoneNumber = ContactsHelper.phoneNumberLookup(context,phoneNumber);
            np.set("phoneNumber", phoneNumber);
        }

        switch (state) {
            case TelephonyManager.CALL_STATE_RINGING:
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

                    //Emit a missed call notification if needed
                    if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                        np.set("event","missedCall");
                        np.set("phoneNumber", lastPackage.getString("phoneNumber",null));
                        device.sendPackage(np);
                    }

                }

                break;

        }

        lastPackage = np;
        lastState = state;
    }

    public void smsBroadcastReceived(SmsMessage message) {

        //Log.e("SmsBroadcastReceived", message.toString());

        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_TELEPHONY);

        np.set("event","sms");

        String messageBody = message.getMessageBody();
        if (messageBody != null) {
            np.set("messageBody",messageBody);
        }

        String phoneNumber = message.getOriginatingAddress();
        if (phoneNumber != null) {
            phoneNumber = ContactsHelper.phoneNumberLookup(context, phoneNumber);
            np.set("phoneNumber",phoneNumber);
        }

        device.sendPackage(np);
    }

    @Override
    public boolean onCreate() {
        //Log.e("TelephonyPlugin", "onCreate");
        IntentFilter filter = new IntentFilter("android.provider.Telephony.SMS_RECEIVED");
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
        //Do nothing
        return false;
    }

    @Override
    public AlertDialog getErrorDialog(Context baseContext) {
        return null;
    }

    @Override
    public Button getInterfaceButton(Activity activity) {
        return null;
    }
}
