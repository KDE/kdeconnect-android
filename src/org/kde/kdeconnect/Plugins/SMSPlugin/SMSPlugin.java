/*
 * Copyright 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 * Copyright 2019 Simon Redman <simon@ergotech.com>
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
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Telephony;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.kde.kdeconnect.Helpers.ContactsHelper;
import org.kde.kdeconnect.Helpers.SMSHelper;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect.Plugins.TelephonyPlugin.TelephonyPlugin;
import org.kde.kdeconnect_tp.BuildConfig;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import androidx.core.content.ContextCompat;

import com.klinker.android.send_message.ApnUtils;
import com.klinker.android.send_message.Transaction;
import com.klinker.android.send_message.Utils;
import com.klinker.android.logger.Log;

import static org.kde.kdeconnect.Plugins.TelephonyPlugin.TelephonyPlugin.PACKET_TYPE_TELEPHONY;

@PluginFactory.LoadablePlugin
@SuppressLint("InlinedApi")
public class SMSPlugin extends Plugin {

    /**
     * Packet used to indicate a batch of messages has been pushed from the remote device
     * <p>
     * The body should contain the key "messages" mapping to an array of messages
     * <p>
     * For example:
     * {
     *   "version": 2                     // This is the second version of this packet type and
     *                                    // version 1 packets (which did not carry this flag)
     *                                    // are incompatible with the new format
     *   "messages" : [
     *   { "event"     : 1,               // 32-bit field containing a bitwise-or of event flags
     *                                    // See constants declared in SMSHelper.Message for defined
     *                                    // values and explanations
     *     "body"      : "Hello",         // Text message body
     *     "addresses": <List<Address>>   // List of Address objects, one for each participant of the conversation
     *                                    // The user's Address is excluded so:
     *                                    // If this is a single-target messsage, there will only be one
     *                                    // Address (the other party)
     *                                    // If this is an incoming multi-target message, the first Address is the
     *                                    // sender and all other addresses are other parties to the conversation
     *                                    // If this is an outgoing multi-target message, the sender is implicit
     *                                    // (the user's phone number) and all Addresses are recipients
     *     "date"      : "1518846484880", // Timestamp of the message
     *     "type"      : "2",   // Compare with Android's
     *                          // Telephony.TextBasedSmsColumns.MESSAGE_TYPE_*
     *     "thread_id" : 132    // Thread to which the message belongs
     *     "read"      : true   // Boolean representing whether a message is read or unread
     *   },
     *   { ... },
     *   ...
     * ]
     *
     * The following optional fields of a message object may be defined
     * "sub_id": <int> // Android's subscriber ID, which is basically used to determine which SIM card the message
     *                 // belongs to. This is mostly useful when attempting to reply to an SMS with the correct
     *                 // SIM card using PACKET_TYPE_SMS_REQUEST.
     *                 // If this value is not defined or if it does not match a valid subscriber_id known by
     *                 // Android, we will use whatever subscriber ID Android gives us as the default
     *
     * An Address object looks like:
     * {
     *     "address": <String> // Address (phone number, email address, etc.) of this object
     * }
     */
    private final static String PACKET_TYPE_SMS_MESSAGE = "kdeconnect.sms.messages";
    private final static int SMS_MESSAGE_PACKET_VERSION = 2; // We *send* packets of this version

    /**
     * Packet sent to request a message be sent
     * <p>
     * This will almost certainly need to be replaced or augmented to support MMS,
     * but be sure the Android side remains compatible with old desktop apps!
     * <p>
     * The body should look like so:
     * { "sendSms": true,
     *   "phoneNumber": "542904563213"     // For older desktop versions of SMS app this packet carries phoneNumber field
     *   "addresses": <List of Addresses>  // For newer desktop versions of SMS app it contains addresses field instead of phoneNumber field
     *   "messageBody": "Hi mom!",
     *   "sub_id": "3859358340534"
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
     * The following fields are available:
     * "threadID": <long>            // (Required) ThreadID to request
     * "rangeStartTimestamp": <long> // (Optional) Millisecond epoch timestamp indicating the start of the range from which to return messages
     * "numberToRequest": <long>     // (Optional) Number of messages to return, starting from rangeStartTimestamp.
     *                               // May return fewer than expected if there are not enough or more than expected if many
     *                               // messages have the same timestamp.
     */
    private final static String PACKET_TYPE_SMS_REQUEST_CONVERSATION = "kdeconnect.sms.request_conversation";

    private static final String KEY_PREF_BLOCKED_NUMBERS = "telephony_blocked_numbers";

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();

            //Log.e("TelephonyPlugin","Telephony event: " + action);

            if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(action)) {

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

        /**
         * Create a ContentObserver to watch the Messages database. onChange is called for
         * every subscribed change
         *
         * @param handler Handler object used to make the callback
         */
        MessageContentObserver(Handler handler) {
            super(handler);
        }

        /**
         * The onChange method is called whenever the subscribed-to database changes
         *
         * In this case, this onChange expects to be called whenever *anything* in the Messages
         * database changes and simply reports those updated messages to anyone who might be listening
         */
        @Override
        public void onChange(boolean selfChange) {
            // If the KDE Connect is set as default Sms app
            // prevent from reading the latest message in the database before the sentReceivers mark it as sent
            if (Utils.isDefaultSmsApp(context)) {
                return;
            }

            sendLatestMessage();
        }

    }

    /**
     * This receiver will be invoked only when the app will be set as the default sms app
     * Whenever the app will be set as the default, the database update alert will be sent
     * using messageUpdateReceiver and not the contentObserver class
     */
    private final BroadcastReceiver messagesUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();

            if (Transaction.REFRESH.equals(action)) {
                sendLatestMessage();
            }
        }
    };

    /**
     * Helper method to read the latest message from the sms-mms database and sends it to the desktop
     */
    private void sendLatestMessage() {
        // Lock so no one uses the mostRecentTimestamp between the moment we read it and the
        // moment we update it. This is because reading the Messages DB can take long.
        mostRecentTimestampLock.lock();

        if (mostRecentTimestamp == 0) {
            // Since the timestamp has not been initialized, we know that nobody else
            // has requested a message. That being the case, there is most likely
            // nobody listening for message updates, so just drop them
            mostRecentTimestampLock.unlock();
            return;
        }
        SMSHelper.Message message = SMSHelper.getNewestMessage(context);

        if (message == null || message.date <= mostRecentTimestamp) {
            // onChange can trigger many times for a single message. Don't make unnecessary noise
            mostRecentTimestampLock.unlock();
            return;
        }

        // Update the most recent counter
        mostRecentTimestamp = message.date;
        mostRecentTimestampLock.unlock();

        // Send the alert about the update
        device.sendPacket(constructBulkMessagePacket(Collections.singleton(message)));
        Log.e("sent", "update");
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

        IntentFilter filter = new IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION);
        filter.setPriority(500);
        context.registerReceiver(receiver, filter);

        IntentFilter refreshFilter = new IntentFilter(Transaction.REFRESH);
        refreshFilter.setPriority(500);
        context.registerReceiver(messagesUpdateReceiver, refreshFilter);

        Looper helperLooper = SMSHelper.MessageLooper.getLooper();
        ContentObserver messageObserver = new MessageContentObserver(new Handler(helperLooper));
        SMSHelper.registerObserver(messageObserver, context);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            Log.w("SMSPlugin", "This is a very old version of Android. The SMS Plugin might not function as intended.");
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            ApnUtils.initDefaultApns(context, null);
        }

        // To see debug messages for Klinker library, uncomment the below line
        //Log.setDebug(true);

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
                if (np.getBoolean("sendSms")) {
                    String textMessage = np.getString("messageBody");
                    long subID = np.getLong("subID", -1);

                    List<SMSHelper.Address> addressList = SMSHelper.jsonArrayToAddressList(np.getJSONArray("addresses"));
                    if (addressList == null) {
                        // If the List of Address is null, then the SMS_REQUEST packet is
                        // most probably from the older version of the desktop app.
                        addressList = new ArrayList<>();
                        addressList.add(new SMSHelper.Address(np.getString("phoneNumber")));
                    }

                    SmsMmsUtils.sendMessage(context, textMessage, addressList, (int) subID);
                }
                break;

            case TelephonyPlugin.PACKET_TYPE_TELEPHONY_REQUEST:
                if (np.getBoolean("sendSms")) {
                    String phoneNo = np.getString("phoneNumber");
                    String sms = np.getString("messageBody");
                    long subID = np.getLong("subID", -1);

                    try {
                        SmsManager smsManager = subID == -1? SmsManager.getDefault() :
                            SmsManager.getSmsManagerForSubscriptionId((int) subID);
                        ArrayList<String> parts = smsManager.divideMessage(sms);

                        // If this message turns out to fit in a single SMS, sendMultipartTextMessage
                        // properly handles that case
                        smsManager.sendMultipartTextMessage(phoneNo, null, parts, null, null);

                        //TODO: Notify other end
                    } catch (Exception e) {
                        //TODO: Notify other end
                        Log.e("SMSPlugin", "Exception", e);
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

                body.put(json);
            } catch (JSONException e) {
                Log.e("Conversations", "Error serializing message", e);
            }
        }

        reply.set("messages", body);
        reply.set("version", SMS_MESSAGE_PACKET_VERSION);

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
            if (message.date > mostRecentTimestamp) {
                mostRecentTimestamp = message.date;
            }
        }
        mostRecentTimestampLock.unlock();

        NetworkPacket reply = constructBulkMessagePacket(conversations.values());

        device.sendPacket(reply);

        return true;
    }

    private boolean handleRequestConversation(NetworkPacket packet) {
        SMSHelper.ThreadID threadID = new SMSHelper.ThreadID(packet.getLong("threadID"));

        Long rangeStartTimestamp = packet.getLong("rangeStartTimestamp", -1);
        Long numberToGet = packet.getLong("numberToRequest", -1);

        if (numberToGet < 0) {
            numberToGet = null;
        }

        List<SMSHelper.Message> conversation;
        if (rangeStartTimestamp < 0) {
            conversation = SMSHelper.getMessagesInThread(this.context, threadID, numberToGet);
        } else {
            conversation = SMSHelper.getMessagesInRange(this.context, threadID, rangeStartTimestamp, numberToGet);
        }

        // Sometimes when desktop app is kept open while android app is restarted for any reason
        // mostRecentTimeStamp must be updated in that scenario too if a user request for a
        // single conversation and not the entire conversation list
        mostRecentTimestampLock.lock();
        for (SMSHelper.Message message : conversation) {
            if (message.date > mostRecentTimestamp) {
                mostRecentTimestamp = message.date;
            }
        }
        mostRecentTimestampLock.unlock();

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
    public boolean hasSettings() {
        return true;
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
        return new String[]{
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_SMS,
                // READ_PHONE_STATE should be optional, since we can just query the user, but that
                // requires a GUI implementation for querying the user!
                Manifest.permission.READ_PHONE_STATE,
        };
    }

    /**
     * Permissions required for sending and receiving MMs messages
     */
    public static String[] getMmsPermissions() {
        return new String[]{
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.RECEIVE_MMS,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CHANGE_NETWORK_STATE,
                Manifest.permission.WAKE_LOCK,
        };
    }

    /**
     * With versions older than KITKAT, lots of the content providers used in SMSHelper become
     * un-documented. Most manufacturers *did* do things the same way as was done in mainline
     * Android at that time, but some did not. If the manufacturer followed the default route,
     * everything will be fine. If not, the plugin will crash. But, since we have a global catch-all
     * in Device.onPacketReceived, it will not crash catastrophically.
     * The onCreated method of this SMSPlugin complains if a version older than KitKat is loaded,
     * but it still allowed in the optimistic hope that things will "just work"
     */
    @Override
    public int getMinSdk() {
        return Build.VERSION_CODES.FROYO;
    }
}
