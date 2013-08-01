package org.kde.connect.PackageInterfaces;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import org.kde.connect.ContactsHelper;
import org.kde.connect.Device;
import org.kde.connect.NetworkPackage;
import org.kde.kdeconnect.R;

public class SmsNotificationPackageInterface extends BasePackageInterface {

    private Context context;

    public void smsBroadcastReceived(SmsMessage message) {

        Log.e("SmsBroadcastReceived", message.toString());

        if (this.context == null) return;

        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_NOTIFICATION);

        np.set("notificationType","sms");

        String messageBody = message.getMessageBody();
        if (messageBody != null) {
            np.set("messageBody",messageBody);
        }

        String phoneNumber = message.getOriginatingAddress();
        if (phoneNumber != null) {
            phoneNumber = ContactsHelper.phoneNumberLookup(context, phoneNumber);
            np.set("phoneNumber",phoneNumber);
        };

        sendPackage(np);
    }

    @Override
    public boolean onCreate(Context context) {
        this.context = context;
        return true;
    }

    @Override
    public void onDestroy() {
        this.context = null;
    }

    @Override
    public boolean onPackageReceived(Device d, NetworkPackage np) {
        return false;
    }

    @Override
    public boolean onDeviceConnected(Device d) {
        return false;
    }
}
