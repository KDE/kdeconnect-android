/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 * SPDX-FileCopyrightText: 2021 Simon Redman <simon@ergotech.com>
 * SPDX-FileCopyrightText: 2020 Aniket Kumar <anikketkumar786@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.sms

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
import android.provider.Telephony
import android.telephony.PhoneNumberUtils
import android.telephony.SmsMessage
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import com.klinker.android.logger.Log
import com.klinker.android.send_message.Transaction
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.kde.kdeconnect.helpers.ContactsHelper
import org.kde.kdeconnect.helpers.SMSHelper
import org.kde.kdeconnect.helpers.SMSHelper.MessageLooper.Companion.getLooper
import org.kde.kdeconnect.helpers.SMSHelper.ThreadID
import org.kde.kdeconnect.helpers.SMSHelper.getConversations
import org.kde.kdeconnect.helpers.SMSHelper.getMessagesInRange
import org.kde.kdeconnect.helpers.SMSHelper.getMessagesInThread
import org.kde.kdeconnect.helpers.SMSHelper.getNewestMessageTimestamp
import org.kde.kdeconnect.helpers.SMSHelper.jsonArrayToAddressList
import org.kde.kdeconnect.helpers.SMSHelper.jsonArrayToAttachmentsList
import org.kde.kdeconnect.helpers.ThreadHelper.execute
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginFactory.LoadablePlugin
import org.kde.kdeconnect.plugins.sms.SmsMmsUtils.partIdToMessageAttachmentPacket
import org.kde.kdeconnect.plugins.sms.SmsMmsUtils.sendMessage
import org.kde.kdeconnect.plugins.telephony.TelephonyPlugin
import org.kde.kdeconnect.ui.PluginSettingsFragment
import org.kde.kdeconnect_tp.BuildConfig
import org.kde.kdeconnect_tp.R
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

@LoadablePlugin
@SuppressLint("InlinedApi")
class SMSPlugin : Plugin() {
    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action: String? = intent.action

