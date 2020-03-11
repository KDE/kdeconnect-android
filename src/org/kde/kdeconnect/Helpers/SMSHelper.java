/*
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
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

@SuppressLint("InlinedApi")
public class SMSHelper {

    /**
     * Get a URI for querying SMS messages
     */
    private static Uri getSMSUri() {
        // This constant was introduces with API 19 (KitKat)
        // The value it represents was used in older Android versions so it *should* work but
        // might vary between vendors.
        return Telephony.Sms.CONTENT_URI;
    }

    private static Uri getMMSUri() {
        // Same warning as getSMSUri: This constant was introduced with API 19
        return Telephony.Mms.CONTENT_URI;
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
        if ("Samsung".equalsIgnoreCase(Build.MANUFACTURER)) {
            Log.i("SMSHelper", "This appears to be a Samsung device. This may cause some features to not work properly.");
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
     * @param numberToGet Number of messages to return. Pass null for "all"
     * @return List of all messages in the thread
     */
    public static @NonNull List<Message> getMessagesInThread(
            @NonNull Context context,
            @NonNull ThreadID threadID,
            @Nullable Long numberToGet
    ) {
        Uri uri = Uri.withAppendedPath(getConversationUri(), threadID.toString());

        return getMessages(uri, context, null, null, null, numberToGet);
    }

    /**
     * Get some messages in the given thread which have timestamp equal to or after the given timestamp
     *
     * @param context  android.content.Context running the request
     * @param threadID Thread to look up
     * @param startTimestamp Beginning of the range to return
     * @param numberToGet Number of messages to return. Pass null for "all"
     * @return Some messages in the requested conversation
     */
    @SuppressLint("NewApi")
    public static @NonNull List<Message> getMessagesInRange(
            @NonNull Context context,
            @NonNull ThreadID threadID,
            @NonNull Long startTimestamp,
            @Nullable Long numberToGet
    ) {
        // The stickiness with this is that Android's MMS database has its timestamp in epoch *seconds*
        // while the SMS database uses epoch *milliseconds*.
        // I can think of no way around this other than manually querying each one with a different
        // "WHERE" statement.
        Uri smsUri = getSMSUri();
        Uri mmsUri = getMMSUri();

        List<String> allSmsColumns = new ArrayList<>(Arrays.asList(Message.smsColumns));
        List<String> allMmsColumns = new ArrayList<>(Arrays.asList(Message.mmsColumns));

        if (getSubscriptionIdSupport(smsUri, context)) {
            allSmsColumns.addAll(Arrays.asList(Message.multiSIMColumns));
        }

        if (getSubscriptionIdSupport(mmsUri, context)) {
            allMmsColumns.addAll(Arrays.asList(Message.multiSIMColumns));
        }

        String selection = Message.THREAD_ID + " = ? AND ? >= " + Message.DATE;

        String[] smsSelectionArgs = new String[] { threadID.toString(), startTimestamp.toString() };
        String[] mmsSelectionArgs = new String[] { threadID.toString(), Long.toString(startTimestamp / 1000) };

        String sortOrder = Message.DATE + " DESC";

        List<Message> allMessages = getMessages(smsUri, context, allSmsColumns, selection, smsSelectionArgs, sortOrder, numberToGet);
        allMessages.addAll(getMessages(mmsUri, context, allMmsColumns, selection, mmsSelectionArgs, sortOrder, numberToGet));

        // Need to now only return the requested number of messages:
        // Suppose we were requested to return N values and suppose a user sends only one MMS per
        // week and N SMS per day. We have requested the same N for each, so if we just return everything
        // we would return some very old MMS messages which would be very confusing.
        SortedMap<Long, Collection<Message>> sortedMessages = new TreeMap<>((lhs, rhs) -> Long.compare(rhs, lhs));
        for (Message message : allMessages) {
            Collection<Message> existingMessages = sortedMessages.getOrDefault(message.date, new ArrayList<>());
            assert existingMessages != null;
            existingMessages.add(message);
            sortedMessages.put(message.date, existingMessages);
        }

        List<Message> toReturn = new ArrayList<>(allMessages.size());

        for (Collection<Message> messages : sortedMessages.values()) {
            toReturn.addAll(messages);
            if (numberToGet != null && toReturn.size() >= numberToGet) {
                break;
            }
        }

        return toReturn;
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
     * Checks if device supports `Telephony.Sms.SUBSCRIPTION_ID` column in database with URI `uri`
     *
     * @param uri Uri indicating the messages database to check
     * @param context android.content.Context running the request.
     */
    private static boolean getSubscriptionIdSupport(@NonNull Uri uri, @NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return false;
        }
        // Some (Xiaomi) devices running >= Android Lollipop (SDK 22+) don't support
        // `Telephony.Sms.SUBSCRIPTION_ID`, so additional check is needed.
        // It may be possible to use "sim_id" instead of "sub_id" on these devices
        // https://stackoverflow.com/a/38152331/6509200
        try (Cursor availableColumnsCursor = context.getContentResolver().query(
                uri,
                new String[] {Telephony.Sms.SUBSCRIPTION_ID},
                null,
                null,
                null)
        ) {
            if (availableColumnsCursor != null) {
                return true; // if we got the cursor, the query shouldn't fail
            }
            return false;
        } catch (SQLiteException e) {
            // With uri content://mms-sms/conversations this query throws an exception if sub_id is not supported
            String errMessage = e.getMessage();
            if (errMessage != null && errMessage.contains(Telephony.Sms.SUBSCRIPTION_ID)) {
                return false;
            }
            return true;
        }
    }

    /**
     * Gets messages which match the selection
     *
     * @param uri Uri indicating the messages database to read
     * @param context android.content.Context running the request.
     * @param fetchColumns List of columns to fetch
     * @param selection Parameterizable filter to use with the ContentResolver query. May be null.
     * @param selectionArgs Parameters for selection. May be null.
     * @param sortOrder Sort ordering passed to Android's content resolver. May be null for unspecified
     * @param numberToGet Number of things to get from the result. Pass null to get all
     * @return Returns List<Message> of all messages in the return set, either in the order of sortOrder or in an unspecified order
     */
    private static @NonNull List<Message> getMessages(
            @NonNull Uri uri,
            @NonNull Context context,
            @NonNull Collection<String> fetchColumns,
            @Nullable String selection,
            @Nullable String[] selectionArgs,
            @Nullable String sortOrder,
            @Nullable Long numberToGet
    ) {
        List<Message> toReturn = new ArrayList<>();

        // Get all the active phone numbers so we can filter the user out of the list of targets
        // of any MMSes
        List<String> userPhoneNumbers = TelephonyHelper.getAllPhoneNumbers(context);

        try (Cursor myCursor = context.getContentResolver().query(
                uri,
                fetchColumns.toArray(new String[]{}),
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

                    try {
                        Message message;
                        if (transportType == TransportType.SMS) {
                            message = parseSMS(context, messageInfo);
                        } else if (transportType == TransportType.MMS) {
                            message = parseMMS(context, messageInfo, userPhoneNumbers);
                        } else {
                            // As we can see, all possible transportTypes are covered, but the compiler
                            // requires this line anyway
                            throw new UnsupportedOperationException("Unknown TransportType encountered");
                        }

                        toReturn.add(message);
                    } catch (Exception e) {
                        // Swallow exceptions in case we get an error reading one message so that we
                        // might be able to read some of them
                        Log.e("SMSHelper", "Got an error reading a message of type " + transportType, e);
                    }
                } while ((numberToGet == null || toReturn.size() < numberToGet) && myCursor.moveToNext());
            }
        } catch (SQLiteException e) {
            String[] unfilteredColumns = {};
            try (Cursor unfilteredColumnsCursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (unfilteredColumnsCursor != null) {
                    unfilteredColumns = unfilteredColumnsCursor.getColumnNames();
                }
            }
            if (unfilteredColumns.length == 0) {
                throw new MessageAccessException(uri, e);
            } else {
                throw new MessageAccessException(unfilteredColumns, uri, e);
            }
        }

        return toReturn;
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
    @SuppressLint("NewApi")
    private static @NonNull List<Message> getMessages(
            @NonNull Uri uri,
            @NonNull Context context,
            @Nullable String selection,
            @Nullable String[] selectionArgs,
            @Nullable String sortOrder,
            @Nullable Long numberToGet
    ) {
        Set<String> allColumns = new HashSet<>();
        allColumns.addAll(Arrays.asList(Message.smsColumns));
        allColumns.addAll(Arrays.asList(Message.mmsColumns));
        if (getSubscriptionIdSupport(uri, context)) {
            allColumns.addAll(Arrays.asList(Message.multiSIMColumns));
        }

        if (!uri.equals(getConversationUri())) {
            // See https://issuetracker.google.com/issues/134592631
            allColumns.add(getTransportTypeDiscriminatorColumn());
        }

        return getMessages(uri, context, allColumns, selection, selectionArgs, sortOrder, numberToGet);
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

    private static int addEventFlag(
            int oldEvent,
            int eventFlag
    ) {
        return oldEvent | eventFlag;
    }

    /**
     * Parse all parts of an SMS into a Message
     */
    private static @NonNull Message parseSMS(
            @NonNull Context context,
            @NonNull Map<String, String> messageInfo
    ) {
        int event = Message.EVENT_UNKNOWN;
        event = addEventFlag(event, Message.EVENT_TEXT_MESSAGE);

        @NonNull List<Address> address = Collections.singletonList(new Address(messageInfo.get(Telephony.Sms.ADDRESS)));
        @NonNull String body = messageInfo.get(Message.BODY);
        long date = Long.parseLong(messageInfo.get(Message.DATE));
        int type = Integer.parseInt(messageInfo.get(Message.TYPE));
        int read = Integer.parseInt(messageInfo.get(Message.READ));
        @NonNull ThreadID threadID = new ThreadID(Long.parseLong(messageInfo.get(Message.THREAD_ID)));
        long uID = Long.parseLong(messageInfo.get(Message.U_ID));
        int subscriptionID = messageInfo.get(Message.SUBSCRIPTION_ID) != null ?
                Integer.parseInt(messageInfo.get(Message.SUBSCRIPTION_ID)) : 0;

        return new Message(
                address,
                body,
                date,
                type,
                read,
                threadID,
                uID,
                event,
                subscriptionID
        );
    }

    /**
     * Parse all parts of the MMS message into a message
     * Original implementation from https://stackoverflow.com/a/6446831/3723163
     */
    private static @NonNull Message parseMMS(
            @NonNull Context context,
            @NonNull Map<String, String> messageInfo,
            @NonNull List<String> userPhoneNumbers
    ) {
        int event = Message.EVENT_UNKNOWN;

        @NonNull String body = "";
        long date;
        int type;
        int read = Integer.parseInt(messageInfo.get(Message.READ));
        @NonNull ThreadID threadID = new ThreadID(Long.parseLong(messageInfo.get(Message.THREAD_ID)));
        long uID = Long.parseLong(messageInfo.get(Message.U_ID));
        int subscriptionID = messageInfo.get(Message.SUBSCRIPTION_ID) != null ?
                Integer.parseInt(messageInfo.get(Message.SUBSCRIPTION_ID)) : 0;

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
                        if (data != null) {
                            // data != null means the data is on disk. Go get it.
                            body = getMmsText(context, partID);
                        } else {
                            body = cursor.getString(textColumn);
                        }
                        event = addEventFlag(event, Message.EVENT_TEXT_MESSAGE);
                    } //TODO: Parse more content types (photos and other attachments) here

                } while (cursor.moveToNext());
            }
        }

        // Determine whether the message was in- our out- bound
        long messageBox = Long.parseLong(messageInfo.get(Telephony.Mms.MESSAGE_BOX));
        if (messageBox == Telephony.Mms.MESSAGE_BOX_INBOX) {
            type = Telephony.Sms.MESSAGE_TYPE_INBOX;
        } else if (messageBox == Telephony.Mms.MESSAGE_BOX_SENT) {
            type = Telephony.Sms.MESSAGE_TYPE_SENT;
        } else {
            // As an undocumented feature, it looks like the values of Mms.MESSAGE_BOX_*
            // are the same as Sms.MESSAGE_TYPE_* of the same type. So by default let's just use
            // the value we've got.
            // This includes things like drafts, which are a far-distant plan to support
            type = Integer.parseInt(messageInfo.get(Telephony.Mms.MESSAGE_BOX));
        }

        // Get address(es) of the message
        List<Address> addresses = getMmsAddresses(context, Long.parseLong(mmsID), userPhoneNumbers);
        // It looks like addresses[0] is always the sender of the message and
        // following addresses are recipient(s)
        // This usually means the addresses list is at least 2 long, but there are cases (special
        // telco service messages) where it is not (only 1 long in that case, just the "sender")

        if (addresses.size() >= 2) {
            event = addEventFlag(event, Message.EVENT_MULTI_TARGET);
        }

        // Canonicalize the date field
        // SMS uses epoch milliseconds, MMS uses epoch seconds. Standardize on milliseconds.
        long rawDate = Long.parseLong(messageInfo.get(Message.DATE));
        date = rawDate * 1000;

        return new Message(
                addresses,
                body,
                date,
                type,
                read,
                threadID,
                uID,
                event,
                subscriptionID
        );
    }

    /**
     * Get the address(es) of an MMS message
     * Original implementation from https://stackoverflow.com/a/6446831/3723163
     *
     * The message at the first position of the list should be the sender of the message
     *
     * @param messageID ID of this message in the MMS database for looking up the remaining info
     * @param userPhoneNumbers List of phone numbers which should be removed from the list of addresses
     */
    private static @NonNull List<Address> getMmsAddresses(
            @NonNull Context context,
            @NonNull Long messageID,
            @NonNull List<String> userPhoneNumbers
    ) {
        Uri uri = ContentUris.appendId(getMMSUri().buildUpon(), messageID).appendPath("addr").build();

        String[] columns = {
                Telephony.Mms.Addr.MSG_ID,   // ID of the message for which we are fetching addresses
                Telephony.Mms.Addr.ADDRESS,  // Address of this part
                Telephony.Mms.Addr.CHARSET,  // Charset of the returned address (where relevant) //TODO: Handle
        };

        String selection = Telephony.Mms.Addr.MSG_ID + " = ?";
        String[] selectionArgs = {messageID.toString()};

        // Keep an ordered set rather than a list because Android sometimes throws duplicates at us
        Set<Address> addresses = new LinkedHashSet<>();

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
                    addresses.add(new Address(address));
                } while (addrCursor.moveToNext());
            }
        }

        // Prune the user's phone numbers from the list of addresses
        List<Address> prunedAddresses = new ArrayList<>(addresses);
        prunedAddresses.removeAll(userPhoneNumbers);

        if (prunedAddresses.size() == 0) {
            // If it turns out that we have pruned away everything, prune away nothing
            // (The user is allowed to talk to themself)

            // Remove duplicate entries, since the user knows if a conversation says "Me" on it,
            // it is the conversation with themself. (We don't need to say "Me, Me")
            // This leaves the multi-sim case alone, so the returned address list might say
            // "Me1, Me2"

            prunedAddresses = new ArrayList<>(addresses.size()); // The old one was empty too, but just to be clear...
            for (Address address : addresses) {
                if (!prunedAddresses.contains(address)) {
                    prunedAddresses.add(address);
                }
            }
        }
        return prunedAddresses;
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

    public static class Address {
        final String address;

        /**
         * Address object field names
         */
        public static final String ADDRESS = "address";

        public Address(String address) {
            this.address = address;
        }

        public JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();

            json.put(Address.ADDRESS, this.address);

            return json;
        }

        @Override
        public String toString() {
            return address;
        }

        @Override
        public boolean equals(Object other){
            if (other == null) {
                return false;
            }
            if (other.getClass().isAssignableFrom(Address.class)) {
                return PhoneNumberUtils.compare(this.address, ((Address)other).address);
            }
            if (other.getClass().isAssignableFrom(String.class)) {
                return PhoneNumberUtils.compare(this.address, (String)other);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return this.address.hashCode();
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

        public final List<Address> addresses;
        public final String body;
        public final long date;
        public final int type;
        public final int read;
        public final ThreadID threadID;
        public final long uID;
        public final int event;
        public final int subscriptionID;

        /**
         * Named constants which are used to construct a Message
         * See: https://developer.android.com/reference/android/provider/Telephony.TextBasedSmsColumns.html for full documentation
         */
        static final String ADDRESSES = "addresses";   // Contact information (phone number or otherwise) of the remote
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
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ,
                Telephony.Sms.THREAD_ID,
                Message.U_ID,
        };

        static final String[] mmsColumns = new String[]{
                Message.U_ID,
                Telephony.Mms.THREAD_ID,
                Telephony.Mms.DATE,
                Telephony.Mms.READ,
                Telephony.Mms.TEXT_ONLY,
                Telephony.Mms.MESSAGE_BOX, // Compare with Telephony.BaseMmsColumns.MESSAGE_BOX_*
        };

        /**
         * These columns are for determining what SIM card the message belongs to, and therefore
         * are only defined on Android versions with multi-sim capabilities
         */
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
        static final String[] multiSIMColumns = new String[]{
                Telephony.Sms.SUBSCRIPTION_ID,
        };

        Message(
                @NonNull List<Address> addresses,
                @NonNull String body,
                long date,
                @NonNull Integer type,
                int read,
                @NonNull ThreadID threadID,
                long uID,
                int event,
                int subscriptionID
        ) {
            this.addresses = addresses;
            this.body = body;
            this.date = date;
            if (type == null)
            {
                // To be honest, I have no idea why this happens. The docs say the TYPE field is mandatory.
                Log.w("SMSHelper", "Encountered undefined message type");
                this.type = -1;
                // Proceed anyway, maybe this is not an important problem.
            } else {
                this.type = type;
            }
            this.read = read;
            this.threadID = threadID;
            this.uID = uID;
            this.subscriptionID = subscriptionID;
            this.event = event;
        }

        public JSONObject toJSONObject() throws JSONException {
            JSONObject json = new JSONObject();

            JSONArray jsonAddresses = new JSONArray();
            for (Address address : this.addresses) {
                jsonAddresses.put(address.toJson());
            }

            json.put(Message.ADDRESSES, jsonAddresses);
            json.put(Message.BODY, body);
            json.put(Message.DATE, date);
            json.put(Message.TYPE, type);
            json.put(Message.READ, read);
            json.put(Message.THREAD_ID, threadID.threadID);
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
