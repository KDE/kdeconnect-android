/*
 * Copyright 2018 Simon Redman <simon@ergotech.com>
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

package org.kde.kdeconnect.Helpers;

import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Build;
import android.os.Looper;
import android.provider.Telephony;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

@SuppressLint("InlinedApi")
public class SMSHelper {

    /**
     * Get the base address for the SMS content
     * <p>
     * If we want to support API < 19, it seems to be possible to read via this query
     * This is highly undocumented and very likely varies between vendors but appears to work
     */
    private static Uri getSMSURIBad() {
        return Uri.parse("content://sms/");
    }

    /**
     * Get the base address for the SMS content
     * <p>
     * Use the new API way which should work on any phone API >= 19
     */
    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private static Uri getSMSURIGood() {
        return Telephony.Sms.CONTENT_URI;
    }

    private static Uri getSMSUri() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return getSMSURIGood();
        } else {
            return getSMSURIBad();
        }
    }

    private static Uri getMMSUri() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return Telephony.Mms.CONTENT_URI;
        } else {
            // Same as with getSMSUriBad, this is unsafe if the manufacturer did their own thing
            // before this was part of the API
            return Uri.parse("content://mms/");
        }
    }

    private static Uri getMMSPartUri() {
        // Android says we should have Telephony.Mms.Part.CONTENT_URI. Alas, we do not.
        return Uri.parse("content://mms/part/");
    }

    /**
     * Get the base address for all message conversations
     */
    private static Uri getConversationUri() {

        // Special case for Samsung
        // For some reason, Samsung devices do not support the regular SmsMms column.
        // However, according to https://stackoverflow.com/a/13640868/3723163, we can work around it this way.
        // By my understanding, "simple=true" means we can't support multi-target messages.
        // Go complain to Samsung about their annoying OS changes!
        if ("Samsung".equals(Build.MANUFACTURER)){
            Log.i("SMSHelper", "Samsung compatibility mode enabled. This may cause some features to not work properly.");
            return Uri.parse("content://mms-sms/conversations?simple=true");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return Telephony.MmsSms.CONTENT_CONVERSATIONS_URI;
        } else {
            // As with getSMSUriBad, this is potentially unsafe depending on whether a specific
            // manufacturer decided to do their own thing
            return Uri.parse("content://mms-sms/conversations");
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.FROYO)
    private static Uri getCompleteConversationsUri() {
        // This glorious - but completely undocumented - content URI gives us all messages, both MMS and SMS,
        // in all conversations
        // See https://stackoverflow.com/a/36439630/3723163
        return Uri.parse("content://mms-sms/complete-conversations");
    }

    /**
     * Column used to discriminate between SMS and MMS messages
     * Unfortunately, this column is not defined for Telephony.MmsSms.CONTENT_CONVERSATIONS_URI
     * (aka. content://mms-sms/conversations)
     * which gives us the first message in every conversation, but it *is* defined for
     * content://mms-sms/conversations/<threadID> which gives us the complete conversation matching
     * that threadID, so at least it's partially useful to us.
     */
    private static String getTransportTypeDiscriminatorColumn() {
        return Telephony.MmsSms.TYPE_DISCRIMINATOR_COLUMN;
    }

    /**
     * Get all the messages in a requested thread
     *
     * @param context  android.content.Context running the request
     * @param threadID Thread to look up
     * @return List of all messages in the thread
     */
    public static @NonNull List<Message> getMessagesInThread(
            @NonNull Context context,
            @NonNull ThreadID threadID
    ) {
        Uri uri = Uri.withAppendedPath(getConversationUri(), threadID.toString());

        return getMessages(uri, context, null, null, null, null);
    }

    /**
     * Get the newest sent or received message
     *
     * This might have some potential for race conditions if many messages are received in a short
     * timespan, but my target use-case is humans sending and receiving messages, so I don't think
     * it will be an issue
     *
     * @return null if no matching message is found, otherwise return a Message
     */
    public static @Nullable Message getNewestMessage(
            @NonNull Context context
    ) {
        List<Message> messages = getMessagesWithFilter(context, null, null, 1L);

        if (messages.size() > 1) {
            Log.w("SMSHelper", "getNewestMessage asked for one message but got " + messages.size());
        }
        if (messages.size() < 1) {
            return null;
        } else {
            return messages.get(0);
        }
    }

    /**
     * Gets messages which match the selection
     *
     * @param uri Uri indicating the messages database to read
     * @param context android.content.Context running the request.
     * @param selection Parameterizable filter to use with the ContentResolver query. May be null.
     * @param selectionArgs Parameters for selection. May be null.
     * @param sortOrder Sort ordering passed to Android's content resolver. May be null for unspecified
     * @param numberToGet Number of things to get from the result. Pass null to get all
     * @return Returns List<Message> of all messages in the return set, either in the order of sortOrder or in an unspecified order
     */
    private static @NonNull List<Message> getMessages(
            @NonNull Uri uri,
            @NonNull Context context,
            @Nullable String selection,
            @Nullable String[] selectionArgs,
            @Nullable String sortOrder,
            @Nullable Long numberToGet
    ) {
        List<Message> toReturn = new ArrayList<>();

        Set<String> allColumns = new HashSet<>();
        allColumns.addAll(Arrays.asList(Message.smsColumns));
        allColumns.addAll(Arrays.asList(Message.mmsColumns));

        if (uri != getConversationUri()) {
            // See https://issuetracker.google.com/issues/134592631
            allColumns.add(getTransportTypeDiscriminatorColumn());
        }

        String[] fetchColumns = {};
        fetchColumns = allColumns.toArray(fetchColumns);
        try (Cursor myCursor = context.getContentResolver().query(
                uri,
                fetchColumns,
                selection,
                selectionArgs,
                sortOrder)
        ) {
            if (myCursor != null && myCursor.moveToFirst()) {
                do {
                    int transportTypeColumn = myCursor.getColumnIndex(getTransportTypeDiscriminatorColumn());

                    TransportType transportType;
                    if (transportTypeColumn < 0) {
                        // The column didn't actually exist. See https://issuetracker.google.com/issues/134592631
                        // Try to determine using other information
                        int messageBoxColumn = myCursor.getColumnIndex(Telephony.Mms.MESSAGE_BOX);
                        // MessageBoxColumn is defined for MMS only
                        boolean messageBoxExists = !myCursor.isNull(messageBoxColumn);
                        if (messageBoxExists) {
                            transportType = TransportType.MMS;
                        } else {
                            // There is room here for me to have made an assumption and we'll guess wrong
                            // The penalty is the user will potentially get some garbled data, so that's not too bad.
                            transportType = TransportType.SMS;
                        }
                    } else {
                        String transportTypeString = myCursor.getString(transportTypeColumn);
                        if ("mms".equals(transportTypeString)) {
                            transportType = TransportType.MMS;
                        } else if ("sms".equals(transportTypeString)) {
                            transportType = TransportType.SMS;
                        } else {
                            Log.w("SMSHelper", "Skipping message with unknown TransportType: " + transportTypeString);
                            continue;
                        }
                    }

                    HashMap<String, String> messageInfo = new HashMap<>();
                    for (int columnIdx = 0; columnIdx < myCursor.getColumnCount(); columnIdx++) {
                        String colName = myCursor.getColumnName(columnIdx);
                        String body = myCursor.getString(columnIdx);
                        messageInfo.put(colName, body);
                    }

                    if (transportType == TransportType.SMS) {
                        parseSMS(context, messageInfo);
                    } else if (transportType == TransportType.MMS) {
                        parseMMS(context, messageInfo);
                    }

                    Message message = new Message(messageInfo);

                    toReturn.add(message);
                } while ((numberToGet == null || toReturn.size() != numberToGet) && myCursor.moveToNext());
            }
        } catch (SQLiteException e) {
            throw new MessageAccessException(fetchColumns, uri, e);
        }
        return toReturn;
    }

    /**
     * Get all messages matching the passed filter. See documentation for Android's ContentResolver
     *
     * @param context android.content.Context running the request
     * @param selection Parameterizable filter to use with the ContentResolver query. May be null.
     * @param selectionArgs Parameters for selection. May be null.
     * @param numberToGet Number of things to return. Pass null to get all
     * @return List of messages matching the filter, from newest to oldest
     */
    private static List<Message> getMessagesWithFilter(
            @NonNull Context context,
            @Nullable String selection,
            @Nullable String[] selectionArgs,
            @Nullable Long numberToGet
    ) {
        String sortOrder = Message.DATE + " DESC";

        return getMessages(getCompleteConversationsUri(), context, selection, selectionArgs, sortOrder, numberToGet);
    }

    /**
     * Get the last message from each conversation. Can use those thread_ids to look up more
     * messages in those conversations
     *
     * @param context android.content.Context running the request
     * @return Mapping of thread_id to the first message in each thread
     */
    public static Map<ThreadID, Message> getConversations(
            @NonNull Context context
    ) {
        Uri uri = SMSHelper.getConversationUri();

        List<Message> unthreadedMessages = getMessages(uri, context, null, null, null, null);

        Map<ThreadID, Message> toReturn = new HashMap<>();

        for (Message message : unthreadedMessages) {
            ThreadID tID = message.threadID;

            if (toReturn.containsKey(tID)) {
                Log.w("SMSHelper", "getConversations got two messages for the same ThreadID: " + tID);
            }

            toReturn.put(tID, message);
        }
        return toReturn;
    }

    private static void addEventFlag(
            @NonNull Map<String, String> messageInfo,
            @NonNull int eventFlag
    ) {
        int oldEvent = 0; //Default value
        String oldEventString = messageInfo.get(Message.EVENT);
        if (oldEventString != null) {
            oldEvent = Integer.parseInt(oldEventString);
        }
        messageInfo.put(Message.EVENT, Integer.toString(oldEvent | eventFlag));
    }

    /**
     * Do any parsing of an SMS message which still needs to be done
     */
    private static void parseSMS(
            @NonNull Context context,
            @NonNull Map<String, String> messageInfo
    ) {
        addEventFlag(messageInfo, Message.EVENT_TEXT_MESSAGE);
    }

    /**
     * Parse all parts of the MMS message into the messageInfo format
     * Original implementation from https://stackoverflow.com/a/6446831/3723163
     */
    private static void parseMMS(
            @NonNull Context context,
            @NonNull Map<String, String> messageInfo
    ) {
        addEventFlag(messageInfo, Message.EVENT_UNKNOWN);

        String[] columns = {
                Telephony.Mms.Part._ID,          // The content ID of this part
                Telephony.Mms.Part._DATA,        // The location in the filesystem of the data
                Telephony.Mms.Part.CONTENT_TYPE, // The mime type of the data
                Telephony.Mms.Part.TEXT,         // The plain text body of this MMS
                Telephony.Mms.Part.CHARSET,      // Charset of the plain text body
        };

        String mmsID = messageInfo.get(Message.U_ID);
        String selection = Telephony.Mms.Part.MSG_ID + " = ?";
        String[] selectionArgs = {mmsID};

        // Get text body and attachments of the message
        try (Cursor cursor = context.getContentResolver().query(
                getMMSPartUri(),
                columns,
                selection,
                selectionArgs,
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                int partIDColumn = cursor.getColumnIndexOrThrow(Telephony.Mms.Part._ID);
                int contentTypeColumn = cursor.getColumnIndexOrThrow(Telephony.Mms.Part.CONTENT_TYPE);
                int dataColumn = cursor.getColumnIndexOrThrow(Telephony.Mms.Part._DATA);
                int textColumn = cursor.getColumnIndexOrThrow(Telephony.Mms.Part.TEXT);
                // TODO: Parse charset (As usual, it is skimpily documented) (Possibly refer to MMS spec)

                do {
                    Long partID = cursor.getLong(partIDColumn);
                    String contentType = cursor.getString(contentTypeColumn);
                    String data = cursor.getString(dataColumn);
                    if ("text/plain".equals(contentType)) {
                        String body;
                        if (data != null) {
                            // data != null means the data is on disk. Go get it.
                            body = getMmsText(context, partID);
                        } else {
                            body = cursor.getString(textColumn);
                        }
                        messageInfo.put(Message.BODY, body);
                        addEventFlag(messageInfo, Message.EVENT_TEXT_MESSAGE);
                    } //TODO: Parse more content types (photos and other attachments) here

                } while (cursor.moveToNext());
            }
        }

        // Determine whether the message was in- our out- bound
        long messageBox = Long.parseLong(messageInfo.get(Telephony.Mms.MESSAGE_BOX));
        if (messageBox == Telephony.Mms.MESSAGE_BOX_INBOX) {
            messageInfo.put(Message.TYPE, Integer.toString(Telephony.Sms.MESSAGE_TYPE_INBOX));
        } else if (messageBox == Telephony.Mms.MESSAGE_BOX_SENT) {
            messageInfo.put(Message.TYPE, Integer.toString(Telephony.Sms.MESSAGE_TYPE_SENT));
        } else {
            // As an undocumented feature, it looks like the values of Mms.MESSAGE_BOX_*
            // are the same as Sms.MESSAGE_TYPE_* of the same type. So by default let's just use
            // the value we've got.
            // This includes things like drafts, which are a far-distant plan to support
            messageInfo.put(Message.TYPE, messageInfo.get(Telephony.Mms.MESSAGE_BOX));
        }

        // Get address(es) of the message
        List<String> addresses = getMmsAddresses(context, Long.parseLong(mmsID));
        // It looks like addresses[0] is always the sender of the message and
        // following addresses are recipient(s)
        // This usually means the addresses list is at least 2 long, but there are cases (special
        // telco service messages) where it is not (only 1 long in that case, just the "sender")

        // The address field which will get written to the message.
        // Remember that this is always the address of the other side of the conversation
        String address = "";

        if (addresses.size() > 2) {
            // TODO: Collect addresses for multi-target MMS
            // Probably we will need to figure out the user's address at this point and strip it out of the list
            addEventFlag(messageInfo, Message.EVENT_MULTI_TARGET);
        } else {
            if (messageBox == Telephony.Mms.MESSAGE_BOX_INBOX) {
                address = addresses.get(0);
            } else if (messageBox == Telephony.Mms.MESSAGE_BOX_SENT) {
                address = addresses.get(1);
            } else {
                Log.w("SMSHelper", "Unknown message type " + messageBox + " while parsing addresses.");
                // Not much smart to do here. Just leave as default.
            }
        }
        messageInfo.put(Message.ADDRESS, address);

        // Canonicalize the date field
        // SMS uses epoch milliseconds, MMS uses epoch seconds. Standardize on milliseconds.
        long rawDate = Long.parseLong(messageInfo.get(Message.DATE));
        messageInfo.put(Message.DATE, Long.toString(rawDate * 1000));
    }

    /**
     * Get the address(es) of an MMS message
     * Original implementation from https://stackoverflow.com/a/6446831/3723163
     */
    private static @NonNull List<String> getMmsAddresses(
            @NonNull Context context,
            @NonNull Long messageID
    ) {
        Uri uri = ContentUris.appendId(getMMSUri().buildUpon(), messageID).appendPath("addr").build();

        String[] columns = {
                Telephony.Mms.Addr.MSG_ID,   // ID of the message for which we are fetching addresses
                Telephony.Mms.Addr.ADDRESS,  // Address of this part
                Telephony.Mms.Addr.CHARSET,  // Charset of the returned address (where relevant) //TODO: Handle
        };

        String selection = Telephony.Mms.Addr.MSG_ID + " = ?";
        String[] selectionArgs = {messageID.toString()};

        List<String> addresses = new ArrayList<>();

        try (Cursor addrCursor = context.getContentResolver().query(
                uri,
                columns,
                selection,
                selectionArgs,
                null
        )) {
            if (addrCursor != null && addrCursor.moveToFirst()) {
                int addressIndex = addrCursor.getColumnIndex(Telephony.Mms.Addr.ADDRESS);

                do {
                    String address = addrCursor.getString(addressIndex);
                    addresses.add(address);
                } while (addrCursor.moveToNext());
            }
        }
        return addresses;
    }

    /**
     * Get a text part of an MMS message
     * Original implementation from https://stackoverflow.com/a/6446831/3723163
     */
    private static String getMmsText(
            @NonNull Context context,
            @NonNull Long id
    ) {
        Uri partURI = ContentUris.withAppendedId(getMMSPartUri(), id);
        StringBuilder body = new StringBuilder();
        try (InputStream is = context.getContentResolver().openInputStream(partURI)) {
            if (is != null) {
                InputStreamReader isr = new InputStreamReader(is, "UTF-8");
                BufferedReader reader = new BufferedReader(isr);
                String temp = reader.readLine();
                while (temp != null) {
                    body.append(temp);
                    temp = reader.readLine();
                }
            }
        } catch (IOException e) {
            throw new SMSHelper.MessageAccessException(partURI, e);
        }
        return body.toString();
    }

    /**
     * Register a ContentObserver for the Messages database
     *
     * @param observer ContentObserver to alert on Message changes
     */
    public static void registerObserver(
            @NonNull ContentObserver observer,
            @NonNull Context context
    ) {
        context.getContentResolver().registerContentObserver(
                SMSHelper.getConversationUri(),
                true,
                observer
        );
    }

    /**
     * Represent an ID used to uniquely identify a message thread
     */
    public static class ThreadID {
        final Long threadID;
        static final String lookupColumn = Telephony.Sms.THREAD_ID;

        public ThreadID(Long threadID) {
            this.threadID = threadID;
        }

        @NonNull
        public String toString() {
            return threadID.toString();
        }

        @Override
        public int hashCode() {
            return threadID.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            return other.getClass().isAssignableFrom(ThreadID.class) && ((ThreadID) other).threadID.equals(this.threadID);
        }
    }

    /**
     * Indicate that some error has occurred while reading a message.
     * More useful for logging than catching and handling
     */
    public static class MessageAccessException extends RuntimeException {
        MessageAccessException(Uri uri, Throwable cause) {
            super("Error getting messages from " + uri.toString(), cause);
        }

        MessageAccessException(String[] availableColumns, Uri uri, Throwable cause) {
            super("Error getting messages from " + uri.toString() + " . Available columns were: " + Arrays.toString(availableColumns), cause);
        }
    }

    /**
     * Represent all known transport types
     */
    public enum TransportType {
        SMS,
        MMS,
        // Maybe in the future there will be more TransportType, but for now these are all I know about
    }

    /**
     * Represent a message and all of its interesting data columns
     */
    public static class Message {

        final String address;
        final String body;
        public final long date;
        final int type;
        final int read;
        final ThreadID threadID; // ThreadID is *int* for SMS messages but *long* for MMS
        final long uID;
        final int event;
        final int subscriptionID;

        /**
         * Named constants which are used to construct a Message
         * See: https://developer.android.com/reference/android/provider/Telephony.TextBasedSmsColumns.html for full documentation
         */
        static final String ADDRESS = Telephony.Sms.ADDRESS;   // Contact information (phone number or otherwise) of the remote
        static final String BODY = Telephony.Sms.BODY;         // Body of the message
        static final String DATE = Telephony.Sms.DATE;         // Date (Unix epoch millis) associated with the message
        static final String TYPE = Telephony.Sms.TYPE;         // Compare with Telephony.TextBasedSmsColumns.MESSAGE_TYPE_*
        static final String READ = Telephony.Sms.READ;         // Whether we have received a read report for this message (int)
        static final String THREAD_ID = ThreadID.lookupColumn; // Magic number which binds (message) threads
        static final String U_ID = Telephony.Sms._ID;          // Something which uniquely identifies this message
        static final String EVENT = "event";
        static final String SUBSCRIPTION_ID = Telephony.Sms.SUBSCRIPTION_ID; // An ID which appears to identify a SIM card

        /**
         * Event flags
         * A message should have a bitwise-or of event flags before delivering the packet
         * Any events not supported by the receiving device should be ignored
         */
        public static final int EVENT_UNKNOWN      = 0x0; // The message was of some type we did not understand
        public static final int EVENT_TEXT_MESSAGE = 0x1; // This message has a "body" field which contains
                                                          // pure, human-readable text
        public static final int EVENT_MULTI_TARGET = 0x2; // Indicates that this message has multiple recipients

        /**
         * Define the columns which are to be extracted from the Android SMS database
         */
        static final String[] smsColumns = new String[]{
                Message.ADDRESS,
                Message.BODY,
                Message.DATE,
                Message.TYPE,
                Message.READ,
                Message.THREAD_ID,
                Message.U_ID,
                Message.SUBSCRIPTION_ID,
        };

        static final String[] mmsColumns = new String[]{
                Message.U_ID,
                Message.THREAD_ID,
                Message.DATE,
                Message.READ,
                Telephony.Mms.TEXT_ONLY,
                Telephony.Mms.MESSAGE_BOX, // Compare with Telephony.BaseMmsColumns.MESSAGE_BOX_*
        };

        Message(final HashMap<String, String> messageInfo) {
            address = messageInfo.get(Message.ADDRESS);
            body = messageInfo.get(Message.BODY);
            date = Long.parseLong(messageInfo.get(Message.DATE));
            if (messageInfo.get(Message.TYPE) == null)
            {
                // To be honest, I have no idea why this happens. The docs say the TYPE field is mandatory.
                Log.w("SMSHelper", "Encountered undefined message type");
                type = -1;
                // Proceed anyway, maybe this is not an important problem.
            } else {
                type = Integer.parseInt(messageInfo.get(Message.TYPE));
            }
            read = Integer.parseInt(messageInfo.get(Message.READ));
            threadID = new ThreadID(Long.parseLong(messageInfo.get(Message.THREAD_ID)));
            uID = Integer.parseInt(messageInfo.get(Message.U_ID));
            subscriptionID = Integer.parseInt(messageInfo.get(Message.SUBSCRIPTION_ID));
            event = Integer.parseInt(messageInfo.get(Message.EVENT));
        }

        public JSONObject toJSONObject() throws JSONException {
            JSONObject json = new JSONObject();

            json.put(Message.ADDRESS, address);
            json.put(Message.BODY, body);
            json.put(Message.DATE, date);
            json.put(Message.TYPE, type);
            json.put(Message.READ, read);
            json.put(Message.THREAD_ID, threadID);
            json.put(Message.U_ID, uID);
            json.put(Message.SUBSCRIPTION_ID, subscriptionID);
            json.put(Message.EVENT, event);

            return json;
        }

        @Override
        public String toString() {
            return body;
        }
    }

    /**
     * If anyone wants to subscribe to changes in the messages database, they will need a thread
     * to handle callbacks on
     * This singleton conveniently provides such a thread, accessed and used via its Looper object
     */
    public static class MessageLooper extends Thread {
        private static MessageLooper singleton = null;
        private static Looper looper = null;

        private static final Lock looperReadyLock = new ReentrantLock();
        private static final Condition looperReady = looperReadyLock.newCondition();

        private MessageLooper() {
            setName("MessageHelperLooper");
        }

        /**
         * Get the Looper object associated with this thread
         *
         * If the Looper has not been prepared, it is prepared as part of this method call.
         * Since this means a thread has to be spawned, this method might block until that thread is
         * ready to serve requests
         */
        public static Looper getLooper() {
            if (singleton == null) {
                looperReadyLock.lock();
                try {
                    singleton = new MessageLooper();
                    singleton.start();
                    while (looper == null) {
                        // Block until the looper is ready
                        looperReady.await();
                    }
                } catch (InterruptedException e) {
                    // I don't know when this would happen
                    Log.e("SMSHelper", "Interrupted while waiting for Looper", e);
                    return null;
                } finally {
                    looperReadyLock.unlock();
                }
            }

            return looper;
        }

        public void run() {
            looperReadyLock.lock();
            try {
                Looper.prepare();

                looper = Looper.myLooper();
                looperReady.signalAll();
            } finally {
                looperReadyLock.unlock();
            }

            Looper.loop();
        }
    }
}
