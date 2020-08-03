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

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.provider.Telephony.Sms;
import android.net.Uri;
import android.content.ContentValues;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;
import androidx.core.app.RemoteInput;
import androidx.core.content.ContextCompat;

import com.klinker.android.send_message.Transaction;
import com.klinker.android.send_message.Utils;

import org.kde.kdeconnect.Helpers.NotificationHelper;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;
import java.util.Arrays;

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

                    String body = message[i].getMessageBody();
                    String phoneNo = message[i].getOriginatingAddress();
                    long date = message[i].getTimestampMillis();

                    createSmsNotification(context, body, phoneNo, date);
                }
            }
        }
    }

    private void createSmsNotification(Context context, String body, String phoneNo, long date) {
        int notificationId;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notificationId = (int) Utils.getOrCreateThreadId(context, phoneNo);
        } else {
            notificationId = (int) System.currentTimeMillis();
        }

        Person sender = NotificationReplyReceiver.getMessageSender(context, phoneNo);

        ArrayList<String> addressList = new ArrayList<>(Arrays.asList(phoneNo));

        // Create pending intent for reply action through notification
        PendingIntent replyPendingIntent = NotificationReplyReceiver.createReplyPendingIntent(
                context,
                addressList,
                notificationId,
                true
        );

        RemoteInput remoteReplyInput = new RemoteInput.Builder(NotificationReplyReceiver.KEY_TEXT_REPLY)
                .setLabel(context.getString(R.string.message_reply_label))
                .build();

        NotificationCompat.Action replyAction = new NotificationCompat.Action.Builder(0, context.getString(R.string.message_reply_label), replyPendingIntent)
                .addRemoteInput(remoteReplyInput)
                .setAllowGeneratedReplies(true)
                .build();

        // Create pending intent for marking the message as read in database through mark as read action
        PendingIntent markAsReadPendingIntent = NotificationReplyReceiver.createMarkAsReadPendingIntent(
                context,
                addressList,
                notificationId
        );

        NotificationCompat.Action markAsReadAction = new NotificationCompat.Action.Builder(0, context.getString(R.string.mark_as_read_label), markAsReadPendingIntent)
                .build();

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.MessagingStyle messagingStyle = NotificationReplyReceiver.createMessagingStyle(
                context,
                notificationId,
                body,
                phoneNo,
                date,
                sender,
                notificationManager
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NotificationReplyReceiver.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_baseline_sms_24)
                .setColor(ContextCompat.getColor(context, R.color.primary))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setStyle(messagingStyle)
                .setAutoCancel(true)
                .addAction(replyAction)
                .addAction(markAsReadAction)
                .setGroup(NotificationReplyReceiver.SMS_NOTIFICATION_GROUP_KEY);

        NotificationHelper.notifyCompat(notificationManager, notificationId, builder.build());
    }
}
