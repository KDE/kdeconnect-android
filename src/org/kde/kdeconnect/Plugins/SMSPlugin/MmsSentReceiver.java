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

import android.content.Context;
import android.content.Intent;

import com.klinker.android.send_message.Transaction;
import com.klinker.android.send_message.Utils;

import org.kde.kdeconnect.Helpers.SMSHelper;

import java.util.ArrayList;

public class MmsSentReceiver extends com.klinker.android.send_message.MmsSentReceiver {

    @Override
    public void updateInInternalDatabase(Context context, Intent intent, int resultCode) {
        super.updateInInternalDatabase(context, intent, resultCode);

        if (Utils.isDefaultSmsApp(context)) {
            // Notify messageUpdateReceiver about the successful sending of the mms message
            Intent refreshIntent = new Intent(Transaction.REFRESH);
            context.sendBroadcast(refreshIntent);
        }
    }

    @Override
    public void onMessageStatusUpdated(Context context, Intent intent, int resultCode) {
        SMSHelper.Message message = SMSHelper.getNewestMessage(context);

        ArrayList<String> addressList =  new ArrayList<>();
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
