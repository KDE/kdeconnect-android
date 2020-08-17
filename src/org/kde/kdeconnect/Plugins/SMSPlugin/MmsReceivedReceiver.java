/*
 * SPDX-FileCopyrightText: 2020 Aniket Kumar <anikketkumar786@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.SMSPlugin;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import android.app.NotificationManager;
import android.app.PendingIntent;

import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;
import androidx.core.app.RemoteInput;
import androidx.core.content.ContextCompat;

import com.klinker.android.send_message.Transaction;
import com.klinker.android.send_message.Utils;

import org.kde.kdeconnect.Helpers.TelephonyHelper;
import org.kde.kdeconnect_tp.R;
import org.kde.kdeconnect.Helpers.NotificationHelper;
import org.kde.kdeconnect.Helpers.SMSHelper;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Receiver for notifying user when a new MMS has been received by the device. By default it will
 * persist the message to the internal database and it will also show a notification in the status bar.
 */
public class MmsReceivedReceiver extends com.klinker.android.send_message.MmsReceivedReceiver {

    private TelephonyHelper.ApnSetting apnSetting = null;

    @Override
    public void onMessageReceived(Context context, Uri messageUri) {
        Log.v("MmsReceived", "message received: " + messageUri.toString());

        // Notify messageUpdateReceiver about the arrival of the new MMS message
        Intent refreshIntent = new Intent(Transaction.REFRESH);
        context.sendBroadcast(refreshIntent);

        // Fetch the latest message from the database
        SMSHelper.Message message = SMSHelper.getNewestMessage(context);

        // Notify the user about the received mms message
        createMmsNotification(context, message);
    }

    @Override
    public void onError(Context context, String error) {
        Log.v("MmsReceived", "error: " + error);

        // Fetch the latest message from the database
        SMSHelper.Message message = SMSHelper.getNewestMessage(context);

        // Notify the user about the received mms message
        createMmsNotification(context, message);
    }

    public void getPreferredApn(Context context, Intent intent) {
        int subscriptionId = intent.getIntExtra(SUBSCRIPTION_ID, Utils.getDefaultSubscriptionId());
        apnSetting = TelephonyHelper.getPreferredApn(context, subscriptionId);
    }

    /**
     * some carriers will download duplicate MMS messages without this ACK. When using the
     * system sending method, apparently Android does not do this for us. Not sure why.
     * We might have to have users manually enter their APN settings if we cannot get them
     * from the system somehow.
     */
    @Override
    public MmscInformation getMmscInfoForReceptionAck() {
        if (apnSetting != null) {
            String mmscUrl = apnSetting.getMmsc().toString();
            String mmsProxy = apnSetting.getMmsProxyAddressAsString();
            int mmsPort = apnSetting.getMmsProxyPort();

            try {
                return new MmscInformation(mmscUrl, mmsProxy, mmsPort);
            } catch (Exception e) {
                Log.e("MmsReceivedReceiver", "Exception", e);
            }
        }
        return null;
    }

    private void createMmsNotification(Context context, SMSHelper.Message mmsMessage) {
        ArrayList<String> addressList = new ArrayList<>();
        for (SMSHelper.Address address : mmsMessage.addresses) {
            addressList.add(address.toString());
        }

        Person sender = NotificationReplyReceiver.getMessageSender(context, addressList.get(0));

        int notificationId;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notificationId = (int) Utils.getOrCreateThreadId(context, new HashSet<>(addressList));
        } else {
            notificationId = (int) System.currentTimeMillis();
        }

        // Todo: When SMSHelper.Message class will be modified to contain thumbnail of the image or video attachment, add them here to display.

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
                mmsMessage.body,
                TextUtils.join(",", addressList),
                mmsMessage.date,
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
