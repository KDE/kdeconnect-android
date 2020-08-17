/*
 * SPDX-FileCopyrightText: 2020 Aniket Kumar <anikketkumar786@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.SMSPlugin;

import android.content.Context;
import android.content.Intent;

import com.klinker.android.send_message.SentReceiver;
import com.klinker.android.send_message.Transaction;
import com.klinker.android.send_message.Utils;

import org.kde.kdeconnect.Helpers.SMSHelper;

import java.util.ArrayList;

public class SmsSentReceiver extends SentReceiver {

    @Override
    public void updateInInternalDatabase(Context context, Intent intent, int receiverResultCode) {
        super.updateInInternalDatabase(context, intent, receiverResultCode);

        if (Utils.isDefaultSmsApp(context)) {
            // Notify messageUpdateReceiver about the successful sending of the sms message
            Intent refreshIntent = new Intent(Transaction.REFRESH);
            context.sendBroadcast(refreshIntent);
        }
    }

    @Override
    public void onMessageStatusUpdated(Context context, Intent intent, int receiverResultCode) {
        SMSHelper.Message message = SMSHelper.getNewestMessage(context);

        ArrayList<String>  addressList =  new ArrayList<>();
        for (SMSHelper.Address address : message.addresses) {
            addressList.add(address.toString());
        }

        Intent repliedNotification = new Intent(context, NotificationReplyReceiver.class);
        repliedNotification.setAction(NotificationReplyReceiver.SMS_MMS_REPLY_ACTION);
        repliedNotification.putExtra(NotificationReplyReceiver.TEXT_BODY, message.body);
        repliedNotification.putExtra(NotificationReplyReceiver.NOTIFICATION_ID, Integer.parseInt(message.threadID.toString()));
        repliedNotification.putExtra(NotificationReplyReceiver.ADDRESS_LIST, addressList);

        // SEND_ACTION value is required to differentiate between the intents sent from reply action or
        // SentReceivers inorder to avoid posting duplicate notifications
        repliedNotification.putExtra(NotificationReplyReceiver.SEND_ACTION, false);

        context.sendBroadcast(repliedNotification);
    }
}
