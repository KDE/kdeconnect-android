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

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Looper;
import android.provider.Telephony;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import androidx.annotation.RequiresApi;

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
        // TODO: Why not use Telephony.MmsSms.CONTENT_URI?
        return Telephony.Sms.CONTENT_URI;
    }

    private static Uri getSMSUri() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return getSMSURIGood();
        } else {
            return getSMSURIBad();
        }
    }

    /**
     * Get the base address for all message conversations
     */
    private static Uri getConversationUri() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return Telephony.MmsSms.CONTENT_CONVERSATIONS_URI;
        } else {
            // As with getSMSUriBad, this is potentially unsafe depending on whether a specific
            // manufacturer decided to do their own thing
            return Uri.parse("content://mms-sms/conversations");
        }
    }

    /**
     * Get all the messages in a requested thread
     *
     * @param context  android.content.Context running the request
     * @param threadID Thread to look up
     * @return List of all messages in the thread
     */
    public static List<Message> getMessagesInThread(Context context, ThreadID threadID) {
        final String selection = ThreadID.lookupColumn + " == ?";
        final String[] selectionArgs = new String[] { threadID.toString() };

        return getMessagesWithFilter(context, selection, selectionArgs);
    }

    /**
     * Get all messages which have a timestamp after the requested timestamp
     *
     * @param timestamp epoch in millis matching the timestamp to return
     * @return null if no matching message is found, otherwise return a Message
     */
    public static List<Message> getMessagesSinceTimestamp(Context context, long timestamp) {
        final String selection = Message.DATE + " > ?";
        final String[] selectionArgs = new String[] {Long.toString(timestamp)};

        return getMessagesWithFilter(context, selection, selectionArgs);
    }

    /**
     * Gets Messages for caller functions, such as: getMessagesWithFilter() and getConversations()
     *
     * @param Uri Uri indicating the messages database to read
     * @param context android.content.Context running the request.
     * @param selection Parameterizable filter to use with the ContentResolver query. May be null.
     * @param selectionArgs Parameters for selection. May be null.
     * @return Returns HashMap<ThreadID, List<Message>>, which is transformed in caller functions into other classes.
     */
    private static HashMap<ThreadID, List<Message>> getMessages(Uri Uri,
                                                          Context context,
                                                          String selection,
                                                          String[] selectionArgs) {
        HashMap<ThreadID, List<Message>> toReturn = new HashMap<>();
            try (Cursor myCursor = context.getContentResolver().query(
                Uri,
                Message.smsColumns,
                selection,
                selectionArgs,
                null)
        ) {
            if (myCursor != null && myCursor.moveToFirst()) {
                int threadColumn = myCursor.getColumnIndexOrThrow(ThreadID.lookupColumn);
                do {
                    HashMap<String, String> messageInfo = new HashMap<>();
                    for (int columnIdx = 0; columnIdx < myCursor.getColumnCount(); columnIdx++) {
                        String colName = myCursor.getColumnName(columnIdx);
                        String body = myCursor.getString(columnIdx);
                        messageInfo.put(colName, body);
                    }

                    Message message = new Message(messageInfo);
                    ThreadID threadID = new ThreadID(message.m_threadID);

                    if (!toReturn.containsKey(threadID)) {
                        toReturn.put(threadID, new ArrayList<>());
                    }
                    toReturn.get(threadID).add(message);
                } while (myCursor.moveToNext());
            } else {
                // No conversations or SMSes available?
            }
        }
        return toReturn;
    }
    
    /**
     * Get all messages matching the passed filter. See documentation for Android's ContentResolver
     *
     * @param context android.content.Context running the request
     * @param selection Parameterizable filter to use with the ContentResolver query. May be null.
     * @param selectionArgs Parameters for selection. May be null.
     * @return List of messages matching the filter
     */
    private static List<Message> getMessagesWithFilter(Context context, String selection, String[] selectionArgs) {
        HashMap<ThreadID, List<Message>> result = getMessages(SMSHelper.getSMSUri(), context, selection, selectionArgs);
        List<Message> toReturn = new ArrayList<>();

        for(Map.Entry<ThreadID, List<Message>> entry : result.entrySet()) {
            toReturn.addAll(entry.getValue());
        }
        return toReturn;
    }

    /**
     * Get the last message from each conversation. Can use those thread_ids to look up more
     * messages in those conversations
     *
     * @param context android.content.Context running the request
     * @return Mapping of thread_id to the first message in each thread
     */
    public static Map<ThreadID, Message> getConversations(Context context) {
        HashMap<ThreadID, List<Message>> result = getMessages(SMSHelper.getConversationUri(), context, null, null);
        HashMap<ThreadID, Message> toReturn = new HashMap<>();

        for(Map.Entry<ThreadID, List<Message>> entry : result.entrySet()) {
            ThreadID returnThreadID = entry.getKey();
            List<Message> messages = entry.getValue();

            toReturn.put(returnThreadID, messages.get(0));
        }
        return toReturn;
    }

    /**
     * Register a ContentObserver for the Messages database
     *
     * @param observer ContentObserver to alert on Message changes
     */
    public static void registerObserver(ContentObserver observer, Context context) {
        context.getContentResolver().registerContentObserver(
                SMSHelper.getSMSUri(),
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

        public String toString() {
            return this.threadID.toString();
        }

        @Override
        public int hashCode() {
            return this.threadID.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            return other.getClass().isAssignableFrom(ThreadID.class) && ((ThreadID) other).threadID.equals(this.threadID);
        }
    }

    /**
     * Represent a message and all of its interesting data columns
     */
    public static class Message {

        final String m_address;
        final String m_body;
        public final long m_date;
        final int m_type;
        final int m_read;
        final long m_threadID; // ThreadID is *int* for SMS messages but *long* for MMS
        final int m_uID;

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
        static final String U_ID = Telephony.Sms._ID;           // Something which uniquely identifies this message

        /**
         * Event flags
         * A message should have a bitwise-or of event flags before delivering the packet
         * Any events not supported by the receiving device should be ignored
         */
        public static final int TEXT_MESSAGE = 0x1; // This message has a "body" field which contains
                                                    // pure, human-readable text

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
        };

        Message(final HashMap<String, String> messageInfo) {
            m_address = messageInfo.get(Message.ADDRESS);
            m_body = messageInfo.get(Message.BODY);
            m_date = Long.parseLong(messageInfo.get(Message.DATE));
            if (messageInfo.get(Message.TYPE) == null)
            {
                // To be honest, I have no idea why this happens. The docs say the TYPE field is mandatory.
                // Just stick some junk in here and hope we can figure it out later.
                // Quick investigation suggests that these are multi-target MMSes
                m_type = -1;
            } else {
                m_type = Integer.parseInt(messageInfo.get(Message.TYPE));
            }
            m_read = Integer.parseInt(messageInfo.get(Message.READ));
            m_threadID = Long.parseLong(messageInfo.get(Message.THREAD_ID));
            m_uID = Integer.parseInt(messageInfo.get(Message.U_ID));
        }

        public JSONObject toJSONObject() throws JSONException {
            JSONObject json = new JSONObject();

            json.put(Message.ADDRESS, m_address);
            json.put(Message.BODY, m_body);
            json.put(Message.DATE, m_date);
            json.put(Message.TYPE, m_type);
            json.put(Message.READ, m_read);
            json.put(Message.THREAD_ID, m_threadID);
            json.put(Message.U_ID, m_uID);

            return json;
        }

        @Override
        public String toString() {
            return this.m_body;
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
