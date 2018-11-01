/*
 * Copyright 2014 Albert Vaca Cintora <albertvaka@gmail.com>
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

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.kde.kdeconnect.Helpers.ContactsHelper;
import org.kde.kdeconnect.Helpers.SMSHelper;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.TelephonyPlugin.TelephonyPlugin;
import org.kde.kdeconnect_tp.BuildConfig;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.kde.kdeconnect.Plugins.TelephonyPlugin.TelephonyPlugin.PACKET_TYPE_TELEPHONY;

public class SMSPlugin extends Plugin {

    /**
     * Packet used to indicate a batch of messages has been pushed from the remote device
     * <p>
     * The body should contain the key "messages" mapping to an array of messages
     * <p>
     * For example:
     * { "messages" : [
     *   { "event" : "sms",
     *     "messageBody" : "Hello",
     *     "phoneNumber" : "2021234567",
     *     "messageDate" : "1518846484880",
     *     "messageType" : "2",
     *     "threadID" : "132"
     *   },
     *   { ... },
     *   ...
     * ]
     */
    private final static String PACKET_TYPE_SMS_MESSAGE = "kdeconnect.sms.messages";

    /**
     * Packet sent to request a message be sent
     * <p>
     * This will almost certainly need to be replaced or augmented to support MMS,
     * but be sure the Android side remains compatible with old desktop apps!
     * <p>
     * The body should look like so:
     * { "sendSms": true,
     * "phoneNumber": "542904563213",
     * "messageBody": "Hi mom!"
     * }
     */
    private final static String PACKET_TYPE_SMS_REQUEST = "kdeconnect.sms.request";

    /**
     * Packet sent to request the most-recent message in each conversations on the device
     * <p>
     * The request packet shall contain no body
     */
    private final static String PACKET_TYPE_SMS_REQUEST_CONVERSATIONS = "kdeconnect.sms.request_conversations";

    /**
     * Packet sent to request all the messages in a particular conversation
     * <p>
     * The body should contain the key "threadID" mapping to the threadID (as a string) being requested
     * For example:
     * { "threadID": 203 }
     */
    private final static String PACKET_TYPE_SMS_REQUEST_CONVERSATION = "kdeconnect.sms.request_conversation";

    private static final String KEY_PREF_BLOCKED_NUMBERS = "telephony_blocked_numbers";

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();

            //Log.e("TelephonyPlugin","Telephony event: " + action);

            if ("android.provider.Telephony.SMS_RECEIVED".equals(action)) {

                final Bundle bundle = intent.getExtras();
                if (bundle == null) return;
                final Object[] pdus = (Object[]) bundle.get("pdus");
                ArrayList<SmsMessage> messages = new ArrayList<>();

                for (Object pdu : pdus) {
                    // I hope, but am not sure, that the pdus array is in the order that the parts
                    // of the SMS message should be
                    // If it is not, I believe the pdu contains the information necessary to put it
                    // in order, but in my testing the order seems to be correct, so I won't worry
                    // about it now.
                    messages.add(SmsMessage.createFromPdu((byte[]) pdu));
                }

                smsBroadcastReceivedDeprecated(messages);
            }
        }
    };

    /**
     * Keep track of the most-recently-seen message so that we can query for later ones as they arrive
     */
    private long mostRecentTimestamp = 0;
    // Since the mostRecentTimestamp is accessed both from the plugin's thread and the ContentObserver
    // thread, make sure that access is coherent
    private final Lock mostRecentTimestampLock = new ReentrantLock();

    private class MessageContentObserver extends ContentObserver {
        final SMSPlugin mPlugin;

        /**
         * Create a ContentObserver to watch the Messages database. onChange is called for
         * every subscribed change
         *
         * @param parent  Plugin which owns this observer
         * @param handler Handler object used to make the callback
         */
        MessageContentObserver(SMSPlugin parent, Handler handler) {
            super(handler);
            mPlugin = parent;
        }

        /**
         * The onChange method is called whenever the subscribed-to database changes
         *
         * In this case, this onChange expects to be called whenever *anything* in the Messages
         * database changes and simply reports those updated messages to anyone who might be listening
         */
        @Override
        public void onChange(boolean selfChange) {
            if (mPlugin.mostRecentTimestamp == 0) {
                // Since the timestamp has not been initialized, we know that nobody else
                // has requested a message. That being the case, there is most likely
                // nobody listening for message updates, so just drop them
                return;
            }
            mostRecentTimestampLock.lock();
            // Grab the mostRecentTimestamp into the local stack because reading the Messages
            // database could potentially be a long operation
            long mostRecentTimestamp = mPlugin.mostRecentTimestamp;
            mostRecentTimestampLock.unlock();

            List<SMSHelper.Message> messages = SMSHelper.getMessagesSinceTimestamp(mPlugin.context, mostRecentTimestamp);

            if (messages.size() == 0) {
                // Our onChange often gets called many times for a single message. Don't make unnecessary
                // noise
                return;
            }

            // Update the most recent counter
            mostRecentTimestampLock.lock();
            for (SMSHelper.Message message : messages) {
                if (message.m_date > mostRecentTimestamp) {
                    mPlugin.mostRecentTimestamp = message.m_date;
                }
            }
            mostRecentTimestampLock.unlock();

            // Send the alert about the update
            device.sendPacket(constructBulkMessagePacket(messages));
        }
    }

    /**
     * Deliver an old-style SMS packet in response to a new message arriving
     *
     * For backwards-compatibility with long-lived distro packages, this method needs to exist in
     * order to support older desktop apps. However, note that it should no longer be used
     *
     * This comment is being written 30 August 2018. Distros will likely be running old versions for
     * many years to come...
     *
     * @param messages Ordered list of parts of the message body which should be combined into a single message
     */
    @Deprecated
    private void smsBroadcastReceivedDeprecated(ArrayList<SmsMessage> messages) {

        if (BuildConfig.DEBUG) {
            if (!(messages.size() > 0)) {
                throw new AssertionError("This method requires at least one message");
            }
        }

        NetworkPacket np = new NetworkPacket(PACKET_TYPE_TELEPHONY);

        np.set("event", "sms");

        StringBuilder messageBody = new StringBuilder();
        for (int index = 0; index < messages.size(); index++) {
            messageBody.append(messages.get(index).getMessageBody());
        }
        np.set("messageBody", messageBody.toString());

        String phoneNumber = messages.get(0).getOriginatingAddress();

        if (isNumberBlocked(phoneNumber))
            return;

        int permissionCheck = ContextCompat.checkSelfPermission(context,
                Manifest.permission.READ_CONTACTS);

        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            Map<String, String> contactInfo = ContactsHelper.phoneNumberLookup(context, phoneNumber);

            if (contactInfo.containsKey("name")) {
                np.set("contactName", contactInfo.get("name"));
            }

            if (contactInfo.containsKey("photoID")) {
                np.set("phoneThumbnail", ContactsHelper.photoId64Encoded(context, contactInfo.get("photoID")));
            }
        }
        if (phoneNumber != null) {
            np.set("phoneNumber", phoneNumber);
        }


        device.sendPacket(np);
    }

    @Override
    public boolean onCreate() {
        permissionExplanation = R.string.telepathy_permission_explanation;

        IntentFilter filter = new IntentFilter("android.provider.Telephony.SMS_RECEIVED");
        filter.setPriority(500);
        context.registerReceiver(receiver, filter);

        Looper helperLooper = SMSHelper.MessageLooper.getLooper();
        ContentObserver messageObserver = new MessageContentObserver(this, new Handler(helperLooper));
        SMSHelper.registerObserver(messageObserver, context);

        return true;
    }

    @Override
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_telepathy);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_telepathy_desc);
    }

    @Override
    public boolean onPacketReceived(NetworkPacket np) {

        switch (np.getType()) {
            case PACKET_TYPE_SMS_REQUEST_CONVERSATIONS:
                return this.handleRequestConversations(np);
            case PACKET_TYPE_SMS_REQUEST_CONVERSATION:
                return this.handleRequestConversation(np);
            case PACKET_TYPE_SMS_REQUEST:
                // Fall through to old-style handling
                // This space may be filled in differently once MMS support is implemented
            case TelephonyPlugin.PACKET_TYPE_TELEPHONY_REQUEST:
                if (np.getBoolean("sendSms")) {
                    String phoneNo = np.getString("phoneNumber");
                    String sms = np.getString("messageBody");

                    try {
                        SmsManager smsManager = SmsManager.getDefault();
                        ArrayList<String> parts = smsManager.divideMessage(sms);

                        // If this message turns out to fit in a single SMS, sendMultipartTextMessage
                        // properly handles that case
                        smsManager.sendMultipartTextMessage(phoneNo, null, parts, null, null);

                        //TODO: Notify other end
                    } catch (Exception e) {
                        //TODO: Notify other end
                        Log.e("SMSPlugin", e.getMessage());
                        e.printStackTrace();
                    }
                }
                break;
        }

        return true;
    }

    /**
     * Construct a proper packet of PACKET_TYPE_SMS_MESSAGE from the passed messages
     *
     * @param messages Messages to include in the packet
     * @return NetworkPacket of type PACKET_TYPE_SMS_MESSAGE
     */
    private static NetworkPacket constructBulkMessagePacket(Collection<SMSHelper.Message> messages) {
        NetworkPacket reply = new NetworkPacket(PACKET_TYPE_SMS_MESSAGE);

        JSONArray body = new JSONArray();

        for (SMSHelper.Message message : messages) {
            try {
                JSONObject json = message.toJSONObject();

                json.put("event", "sms");

                body.put(json);
            } catch (JSONException e) {
                Log.e("Conversations", "Error serializing message");
            }
        }

        reply.set("messages", body);
        reply.set("event", "batch_messages");

        return reply;
    }

    /**
     * Respond to a request for all conversations
     * <p>
     * Send one packet of type PACKET_TYPE_SMS_MESSAGE with the first message in all conversations
     */
    private boolean handleRequestConversations(NetworkPacket packet) {
        Map<SMSHelper.ThreadID, SMSHelper.Message> conversations = SMSHelper.getConversations(this.context);

        // Prepare the mostRecentTimestamp counter based on these messages, since they are the most
        // recent in every conversation
        mostRecentTimestampLock.lock();
        for (SMSHelper.Message message : conversations.values()) {
            if (message.m_date > mostRecentTimestamp) {
                mostRecentTimestamp = message.m_date;
            }
        }
        mostRecentTimestampLock.unlock();

        NetworkPacket reply = constructBulkMessagePacket(conversations.values());

        device.sendPacket(reply);

        return true;
    }

    private boolean handleRequestConversation(NetworkPacket packet) {
        SMSHelper.ThreadID threadID = new SMSHelper.ThreadID(packet.getInt("threadID"));

        List<SMSHelper.Message> conversation = SMSHelper.getMessagesInThread(this.context, threadID);

        NetworkPacket reply = constructBulkMessagePacket(conversation);

        device.sendPacket(reply);

        return true;
    }

    private boolean isNumberBlocked(String number) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        String[] blockedNumbers = sharedPref.getString(KEY_PREF_BLOCKED_NUMBERS, "").split("\n");

        for (String s : blockedNumbers) {
            if (PhoneNumberUtils.compare(number, s))
                return true;
        }

        return false;
    }


    @Override
    public String[] getSupportedPacketTypes() {
        return new String[]{
                PACKET_TYPE_SMS_REQUEST,
                TelephonyPlugin.PACKET_TYPE_TELEPHONY_REQUEST,
                PACKET_TYPE_SMS_REQUEST_CONVERSATIONS,
                PACKET_TYPE_SMS_REQUEST_CONVERSATION
        };
    }

    @Override
    public String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_SMS_MESSAGE};
    }

    @Override
    public String[] getRequiredPermissions() {
        return new String[]{Manifest.permission.SEND_SMS};
    }
}
