/*
 * SPDX-FileCopyrightText: 2020 Aniket Kumar <anikketkumar786@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.SMSPlugin;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;
import androidx.core.app.RemoteInput;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.klinker.android.send_message.Utils;

import org.kde.kdeconnect.Helpers.ContactsHelper;
import org.kde.kdeconnect.Helpers.NotificationHelper;
import org.kde.kdeconnect.Helpers.SMSHelper;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;

public class NotificationReplyReceiver extends BroadcastReceiver {
    public static final String SMS_MMS_REPLY_ACTION = "org.kde.kdeconnect.Plugins.SMSPlugin.sms_mms_reply_action";
    public static final String SMS_MMS_MARK_ACTION = "org.kde.kdeconnect.Plugins.SMSPlugin.sms_mms_mark_action";
    public static final String ADDRESS_LIST = "address_list";
    public static final String CHANNEL_ID = NotificationHelper.Channels.SMS_MMS;
    public static final String KEY_TEXT_REPLY = "key_text_reply";
    public static final String NOTIFICATION_ID = "notification_id";
    public static final String SEND_ACTION = "send_action";
    public static final String TEXT_BODY = "text_body";
    public static final String SMS_NOTIFICATION_GROUP_KEY = "Plugins.SMSPlugin.sms_notification_group_key";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Utils.isDefaultSmsApp(context) || intent == null) {
            return;
        }

        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);

        final int notificationId = intent.getIntExtra(NotificationReplyReceiver.NOTIFICATION_ID, 0);
        final ArrayList<String> addressList = intent.getStringArrayListExtra(ADDRESS_LIST);
        final boolean sentUsingReplyButton = intent.getBooleanExtra(SEND_ACTION, false);

        if (intent.getAction().equals(SMS_MMS_REPLY_ACTION)) {
            String inputString = null;

            if (sentUsingReplyButton) {
                inputString = remoteInput.getCharSequence(NotificationReplyReceiver.KEY_TEXT_REPLY).toString();

                ArrayList<SMSHelper.Address> addresses = new ArrayList<>();
                for (String address : addressList) {
                    addresses.add(new SMSHelper.Address(address));
                }
                SmsMmsUtils.sendMessage(context, inputString, addresses, -1);
            } else {
                inputString = intent.getStringExtra(TEXT_BODY);
                repliedMessageNotification(context, notificationId, inputString, addressList);

            }
        }

        // Mark the conversation as read
        if (intent.getAction().equals(SMS_MMS_MARK_ACTION)) {
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(notificationId);
            SmsMmsUtils.markConversationRead(context, new HashSet<>(addressList));
        }
    }

    /**
     * Updates the active notification with the newly replied message
     */
    private void repliedMessageNotification(Context context, int notificationId, String inputString, ArrayList<String> addressList) {
        Person sender = new Person.Builder()
                .setName(context.getString(R.string.user_display_name))
                .build();

        // Create pending intent for reply action through notification
        PendingIntent replyPendingIntent = createReplyPendingIntent(context, addressList, notificationId, true);

        RemoteInput remoteReplyInput = new RemoteInput.Builder(NotificationReplyReceiver.KEY_TEXT_REPLY)
                .setLabel(context.getString(R.string.message_reply_label))
                .build();

        NotificationCompat.Action replyAction = new NotificationCompat.Action.Builder(0, context.getString(R.string.message_reply_label), replyPendingIntent)
                .addRemoteInput(remoteReplyInput)
                .setAllowGeneratedReplies(true)
                .build();

        // Create pending intent for marking the message as read in database through mark as read action
        PendingIntent markAsReadPendingIntent = createMarkAsReadPendingIntent(context, addressList, notificationId);

        NotificationCompat.Action markAsReadAction = new NotificationCompat.Action.Builder(0, context.getString(R.string.mark_as_read_label), markAsReadPendingIntent)
                .build();

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.MessagingStyle.Message message = new NotificationCompat.MessagingStyle.Message(
                inputString,
                System.currentTimeMillis(),
                sender
        );

        NotificationCompat.MessagingStyle messagingStyle = restoreActiveMessagingStyle(notificationId, notificationManager);

        if (messagingStyle == null) {
            // Return when there is no active notification in the statusBar with the above notificationId
            return;
        }

        messagingStyle.addMessage(message);

        NotificationCompat.Builder repliedNotification = new NotificationCompat.Builder(context, NotificationReplyReceiver.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_baseline_sms_24)
                .setColor(ContextCompat.getColor(context, R.color.primary))
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setStyle(messagingStyle)
                .setAutoCancel(true)
                .addAction(replyAction)
                .addAction(markAsReadAction)
                .setGroup(NotificationReplyReceiver.SMS_NOTIFICATION_GROUP_KEY);

        NotificationHelper.notifyCompat(notificationManager, notificationId, repliedNotification.build());
    }

    /**
     * This method creates a new messaging style for newer conversations and if there is already an active notification
     * of the same id, it just adds to the previous and returns the modified messagingStyle object.
     */
    public static NotificationCompat.MessagingStyle createMessagingStyle(
            Context context,
            int notificationId,
            String textMessage,
            String phoneNumbers,
            long date,
            Person sender,
            NotificationManager notificationManager
    ) {
        NotificationCompat.MessagingStyle messageStyle = NotificationReplyReceiver.restoreActiveMessagingStyle(
                notificationId,
                notificationManager
        );

        NotificationCompat.MessagingStyle.Message message = new NotificationCompat.MessagingStyle.Message(
                textMessage,
                date,
                sender
        );

        if (messageStyle == null) {
            // When no active notification is found for matching conversation create a new one
            String senderName = phoneNumbers;
            Map<String, String> contactInfo = ContactsHelper.phoneNumberLookup(context, phoneNumbers);

            if (contactInfo.containsKey("name")) {
                senderName = contactInfo.get("name");
            }

            messageStyle = new NotificationCompat.MessagingStyle(sender)
                    .setConversationTitle(senderName);
        }
        messageStyle.addMessage(message);

        return messageStyle;
    }

    /**
     * This method is responsible for searching the notification for same conversation ID and if there is an active notification
     * of save ID found in the status menu it extracts and returns the messagingStyle object of that notification
     */
    public static NotificationCompat.MessagingStyle restoreActiveMessagingStyle(
            int notificationId,
            NotificationManager notificationManager
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StatusBarNotification notifications[] = notificationManager.getActiveNotifications();
            for (StatusBarNotification notification : notifications) {
                if (notification.getId() == notificationId) {
                    return NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification.getNotification());
                }
            }
        }
        return null;
    }

    /**
     * returns the sender of the message as a Person object
     */
    public static Person getMessageSender(Context context, String address) {
        Map<String, String> contactInfo = ContactsHelper.phoneNumberLookup(context, address);
        String senderName = address;

        if (contactInfo.containsKey("name")) {
            senderName = contactInfo.get("name");
        }

        Bitmap contactPhoto = null;
        if (contactInfo.containsKey("photoID")) {
            String photoUri = contactInfo.get("photoID");
            if (photoUri != null) {
                try {
                    String base64photo = ContactsHelper.photoId64Encoded(context, photoUri);
                    if (!TextUtils.isEmpty(base64photo)) {
                        byte[] decodedString = Base64.decode(base64photo, Base64.DEFAULT);
                        contactPhoto = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                    }
                } catch (Exception e) {
                    Log.e("SMS Notification", "Failed to get contact photo");
                }
            }
        }

        Person.Builder personBuilder = new Person.Builder()
                .setName(senderName);

        if (contactPhoto != null) {
            personBuilder.setIcon(IconCompat.createWithBitmap(contactPhoto));
        }

        return personBuilder.build();
    }

    /**
     * Create pending intent for reply action through notification
     */
    public static PendingIntent createReplyPendingIntent(
            Context context,
            ArrayList<String> addressList,
            int notificationId,
            boolean isFromSendAction
    ) {
        Intent replyIntent = new Intent(context, NotificationReplyReceiver.class);
        replyIntent.setAction(NotificationReplyReceiver.SMS_MMS_REPLY_ACTION);
        replyIntent.putExtra(NotificationReplyReceiver.NOTIFICATION_ID, notificationId);
        replyIntent.putExtra(NotificationReplyReceiver.ADDRESS_LIST, addressList);
        replyIntent.putExtra(NotificationReplyReceiver.SEND_ACTION, isFromSendAction);

        PendingIntent replyPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
        );

        return replyPendingIntent;
    }

    /**
     * Create pending intent for marking the message as read in database through mark as read action
     */
    public static PendingIntent createMarkAsReadPendingIntent(
            Context context,
            ArrayList<String> addressList,
            int notificationId
    ) {
        Intent markAsReadIntent = new Intent(context, NotificationReplyReceiver.class);
        markAsReadIntent.setAction(NotificationReplyReceiver.SMS_MMS_MARK_ACTION);
        markAsReadIntent.putExtra(NotificationReplyReceiver.NOTIFICATION_ID, notificationId);
        markAsReadIntent.putExtra(NotificationReplyReceiver.ADDRESS_LIST, addressList);

        PendingIntent markAsReadPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                markAsReadIntent,
                PendingIntent.FLAG_CANCEL_CURRENT
        );

        return markAsReadPendingIntent;
    }
}
