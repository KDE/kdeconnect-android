/*
 * Copyright 2020 Aniket Kumar <anikketkumar786@gmail.com>
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

package org.kde.kdeconnect.Plugins.SMSPlugin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.provider.Telephony.Sms;
import android.net.Uri;
import android.content.ContentValues;

import com.klinker.android.send_message.Transaction;
import com.klinker.android.send_message.Utils;

public class SmsReceiver extends BroadcastReceiver {

    private static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Utils.isDefaultSmsApp(context)) {
            return;
        }

        if (intent != null && intent.getAction().equals(SMS_RECEIVED)) {
            Bundle dataBundle = intent.getExtras();

            if (dataBundle != null) {
                Object[] smsExtra = (Object[]) dataBundle.get("pdus");
                final SmsMessage[] message = new SmsMessage[smsExtra.length];

                for (int i = 0; i < smsExtra.length; ++i) {

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        String format = dataBundle.getString("format");
                        message[i] = SmsMessage.createFromPdu((byte[]) smsExtra[i], format);
                    } else {
                        message[i] = SmsMessage.createFromPdu((byte[]) smsExtra[i]);
                    }

                    // Write the received sms to the sms provider
                    for (SmsMessage msg : message) {
                        ContentValues values = new ContentValues();
                        values.put(Sms.ADDRESS, msg.getDisplayOriginatingAddress());
                        values.put(Sms.BODY, msg.getMessageBody());
                        values.put(Sms.DATE, System.currentTimeMillis()+"");
                        values.put(Sms.TYPE, Sms.MESSAGE_TYPE_INBOX);
                        values.put(Sms.STATUS, msg.getStatus());
                        values.put(Sms.READ, 0);
                        values.put(Sms.SEEN, 0);
                        context.getApplicationContext().getContentResolver().insert(Uri.parse("content://sms/"), values);

                        // Notify messageUpdateReceiver about the arrival of the new sms message
                        Intent refreshIntent = new Intent(Transaction.REFRESH);
                        context.sendBroadcast(refreshIntent);
                    }
                }
            }
        }
    }
}