            //Log.e("TelephonyPlugin","Telephony event: " + action)
            if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION == action) {
                val bundle: Bundle = intent.extras ?: return
                val pdus: Array<ByteArray?> = bundle.get("pdus") as Array<ByteArray?>
                val messages: MutableList<SmsMessage> = mutableListOf()

                for (pdu in pdus) {
                    // I hope, but am not sure, that the pdus array is in the order that the parts
                    // of the SMS message should be
                    // If it is not, I believe the pdu contains the information necessary to put it
                    // in order, but in my testing the order seems to be correct, so I won't worry
                    // about it now.
                    messages.add(SmsMessage.createFromPdu(pdu))
                }

                smsBroadcastReceivedDeprecated(messages)
            }
        }
    }

    /**
     * Keep track of the most-recently-seen message so that we can query for later ones as they arrive
     */
    private var mostRecentTimestamp: Long = 0

    // Since the mostRecentTimestamp is accessed both from the plugin's thread and the ContentObserver
    // thread, make sure that access is coherent
    private val mostRecentTimestampLock: Lock = ReentrantLock()

    /**
     * Keep track of whether we have received any packet which requested messages.
     *
     * If not, we will not send updates, since probably the user doesn't care.
     */
    private var haveMessagesBeenRequested: Boolean = false

    private inner class MessageContentObserver
    /**
     * Create a ContentObserver to watch the Messages database. onChange is called for
     * every subscribed change
     *
     * @param handler Handler object used to make the callback
     */
        (handler: Handler?) : ContentObserver(handler) {
        /**
         * The onChange method is called whenever the subscribed-to database changes
         *
         * In this case, this onChange expects to be called whenever *anything* in the Messages
         * database changes and simply reports those updated messages to anyone who might be listening
         */
        override fun onChange(selfChange: Boolean) {
            sendLatestMessage()
        }
    }

    /**
     * This receiver will be invoked only when the app will be set as the default sms app
     * Whenever the app will be set as the default, the database update alert will be sent
     * using messageUpdateReceiver and not the contentObserver class
     */
    private val messagesUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action: String? = intent.action

            if (Transaction.REFRESH == action) {
                sendLatestMessage()
            }
        }
    }

    private val messageObserver: ContentObserver = MessageContentObserver(Handler(getLooper()!!))


    /**
     * Helper method to read the latest message from the sms-mms database and sends it to the desktop
     *
     * Should only be called after initializing the mostRecentTimestamp
     */
    private fun sendLatestMessage() {
        // Lock so no one uses the mostRecentTimestamp between the moment we read it and the
        // moment we update it. This is because reading the Messages DB can take long.
        mostRecentTimestampLock.lock()

        if (!haveMessagesBeenRequested) {
            // Since the user has not requested a message, there is most likely nobody listening
            // for message updates, so just drop them rather than spending battery/time sending
            // updates that don't matter.
            mostRecentTimestampLock.unlock()
            return
        }
        val messages: List<SMSHelper.Message> = getMessagesInRange(context, null, mostRecentTimestamp, null, false)

        var newMostRecentTimestamp: Long = mostRecentTimestamp
        for (message: SMSHelper.Message in messages) {
            if (message.date >= newMostRecentTimestamp) {
                newMostRecentTimestamp = message.date
            }
        }

        // Update the most recent counter
        mostRecentTimestamp = newMostRecentTimestamp
        mostRecentTimestampLock.unlock()

        // Send the alert about the update
        device.sendPacket(constructBulkMessagePacket(messages))
    }

    /**
     * Deliver an old-style SMS packet in response to a new message arriving
     *
     * For backwards-compatibility with long-lived distro packages, this method needs to exist in
     * order to support older desktop apps. However, note that it should no longer be used
     *
     * This comment is being written 30 August 2018. Distros will likely be running old versions for many years to come...
     *
     * @param messages Ordered list of parts of the message body which should be combined into a single message
     */
    @Deprecated("")
    private fun smsBroadcastReceivedDeprecated(messages: MutableList<SmsMessage>) {
        if (BuildConfig.DEBUG) {
            if (messages.isEmpty()) {
                throw AssertionError("This method requires at least one message")
            }
        }

        val np = NetworkPacket(TelephonyPlugin.PACKET_TYPE_TELEPHONY)

        np["event"] = "sms"

        np["messageBody"] = buildString {
            for (message in messages) {
                append(message.messageBody)
            }
        }

        val phoneNumber: String? = messages[0].originatingAddress

        if (isNumberBlocked(phoneNumber)) return

        val permissionCheck: Int = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)

        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            val contactInfo: Map<String, String> = ContactsHelper.phoneNumberLookup(context, phoneNumber)

            val name = contactInfo["name"]
            if (name != null) {
                np["contactName"] = name
            }

            val photoID = contactInfo["photoID"]
            if (photoID != null) {
                np["phoneThumbnail"] = ContactsHelper.photoId64Encoded(context, photoID)
            }
        }
        if (phoneNumber != null) {
            np["phoneNumber"] = phoneNumber
        }

        device.sendPacket(np)
    }

    override val permissionExplanation: Int = R.string.telepathy_permission_explanation

    override fun onCreate(): Boolean {
        val filter = IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
        filter.priority = 500
        context.registerReceiver(receiver, filter)

        val refreshFilter = IntentFilter(Transaction.REFRESH)
        refreshFilter.priority = 500
        context.registerReceiver(messagesUpdateReceiver, refreshFilter, ContextCompat.RECEIVER_EXPORTED)

        context.contentResolver.registerContentObserver(SMSHelper.mConversationUri, true, messageObserver)

        // To see debug messages for Klinker library, uncomment the below line
        //Log.setDebug(true)
        mostRecentTimestampLock.lock()
        mostRecentTimestamp = getNewestMessageTimestamp(context)
        mostRecentTimestampLock.unlock()

        return true
    }

    override fun onDestroy() {
        context.unregisterReceiver(receiver)
        context.unregisterReceiver(messagesUpdateReceiver)
        context.contentResolver.unregisterContentObserver(messageObserver)
    }

    override val displayName: String
        get() = context.resources.getString(R.string.pref_plugin_telepathy)

    override val description: String
        get() = context.resources.getString(R.string.pref_plugin_telepathy_desc)

    override fun onPacketReceived(np: NetworkPacket): Boolean = when (np.type) {
        PACKET_TYPE_SMS_REQUEST_CONVERSATIONS -> {
            execute {
                this.handleRequestAllConversations(np)
            }
            true
        }
        PACKET_TYPE_SMS_REQUEST_CONVERSATION -> {
            execute {
                this.handleRequestSingleConversation(np)
            }
            true
        }
        PACKET_TYPE_SMS_REQUEST -> {
            val textMessage: String = np.getString("messageBody")
            val subID = np.getLong("subID", -1)

            val jsonAddressList = np.getJSONArray("addresses")
            val addressList = if (jsonAddressList == null) {
                // If jsonAddressList is null, then the SMS_REQUEST packet is most probably from the older version of the desktop app.
                listOf(SMSHelper.Address(context, np.getString("phoneNumber")))
            } else {
                jsonArrayToAddressList(context, jsonAddressList)
            }
            val attachedFiles: List<SMSHelper.Attachment> = jsonArrayToAttachmentsList(np.getJSONArray("attachments"))

            sendMessage(context, textMessage, attachedFiles, addressList.toMutableList(), subID.toInt())

            true
        }
        PACKET_TYPE_SMS_REQUEST_ATTACHMENT -> {
            val partID: Long = np.getLong("part_id")
            val uniqueIdentifier: String = np.getString("unique_identifier")

            val networkPacket: NetworkPacket? = partIdToMessageAttachmentPacket(context, partID, uniqueIdentifier, PACKET_TYPE_SMS_ATTACHMENT_FILE)

            if (networkPacket != null) {
                device.sendPacket(networkPacket)
            }

            true
        }
        else -> true
    }

    /**
     * Respond to a request for all conversations
     *
     * @param packet One packet of type [PACKET_TYPE_SMS_REQUEST_CONVERSATIONS] with the first message in all conversations that will be send
     */
    @WorkerThread
    private fun handleRequestAllConversations(packet: NetworkPacket): Boolean {
        haveMessagesBeenRequested = true
        val conversations: Iterator<SMSHelper.Message> = getConversations(this.context).iterator()

        while (conversations.hasNext()) {
            val message: SMSHelper.Message = conversations.next()
            val partialReply: NetworkPacket = constructBulkMessagePacket(setOf(message))
            device.sendPacket(partialReply)
        }

        return true
    }

    @WorkerThread
    private fun handleRequestSingleConversation(packet: NetworkPacket): Boolean {
        haveMessagesBeenRequested = true
        val threadID = ThreadID(packet.getLong("threadID"))

        val rangeStartTimestamp: Long = packet.getLong("rangeStartTimestamp", -1)
        var numberToGet: Long? = packet.getLong("numberToRequest", -1)

        if (numberToGet!! < 0) {
            numberToGet = null
        }

        val conversation = if (rangeStartTimestamp < 0) {
            getMessagesInThread(this.context, threadID, numberToGet)
        } else {
            getMessagesInRange(this.context, threadID, rangeStartTimestamp, numberToGet, true)
        }

        val reply: NetworkPacket = constructBulkMessagePacket(conversation)

        device.sendPacket(reply)

        return true
    }

    private fun isNumberBlocked(number: String?): Boolean {
        val sharedPref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val blockedNumbers: Array<String> =
            sharedPref.getString(KEY_PREF_BLOCKED_NUMBERS, "")!!.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        for (s: String in blockedNumbers) {
            if (PhoneNumberUtils.compare(number, s)) return true
        }

        return false
    }

    override fun hasSettings(): Boolean = true

    override fun getSettingsFragment(activity: Activity): PluginSettingsFragment? = PluginSettingsFragment.newInstance(pluginKey, R.xml.smsplugin_preferences)

    override val supportedPacketTypes: Array<String> = arrayOf(
            PACKET_TYPE_SMS_REQUEST,
            PACKET_TYPE_SMS_REQUEST_CONVERSATIONS,
            PACKET_TYPE_SMS_REQUEST_CONVERSATION,
            PACKET_TYPE_SMS_REQUEST_ATTACHMENT
        )

    override val outgoingPacketTypes: Array<String> = arrayOf(PACKET_TYPE_SMS_MESSAGE, PACKET_TYPE_SMS_ATTACHMENT_FILE)

    override val requiredPermissions: Array<String> = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS,  // READ_PHONE_STATE should be optional, since we can just query the user, but that
            // requires a GUI implementation for querying the user!
            Manifest.permission.READ_PHONE_STATE,
        )

    companion object {
        /**
         * Packet used to indicate a batch of messages has been pushed from the remote device
         *
         * The body should contain the key "messages" mapping to an array of messages
         *
         * For example:
         * ```
         * {
         *     "version": 2 // This is the second version of this packet type and version 1 packets
         *                  // (which did not carry this flag) are incompatible with the new format
         *     "messages" : [
         *         {
         *             "event"     : 1,               // 32-bit field containing a bitwise-or of event flags
         *                                            // See constants declared in SMSHelper.Message for define
         *                                            // values and explanations
         *             "body"      : "Hello",         // Text message body
         *             "addresses": <List<Address>>   // List of Address objects, one for each participant of the conversation
         *                                            // The user's Address is excluded so:
         *                                            // If this is a single-target message, there will only be one
         *                                            // Address (the other party)
         *                                            // If this is an incoming multi-target message, the first Address is the
         *                                            // sender and all other addresses are other parties to the conversation
         *                                            // If this is an outgoing multi-target message, the sender is implicit
         *                                            // (the user's phone number) and all Addresses are recipients
         *             "date"      : "1518846484880", // Timestamp of the message
         *             "type"      : "2",             // Compare with Android's Telephony.TextBasedSmsColumns.MESSAGE_TYPE_*
         *             "thread_id" : 132              // Thread to which the message belongs
         *             "read"      : true             // Boolean representing whether a message is read or unread
         *         },
         *         ...
         *     ]
         * }
         * ```
         *
         * The following optional fields of a message object may be defined
         * "sub_id": <int> // Android's subscriber ID, which is basically used to determine which SIM card the message
         *                 // belongs to. This is mostly useful when attempting to reply to an SMS with the correct
         *                 // SIM card using [PACKET_TYPE_SMS_REQUEST].
         *                 // If this value is not defined or if it does not match a valid subscriber_id known by
         *                 // Android, we will use whatever subscriber ID Android gives us as the default
         *
         * "attachments": <List<Attachment>>    // List of Attachment objects, one for each attached file in the message.
         *
         * An Attachment object looks like:
         * {
         *     "part_id": <long>                // part_id of the attachment used to read the file from MMS database
         *     "mime_type": <String>            // contains the mime type of the file (eg: image/jpg, video/mp4 etc.)
         *     "encoded_thumbnail": <String>    // Optional base64-encoded thumbnail preview of the content for types which support it
         *     "unique_identifier": <String>    // Unique name of the file
         * }
         *
         * An Address object looks like:
         * {
         *     "address": <String> // Address (phone number, email address, etc.) of this object
         * }
         */
        private const val PACKET_TYPE_SMS_MESSAGE: String = "kdeconnect.sms.messages"
        private const val SMS_MESSAGE_PACKET_VERSION: Int = 2 // We *send* packets of this version

        /**
         * Packet sent to request a message be sent
         *
         * The body should look like so:
         * {
         *     "version": 2,                     // The version of the packet being sent. Compare to SMS_REQUEST_PACKET_VERSION before attempting to handle.
         *     "sendSms": true,                  // (Depreciated, ignored) Old versions of the desktop app used to mix phone calls, SMS, etc. in the same packet type and used this field to differentiate.
         *     "phoneNumber": "542904563213",    // (Depreciated) Retained for backwards-compatibility. Old versions of the desktop app send a single phoneNumber. Use the Addresses field instead.
         *     "addresses": <List of Addresses>  // The one or many targets of this message
         *     "messageBody": "Hi mom!",         // Plain-text string to be sent as the body of the message (Optional if sending an attachment)
         *     "attachments": <List of Attached files>,
         *     "sub_id": 3859358340534           // Some magic number which tells Android which SIM card to use (Optional, if omitted, sends with the default SIM card)
         * }
         *
         * An AttachmentContainer object looks like:
         * {
         *     "fileName": <String>             // Name of the file
         *     "base64EncodedFile": <String>    // Base64 encoded file
         *     "mimeType": <String>             // File type (eg: image/jpg, video/mp4 etc.)
         * }
         */
        private const val PACKET_TYPE_SMS_REQUEST: String = "kdeconnect.sms.request"

        /**
         * Packet sent to request the most-recent message in each conversations on the device
         *
         * The request packet shall contain no body
         */
        private const val PACKET_TYPE_SMS_REQUEST_CONVERSATIONS: String = "kdeconnect.sms.request_conversations"

        /**
         * Packet sent to request all the messages in a particular conversation
         *
         * The following fields are available:
         * "threadID": <long>            // (Required) ThreadID to request
         * "rangeStartTimestamp": <long> // (Optional) Millisecond epoch timestamp indicating the start of the range from which to return messages
         * "numberToRequest": <long>     // (Optional) Number of messages to return, starting from rangeStartTimestamp.
         *                               // May return fewer than expected if there are not enough or more than expected if many
         *                               // messages have the same timestamp.
         */
        private const val PACKET_TYPE_SMS_REQUEST_CONVERSATION: String = "kdeconnect.sms.request_conversation"

        /**
         * Packet sent to request an attachment file in a particular message of a conversation
         *
         *
         * The body should look like so:
         * "part_id": <long>                // Part id of the attachment
         * "unique_identifier": <String>    // This unique_identifier should come from a previous message packet's attachment field
         */
        private const val PACKET_TYPE_SMS_REQUEST_ATTACHMENT: String = "kdeconnect.sms.request_attachment"

        /**
         * Packet used to send original attachment file from mms database to desktop
         *
         *
         * The following fields are available:
         * "filename": <String>     // Name of the attachment file in the database
         * "payload":               // Actual attachment file to be transferred
         */
        private const val PACKET_TYPE_SMS_ATTACHMENT_FILE: String = "kdeconnect.sms.attachment_file"

        private const val KEY_PREF_BLOCKED_NUMBERS: String = "telephony_blocked_numbers"

        /**
         * Construct a proper packet of [PACKET_TYPE_SMS_MESSAGE] from the passed messages
         *
         * @param messages Messages to include in the packet
         * @return NetworkPacket of type [PACKET_TYPE_SMS_MESSAGE]
         */
        private fun constructBulkMessagePacket(messages: Iterable<SMSHelper.Message>): NetworkPacket {
            val reply = NetworkPacket(PACKET_TYPE_SMS_MESSAGE)

            val body = JSONArray()

            for (message: SMSHelper.Message in messages) {
                try {
                    val json: JSONObject = message.toJSONObject()

                    body.put(json)
                } catch (e: JSONException) {
                    Log.e("Conversations", "Error serializing message", e)
                }
            }

            reply["messages"] = body
            reply["version"] = SMS_MESSAGE_PACKET_VERSION

            return reply
        }
    }
}
