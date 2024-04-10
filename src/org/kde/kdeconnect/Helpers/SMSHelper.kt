/*
 * SPDX-FileCopyrightText: 2021 Simon Redman <simon@ergotech.com>
 * SPDX-FileCopyrightText: 2020 Aniket Kumar <anikketkumar786@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.Helpers

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.sqlite.SQLiteException
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.provider.Telephony
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.util.Log
import android.util.Pair
import androidx.annotation.RequiresApi
import com.google.android.mms.pdu_alt.MultimediaMessagePdu
import com.google.android.mms.pdu_alt.PduPersister
import com.google.android.mms.util_alt.PduCache
import com.google.android.mms.util_alt.PduCacheEntry
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.math.NumberUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.kde.kdeconnect.Helpers.TelephonyHelper.LocalPhoneNumber
import org.kde.kdeconnect.Plugins.SMSPlugin.MimeType
import org.kde.kdeconnect.Plugins.SMSPlugin.SmsMmsUtils
import java.io.IOException
import java.util.Arrays
import java.util.Objects
import java.util.SortedMap
import java.util.TreeMap
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import java.util.stream.Collectors
import kotlin.text.Charsets.UTF_8

@SuppressLint("InlinedApi")
object SMSHelper {
    private const val THUMBNAIL_HEIGHT = 100
    private const val THUMBNAIL_WIDTH = 100

    // The constant Telephony.Mms.Part.CONTENT_URI was added in API 29
    @JvmField
    val mMSPartUri : Uri = Uri.parse("content://mms/part/")

    /**
     * Get the base address for all message conversations
     * We only use this to fetch thread_ids because the data it returns if often incomplete or useless
     */
    private fun getConversationUri(): Uri {
        // Special case for Samsung
        // For some reason, Samsung devices do not support the regular SmsMms column.
        // However, according to https://stackoverflow.com/a/13640868/3723163, we can work around it this way.
        // By my understanding, "simple=true" means we can't support multi-target messages.
        // Go complain to Samsung about their annoying OS changes!
        if ("Samsung".equals(Build.MANUFACTURER, ignoreCase = true)) {
            Log.i("SMSHelper", "This appears to be a Samsung device. This may cause some features to not work properly.")
        }
        return Uri.parse("content://mms-sms/conversations?simple=true")
    }

    private fun getCompleteConversationsUri(): Uri {
        // This glorious - but completely undocumented - content URI gives us all messages, both MMS and SMS,
        // in all conversations
        // See https://stackoverflow.com/a/36439630/3723163
        return Uri.parse("content://mms-sms/complete-conversations")
    }

    /**
     * Column used to discriminate between SMS and MMS messages
     * Unfortunately, this column is not defined for Telephony.MmsSms.CONTENT_CONVERSATIONS_URI
     * (aka. content://mms-sms/conversations)
     * which gives us the first message in every conversation, but it *is* defined for
     * content://mms-sms/conversations/<threadID> which gives us the complete conversation matching
     * that threadID, so at least it's partially useful to us.
     */
    private const val TRANSPORT_TYPE_DISCRIMINATOR_COLUMN = Telephony.MmsSms.TYPE_DISCRIMINATOR_COLUMN

    /**
     * Get the timestamp of the newest known message. Will return Integer.MIN_VALUE if there are no messages.
     *
     * @param context  android.content.Context running the request
     * @return Timestamp of the oldest known message.
     */
    @JvmStatic
    fun getNewestMessageTimestamp(
        context: Context
    ): Long {
        var oldestMessageTimestamp = Long.MIN_VALUE
        val newestMessage = getMessagesInRange(context, null, Long.MAX_VALUE, 1L, true)
        // There should only be one, but in case for some reason there are more, take the latest
        for (message in newestMessage) {
            if (message.date > oldestMessageTimestamp) {
                oldestMessageTimestamp = message.date
            }
        }
        return oldestMessageTimestamp
    }

    /**
     * Get some or all the messages in a requested thread, starting with the most-recent message
     *
     * @param context  android.content.Context running the request
     * @param threadID Thread to look up
     * @param numberToGet Number of messages to return. Pass null for "all"
     * @return List of all messages in the thread
     */
    @JvmStatic
    fun getMessagesInThread(
        context: Context,
        threadID: ThreadID,
        numberToGet: Long?
    ): List<Message> {
        return getMessagesInRange(context, threadID, Long.MAX_VALUE, numberToGet, true)
    }

    /**
     * Get some messages in the given thread based on a start timestamp and an optional count
     *
     * @param context  android.content.Context running the request
     * @param threadID Optional ThreadID to look up. If not included, this method will return the latest messages from all threads.
     * @param startTimestamp Beginning of the range to return
     * @param numberToGet Number of messages to return. Pass null for "all"
     * @param getMessagesOlderStartTime If true, get messages with timestamps before the startTimestamp. If false, get newer messages
     * @return Some messages in the requested conversation
     */
    @JvmStatic
    @SuppressLint("NewApi")
    fun getMessagesInRange(
        context: Context,
        threadID: ThreadID?,
        startTimestamp: Long,
        numberToGet: Long?,
        getMessagesOlderStartTime: Boolean
    ): List<Message> {
        // The stickiness with this is that Android's MMS database has its timestamp in epoch *seconds*
        // while the SMS database uses epoch *milliseconds*.
        // I can think of no way around this other than manually querying each one with a different
        // "WHERE" statement.
        val allSmsColumns: MutableList<String> = Message.smsColumns.toMutableList()
        val allMmsColumns: MutableList<String> = Message.mmsColumns.toMutableList()
        if (getSubscriptionIdSupport(Telephony.Sms.CONTENT_URI, context)) {
            allSmsColumns.addAll(Message.multiSIMColumns)
        }
        if (getSubscriptionIdSupport(Telephony.Mms.CONTENT_URI, context)) {
            allMmsColumns.addAll(Message.multiSIMColumns)
        }
        var selection: String = if (getMessagesOlderStartTime) {
            Message.DATE + " <= ?"
        } else {
            Message.DATE + " >= ?"
        }
        val smsSelectionArgs: MutableList<String> = ArrayList(2)
        smsSelectionArgs.add(startTimestamp.toString())
        val mmsSelectionArgs: MutableList<String> = ArrayList(2)
        mmsSelectionArgs.add((startTimestamp / 1000).toString())
        if (threadID != null) {
            selection += " AND " + Message.THREAD_ID + " = ?"
            smsSelectionArgs.add(threadID.toString())
            mmsSelectionArgs.add(threadID.toString())
        }
        val sortOrder = Message.DATE + " DESC"
        val allMessages = getMessages(
            Telephony.Sms.CONTENT_URI,
            context,
            allSmsColumns,
            selection,
            smsSelectionArgs.toTypedArray<String>(),
            sortOrder,
            numberToGet
        )
        allMessages.addAll(
            getMessages(
                Telephony.Mms.CONTENT_URI,
                context,
                allMmsColumns,
                selection,
                mmsSelectionArgs.toTypedArray<String>(),
                sortOrder,
                numberToGet
            )
        )

        // Need to now only return the requested number of messages:
        // Suppose we were requested to return N values and suppose a user sends only one MMS per
        // week and N SMS per day. We have requested the same N for each, so if we just return everything
        // we would return some very old MMS messages which would be very confusing.
        val sortedMessages: SortedMap<Long, MutableCollection<Message>> =
            TreeMap(Comparator.reverseOrder())
        for (message in allMessages) {
            val existingMessages = sortedMessages.computeIfAbsent(
                message.date
            ) { _: Long? -> ArrayList() }
            existingMessages.add(message)
        }
        val toReturn: MutableList<Message> = ArrayList(allMessages.size)
        for (messages in sortedMessages.values) {
            toReturn.addAll(messages)
            if (numberToGet != null && toReturn.size >= numberToGet) {
                break
            }
        }
        return toReturn
    }

    /**
     * Checks if device supports `Telephony.Sms.SUBSCRIPTION_ID` column in database with URI `uri`
     *
     * @param uri Uri indicating the messages database to check
     * @param context android.content.Context running the request.
     */
    private fun getSubscriptionIdSupport(uri: Uri, context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return false
        }
        // Some (Xiaomi) devices running >= Android Lollipop (SDK 22+) don't support
        // `Telephony.Sms.SUBSCRIPTION_ID`, so additional check is needed.
        // It may be possible to use "sim_id" instead of "sub_id" on these devices
        // https://stackoverflow.com/a/38152331/6509200
        try {
            context.contentResolver.query(
                uri, arrayOf(Telephony.Sms.SUBSCRIPTION_ID),
                null,
                null,
                null
            ).use { availableColumnsCursor ->
                return availableColumnsCursor != null // if we got the cursor, the query shouldn't fail
            }
        } catch (e: SQLiteException) {
            // With uri content://mms-sms/conversations this query throws an exception if sub_id is not supported
            return !StringUtils.contains(e.message, Telephony.Sms.SUBSCRIPTION_ID)
        } catch (e: IllegalArgumentException) {
            return !StringUtils.contains(e.message, Telephony.Sms.SUBSCRIPTION_ID)
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
    </Message> */
    private fun getMessages(
        uri: Uri,
        context: Context,
        fetchColumns: Collection<String>,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
        numberToGet: Long?
    ): MutableList<Message> {
        val toReturn: MutableList<Message> = ArrayList()

        // Get all the active phone numbers so we can filter the user out of the list of targets
        // of any MMSes
        val userPhoneNumbers = TelephonyHelper.getAllPhoneNumbers(context)
        try {
            context.contentResolver.query(
                uri,
                fetchColumns.toTypedArray(),
                selection,
                selectionArgs,
                sortOrder
            ).use { myCursor ->
                if (myCursor != null && myCursor.moveToFirst()) {
                    do {
                        val transportTypeColumn = myCursor.getColumnIndex(
                            TRANSPORT_TYPE_DISCRIMINATOR_COLUMN
                        )
                        val transportType = if (transportTypeColumn < 0) {
                            // The column didn't actually exist. See https://issuetracker.google.com/issues/134592631
                            // Try to determine using other information
                            val messageBoxColumn = myCursor.getColumnIndex(Telephony.Mms.MESSAGE_BOX)
                            // MessageBoxColumn is defined for MMS only
                            val messageBoxExists = !myCursor.isNull(messageBoxColumn)
                            if (messageBoxExists) {
                                TransportType.MMS
                            } else {
                                // There is room here for me to have made an assumption and we'll guess wrong
                                // The penalty is the user will potentially get some garbled data, so that's not too bad.
                                TransportType.SMS
                            }
                        } else {
                            val transportTypeString = myCursor.getString(transportTypeColumn)
                            if ("mms" == transportTypeString) {
                                TransportType.MMS
                            } else if ("sms" == transportTypeString) {
                                TransportType.SMS
                            } else {
                                Log.w("SMSHelper", "Skipping message with unknown TransportType: $transportTypeString")
                                continue
                            }
                        }
                        val messageInfo = HashMap<String, String?>()
                        for (columnIdx in 0 until myCursor.columnCount) {
                            val colName = myCursor.getColumnName(columnIdx)
                            val body = myCursor.getString(columnIdx)
                            messageInfo[colName] = body
                        }
                        try {
                            when (transportType) {
                                TransportType.SMS -> toReturn.add(parseSMS(context, messageInfo))
                                TransportType.MMS -> toReturn.add(
                                    parseMMS(
                                        context,
                                        messageInfo,
                                        userPhoneNumbers
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            // Swallow exceptions in case we get an error reading one message so that we
                            // might be able to read some of them
                            Log.e("SMSHelper", "Got an error reading a message of type $transportType", e)
                        }
                    } while ((numberToGet == null || toReturn.size < numberToGet) && myCursor.moveToNext())
                }
            }
        } catch (e: SQLiteException) {
            var unfilteredColumns = arrayOf<String?>()
            context.contentResolver.query(uri, null, null, null, null)
                .use { unfilteredColumnsCursor ->
                    if (unfilteredColumnsCursor != null) {
                        unfilteredColumns = unfilteredColumnsCursor.columnNames
                    }
                }
            if (unfilteredColumns.isEmpty()) {
                throw MessageAccessException(uri, e)
            } else {
                throw MessageAccessException(unfilteredColumns, uri, e)
            }
        } catch (e: IllegalArgumentException) {
            var unfilteredColumns = arrayOf<String?>()
            context.contentResolver.query(uri, null, null, null, null)
                .use { unfilteredColumnsCursor ->
                    if (unfilteredColumnsCursor != null) {
                        unfilteredColumns = unfilteredColumnsCursor.columnNames
                    }
                }
            if (unfilteredColumns.isEmpty()) {
                throw MessageAccessException(uri, e)
            } else {
                throw MessageAccessException(unfilteredColumns, uri, e)
            }
        }
        return toReturn
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
    </Message> */
    @SuppressLint("NewApi")
    private fun getMessages(
        uri: Uri,
        context: Context,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
        numberToGet: Long?
    ): List<Message> {
        val allColumns: MutableSet<String> = HashSet()
        allColumns.addAll(Message.smsColumns)
        allColumns.addAll(Message.mmsColumns)
        if (getSubscriptionIdSupport(uri, context)) {
            allColumns.addAll(Message.multiSIMColumns)
        }
        if (uri != getConversationUri()) {
            // See https://issuetracker.google.com/issues/134592631
            allColumns.add(TRANSPORT_TYPE_DISCRIMINATOR_COLUMN)
        }
        return getMessages(
            uri,
            context,
            allColumns,
            selection,
            selectionArgs,
            sortOrder,
            numberToGet
        )
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
    private fun getMessagesWithFilter(
        context: Context,
        selection: String?,
        selectionArgs: Array<String>?,
        numberToGet: Long?
    ): List<Message> {
        val sortOrder = Message.DATE + " DESC"
        return getMessages(
            getCompleteConversationsUri(),
            context,
            selection,
            selectionArgs,
            sortOrder,
            numberToGet
        )
    }

    /**
     * Get the last message from each conversation. Can use the thread_ids in those messages to look
     * up more messages in those conversations
     *
     * Returns values ordered from most-recently-touched conversation to oldest, if possible.
     * Otherwise ordering is undefined.
     *
     * @param context android.content.Context running the request
     * @return Non-blocking iterable of the first message in each conversation
     */
    @JvmStatic
    fun getConversations(
        context: Context
    ): Sequence<Message> {
        val uri = getConversationUri()

        // Used to avoid spewing logs in case there is an overall problem with fetching thread IDs
        var warnedForNullThreadIDs = false

        // Used to avoid spewing logs in case the date column doesn't return anything.
        var warnedForUnorderedOutputs = false

        // Step 1: Populate the list of all known threadIDs
        // This is basically instantaneous even with lots of conversations because we only make one
        // query. If someone wanted to squeeze better UI performance out of this method, they could
        // iterate over the threadIdCursor instead of getting all the threads before beginning to
        // return conversations, but I doubt anyone will ever find it necessary.
        var threadIds: List<ThreadID>
        context.contentResolver.query(
            uri,
            null,
            null,
            null,
            null
        ).use { threadIdCursor ->
            val threadTimestampPair: MutableList<Pair<ThreadID, Long>> = ArrayList()
            while (threadIdCursor != null && threadIdCursor.moveToNext()) {
                // The "_id" column returned from the `content://sms-mms/conversations?simple=true` URI
                // is actually what the rest of the world calls a thread_id.
                // In my limited experimentation, the other columns are not populated, so don't bother
                // looking at them here.
                val idColumn = threadIdCursor.getColumnIndex("_id")
                val dateColumn = threadIdCursor.getColumnIndex("date")
                var threadID: ThreadID? = null
                var messageDate: Long = -1
                if (!threadIdCursor.isNull(idColumn)) {
                    threadID = ThreadID(threadIdCursor.getLong(idColumn))
                }
                if (!threadIdCursor.isNull(dateColumn)) {
                    // I think the presence of the "date" column depends on the specifics of the
                    // device. If it's there, we'll use it to return threads in a sorted order.
                    // If it's not there, we'll return them unsorted (maybe you get lucky and the
                    // conversations URI returns sorted anyway).
                    messageDate = threadIdCursor.getLong(dateColumn)
                }
                if (messageDate <= 0) {
                    if (!warnedForUnorderedOutputs) {
                        Log.w(
                            "SMSHelper",
                            "Got no value for date of thread. Return order of results is undefined."
                        )
                        warnedForUnorderedOutputs = true
                    }
                }
                if (threadID == null) {
                    if (!warnedForNullThreadIDs) {
                        Log.w(
                            "SMSHelper",
                            "Got null for some thread IDs. If these were valid threads, they will not be returned."
                        )
                        warnedForNullThreadIDs = true
                    }
                    continue
                }
                threadTimestampPair.add(Pair(threadID, messageDate))
            }
            threadIds = threadTimestampPair.stream()
                .sorted { left: Pair<ThreadID, Long>, right: Pair<ThreadID, Long> ->
                    right.second.compareTo(
                        left.second
                    )
                } // Sort most-recent to least-recent (largest to smallest)
                .map { threadTimestampPairElement: Pair<ThreadID, Long> -> threadTimestampPairElement.first }
                .collect(Collectors.toList())
        }

        // Step 2: Get the actual message object from each thread ID
        // Do this in a sequence, so that the caller can choose to interrupt us as frequently as desired
        return sequence {
            var threadIdsIndex = 0
            while (threadIdsIndex < threadIds.size) {
                val nextThreadId = threadIds[threadIdsIndex]
                threadIdsIndex++
                val firstMessage = getMessagesInThread(context, nextThreadId, 1L)
                if (firstMessage.size > 1) {
                    Log.w("SMSHelper", "getConversations got two messages for the same ThreadID: $nextThreadId")
                }
                if (firstMessage.isEmpty()) {
                    Log.e("SMSHelper", "ThreadID: $nextThreadId did not return any messages")
                    // This is a strange issue, but I don't know how to say what is wrong, so just continue along
                    continue
                }
                yield(firstMessage[0])
            }
        }
    }

    private fun addEventFlag(
        oldEvent: Int,
        eventFlag: Int
    ): Int {
        return oldEvent or eventFlag
    }

    /**
     * Parse all parts of an SMS into a Message
     */
    private fun parseSMS(
        context: Context,
        messageInfo: Map<String, String?>
    ): Message {
        var event = Message.EVENT_UNKNOWN
        event = addEventFlag(event, Message.EVENT_TEXT_MESSAGE)
        val address = listOf(
            Address(context, messageInfo[Telephony.Sms.ADDRESS]!!)
        )
        val maybeBody = messageInfo.getOrDefault(Message.BODY, "")
        val body = maybeBody ?: ""
        val date = NumberUtils.toLong(messageInfo.getOrDefault(Message.DATE, null))
        val type = NumberUtils.toInt(messageInfo.getOrDefault(Message.TYPE, null))
        val read = NumberUtils.toInt(messageInfo.getOrDefault(Message.READ, null))
        val threadID = ThreadID(
            NumberUtils.toLong(
                messageInfo.getOrDefault(Message.THREAD_ID, null),
                ThreadID.invalidThreadId.threadID
            )
        )
        val uID = NumberUtils.toLong(messageInfo.getOrDefault(Message.U_ID, null))
        val subscriptionID =
            NumberUtils.toInt(messageInfo.getOrDefault(Message.SUBSCRIPTION_ID, null))

        // Examine all the required SMS columns and emit a log if something seems amiss
        val anyNulls = Arrays.stream(
            arrayOf(
                Telephony.Sms.ADDRESS,
                Message.BODY,
                Message.DATE,
                Message.TYPE,
                Message.READ,
                Message.THREAD_ID,
                Message.U_ID
            )
        )
            .map { key: String -> messageInfo.getOrDefault(key, null) }
            .anyMatch { obj: String? -> Objects.isNull(obj) }
        if (anyNulls) {
            Log.e(
                "parseSMS",
                "Some fields were invalid. This indicates either a corrupted SMS database or an unsupported device."
            )
        }
        return Message(
            address,
            body,
            date,
            type,
            read,
            threadID,
            uID,
            event,
            subscriptionID,
            null
        )
    }

    /**
     * Parse all parts of the MMS message into a message
     * Original implementation from https://stackoverflow.com/a/6446831/3723163
     */
    private fun parseMMS(
        context: Context,
        messageInfo: Map<String, String?>,
        userPhoneNumbers: List<LocalPhoneNumber>
    ): Message {
        var event = Message.EVENT_UNKNOWN
        var body = ""
        val read = NumberUtils.toInt(messageInfo[Message.READ])
        val threadID = ThreadID(
            NumberUtils.toLong(
                messageInfo.getOrDefault(Message.THREAD_ID, null),
                ThreadID.invalidThreadId.threadID
            )
        )
        val uID = NumberUtils.toLong(messageInfo[Message.U_ID])
        val subscriptionID = NumberUtils.toInt(messageInfo[Message.SUBSCRIPTION_ID])
        val attachments: MutableList<Attachment> = ArrayList()
        val columns = arrayOf(
            Telephony.Mms.Part._ID,  // The content ID of this part
            Telephony.Mms.Part._DATA,  // The location in the filesystem of the data
            Telephony.Mms.Part.CONTENT_TYPE,  // The mime type of the data
            Telephony.Mms.Part.TEXT,  // The plain text body of this MMS
            Telephony.Mms.Part.CHARSET
        )
        val mmsID = messageInfo[Message.U_ID]
        val selection = Telephony.Mms.Part.MSG_ID + " = ?"
        val selectionArgs = arrayOf(mmsID)

        // Get text body and attachments of the message
        try {
            context.contentResolver.query(
                mMSPartUri,
                columns,
                selection,
                selectionArgs,
                null
            ).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    val partIDColumn = cursor.getColumnIndexOrThrow(Telephony.Mms.Part._ID)
                    val contentTypeColumn = cursor.getColumnIndexOrThrow(Telephony.Mms.Part.CONTENT_TYPE)
                    val dataColumn = cursor.getColumnIndexOrThrow(Telephony.Mms.Part._DATA)
                    val textColumn = cursor.getColumnIndexOrThrow(Telephony.Mms.Part.TEXT)
                    // TODO: Parse charset (As usual, it is skimpily documented) (Possibly refer to MMS spec)
                    do {
                        val partID = cursor.getLong(partIDColumn)
                        val contentType = cursor.getString(contentTypeColumn)
                        val data = cursor.getString(dataColumn)
                        if (MimeType.isTypeText(contentType)) {
                            body = if (data != null) {
                                // data != null means the data is on disk. Go get it.
                                getMmsText(context, partID)
                            } else {
                                cursor.getString(textColumn)
                            }
                            event = addEventFlag(event, Message.EVENT_TEXT_MESSAGE)
                        } else if (MimeType.isTypeImage(contentType)) {
                            val fileName = data.substring(data.lastIndexOf('/') + 1)

                            // Get the actual image from the mms database convert it into thumbnail and encode to Base64
                            val image = SmsMmsUtils.getMmsImage(context, partID)
                            val thumbnailImage = ThumbnailUtils.extractThumbnail(
                                image,
                                THUMBNAIL_WIDTH,
                                THUMBNAIL_HEIGHT
                            )
                            val encodedThumbnail = SmsMmsUtils.bitMapToBase64(thumbnailImage)
                            attachments.add(
                                Attachment(
                                    partID,
                                    contentType,
                                    encodedThumbnail,
                                    fileName
                                )
                            )
                        } else if (MimeType.isTypeVideo(contentType)) {
                            val fileName = data.substring(data.lastIndexOf('/') + 1)

                            // Can't use try-with-resources since MediaMetadataRetriever's close method was only added in API 29
                            val retriever = MediaMetadataRetriever()
                            retriever.setDataSource(
                                context,
                                ContentUris.withAppendedId(mMSPartUri, partID)
                            )
                            val videoThumbnail = retriever.frameAtTime
                            val encodedThumbnail = SmsMmsUtils.bitMapToBase64(
                                Bitmap.createScaledBitmap(
                                    videoThumbnail!!,
                                    THUMBNAIL_WIDTH,
                                    THUMBNAIL_HEIGHT,
                                    true
                                )
                            )
                            attachments.add(
                                Attachment(
                                    partID,
                                    contentType,
                                    encodedThumbnail,
                                    fileName
                                )
                            )
                        } else if (MimeType.isTypeAudio(contentType)) {
                            val fileName = data.substring(data.lastIndexOf('/') + 1)
                            attachments.add(Attachment(partID, contentType, null, fileName))
                        } else {
                            Log.v("SMSHelper", "Unsupported attachment type: $contentType")
                        }
                    } while (cursor.moveToNext())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Determine whether the message was in- our out- bound
        val messageBox = NumberUtils.toLong(messageInfo[Telephony.Mms.MESSAGE_BOX])
        val type = when (messageBox) {
            Telephony.Mms.MESSAGE_BOX_INBOX.toLong() -> {
                Telephony.Sms.MESSAGE_TYPE_INBOX
            }
            Telephony.Mms.MESSAGE_BOX_SENT.toLong() -> {
                Telephony.Sms.MESSAGE_TYPE_SENT
            }
            else -> {
                // As an undocumented feature, it looks like the values of Mms.MESSAGE_BOX_*
                // are the same as Sms.MESSAGE_TYPE_* of the same type. So by default let's just use
                // the value we've got.
                // This includes things like drafts, which are a far-distant plan to support
                NumberUtils.toInt(messageInfo[Telephony.Mms.MESSAGE_BOX])
            }
        }

        // Get address(es) of the message
        val msg = getMessagePdu(context, uID)
        val from = SmsMmsUtils.getMmsFrom(context, msg)
        val to = SmsMmsUtils.getMmsTo(context, msg)
        val addresses: MutableList<Address> = ArrayList()
        if (from != null) {
            val isLocalPhoneNumber = userPhoneNumbers.stream()
                .anyMatch { localPhoneNumber: LocalPhoneNumber ->
                    localPhoneNumber.isMatchingPhoneNumber(from.getAddress())
                }
            if (!isLocalPhoneNumber && from.toString() != "insert-address-token") {
                addresses.add(from)
            }
        }
        if (to != null) {
            for (toAddress in to) {
                val isLocalPhoneNumber = userPhoneNumbers.stream()
                    .anyMatch { localPhoneNumber: LocalPhoneNumber ->
                        localPhoneNumber.isMatchingPhoneNumber(toAddress.getAddress())
                    }
                if (!isLocalPhoneNumber && toAddress.toString() != "insert-address-token") {
                    addresses.add(toAddress)
                }
            }
        }

        // It looks like addresses[0] is always the sender of the message and
        // following addresses are recipient(s)
        // This usually means the addresses list is at least 2 long, but there are cases (special
        // telco service messages) where it is not (only 1 long in that case, just the "sender")
        if (addresses.size >= 2) {
            event = addEventFlag(event, Message.EVENT_MULTI_TARGET)
        }

        // Canonicalize the date field
        // SMS uses epoch milliseconds, MMS uses epoch seconds. Standardize on milliseconds.
        val rawDate = NumberUtils.toLong(messageInfo[Message.DATE])
        val date = rawDate * 1000
        return Message(
            addresses,
            body,
            date,
            type,
            read,
            threadID,
            uID,
            event,
            subscriptionID,
            attachments
        )
    }

    private fun getMessagePdu(context: Context, uID: Long): MultimediaMessagePdu? {
        val uri = ContentUris.appendId(Telephony.Mms.CONTENT_URI.buildUpon(), uID).build()
        return try {
            // Work around https://bugs.kde.org/show_bug.cgi?id=434348 by querying the PduCache directly
            // Most likely, this is how we should do business anyway and we will probably see a
            // decent speedup...
            val pduCache = PduCache.getInstance()
            val maybePduValue: PduCacheEntry? = synchronized(pduCache) { pduCache[uri] }
            if (maybePduValue != null) {
                maybePduValue.pdu as MultimediaMessagePdu
            } else {
                PduPersister.getPduPersister(context).load(uri) as MultimediaMessagePdu
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Get a text part of an MMS message
     * Original implementation from https://stackoverflow.com/a/6446831/3723163
     */
    private fun getMmsText(context: Context, id: Long): String {
        val partURI = ContentUris.withAppendedId(mMSPartUri, id)
        var body = ""
        try {
            context.contentResolver.openInputStream(partURI).use { stream ->
                if (stream != null) {
                    // The stream is buffered internally, so buffering it separately is unnecessary.
                    body = IOUtils.toString(stream, UTF_8)
                }
            }
        } catch (e: IOException) {
            throw MessageAccessException(partURI, e)
        }
        return body
    }

    /**
     * Register a ContentObserver for the Messages database
     *
     * @param observer ContentObserver to alert on Message changes
     */
    @JvmStatic
    fun registerObserver(
        observer: ContentObserver,
        context: Context
    ) {
        context.contentResolver.registerContentObserver(
            getConversationUri(),
            true,
            observer
        )
    }

    /**
     * Converts a given JSONArray of attachments into List<Attachment>
     *
     * The structure of the input is expected to be as follows:
     * [
     * {
     * "fileName": <String>             // Name of the file
     * "base64EncodedFile": <String>    // Base64 encoded file
     * "mimeType": <String>             // File type (eg: image/jpg, video/mp4 etc.)
     * },
     * ...
     * ]
    </String></String></String></Attachment> */
    @JvmStatic
    fun jsonArrayToAttachmentsList(
        jsonArray: JSONArray?
    ): List<Attachment> {
        if (jsonArray == null) {
            return emptyList()
        }
        val attachedFiles: MutableList<Attachment> = ArrayList(jsonArray.length())
        try {
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val base64EncodedFile = jsonObject.getString("base64EncodedFile")
                val mimeType = jsonObject.getString("mimeType")
                val fileName = jsonObject.getString("fileName")
                attachedFiles.add(Attachment(-1, mimeType, base64EncodedFile, fileName))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return attachedFiles
    }

    /**
     * converts a given JSONArray into List<Address>
    </Address> */
    @JvmStatic
    fun jsonArrayToAddressList(context: Context, jsonArray: JSONArray?): List<Address>? {
        if (jsonArray == null) {
            return null
        }
        val addresses: MutableList<Address> = ArrayList()
        try {
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val address = jsonObject.getString("address")
                addresses.add(Address(context, address))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return addresses
    }

    /**
     * Represent an ID used to uniquely identify a message thread
     */
    class ThreadID(val threadID: Long) {
        override fun toString(): String {
            return threadID.toString()
        }

        override fun hashCode(): Int {
            return java.lang.Long.hashCode(threadID)
        }

        override fun equals(other: Any?): Boolean {
            return other!!.javaClass.isAssignableFrom(ThreadID::class.java) && (other as ThreadID?)!!.threadID == threadID
        }

        companion object {

            /**
             * Define a value against which we can compare others, which should never be returned from
             * a valid thread.
             */
            val invalidThreadId = ThreadID(-1)
        }
    }

    class Attachment(
        private val partID: Long,
        @JvmField val mimeType: String,
        @JvmField val base64EncodedFile: String?,
        @JvmField val uniqueIdentifier: String
    ) {

        @Throws(JSONException::class)
        fun toJson(): JSONObject {
            val json = JSONObject()
            json.put(PART_ID, partID)
            json.put(MIME_TYPE, mimeType)
            if (base64EncodedFile != null) {
                json.put(ENCODED_THUMBNAIL, base64EncodedFile)
            }
            json.put(UNIQUE_IDENTIFIER, uniqueIdentifier)
            return json
        }

        companion object {
            /**
             * Attachment object field names
             */
            const val PART_ID = "part_id"
            const val MIME_TYPE = "mime_type"
            const val ENCODED_THUMBNAIL = "encoded_thumbnail"
            const val UNIQUE_IDENTIFIER = "unique_identifier"
        }
    }

    class Address(val context: Context, private val address: String) {
        @Throws(JSONException::class)
        fun toJson(): JSONObject {
            val json = JSONObject()
            json.put(ADDRESS, address)
            return json
        }

        fun getAddress() = address

        override fun toString() = address

        private val networkCountryIso = (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).networkCountryIso

        override fun equals(other: Any?) = when (other) {
            null -> false
            is Address -> PhoneNumberUtils.areSamePhoneNumber(address, other.address, networkCountryIso)
            is String -> PhoneNumberUtils.areSamePhoneNumber(address, other, networkCountryIso)
            else -> false
        }

        override fun hashCode(): Int {
            return address.hashCode()
        }

        companion object {
            /**
             * Address object field names
             */
            const val ADDRESS = "address"
        }
    }

    /**
     * Indicate that some error has occurred while reading a message.
     * More useful for logging than catching and handling
     */
    class MessageAccessException : RuntimeException {
        internal constructor(uri: Uri, cause: Throwable?) : super("Error getting messages from $uri", cause)

        internal constructor(availableColumns: Array<String?>, uri: Uri, cause: Throwable?) :
            super("Error getting messages from $uri. Available columns were: $availableColumns", cause)
    }

    /**
     * Represent all known transport types
     */
    enum class TransportType {
        SMS,
        MMS
        // Maybe in the future there will be more TransportType, but for now these are all I know about
    }

    /**
     * Represent a message and all of its interesting data columns
     */
    class Message internal constructor(
        private val addresses: List<Address>,
        val body: String,
        @JvmField val date: Long,
        val type: Int,
        val read: Int,
        private val threadID: ThreadID,
        private val uID: Long,
        val event: Int,
        private val subscriptionID: Int,
        private val attachments: List<Attachment>?
    ) {
        @Throws(JSONException::class)
        fun toJSONObject(): JSONObject {
            val json = JSONObject()
            val jsonAddresses = JSONArray()
            for (address in addresses) {
                jsonAddresses.put(address.toJson())
            }
            json.put(ADDRESSES, jsonAddresses)
            json.put(BODY, body)
            json.put(DATE, date)
            json.put(TYPE, type)
            json.put(READ, read)
            json.put(THREAD_ID, threadID.threadID)
            json.put(U_ID, uID)
            json.put(SUBSCRIPTION_ID, subscriptionID)
            json.put(EVENT, event)
            if (attachments != null) {
                val jsonAttachments = JSONArray()
                for (attachment in attachments) {
                    jsonAttachments.put(attachment.toJson())
                }
                json.put(ATTACHMENTS, jsonAttachments)
            }
            return json
        }

        override fun toString() = body

        companion object {
            /**
             * Named constants which are used to construct a Message
             * See: https://developer.android.com/reference/android/provider/Telephony.TextBasedSmsColumns.html for full documentation
             */
            const val ADDRESSES = "addresses" // Contact information (phone number or otherwise) of the remote
            const val BODY = Telephony.Sms.BODY // Body of the message
            const val DATE = Telephony.Sms.DATE // Date (Unix epoch millis) associated with the message
            const val TYPE = Telephony.Sms.TYPE // Compare with Telephony.TextBasedSmsColumns.MESSAGE_TYPE_*
            const val READ = Telephony.Sms.READ // Whether we have received a read report for this message (int)
            const val THREAD_ID = Telephony.Sms.THREAD_ID // Magic number which binds (message) threads
            const val U_ID = Telephony.Sms._ID // Something which uniquely identifies this message
            const val EVENT = "event"
            const val SUBSCRIPTION_ID = Telephony.Sms.SUBSCRIPTION_ID // An ID which appears to identify a SIM card
            const val ATTACHMENTS = "attachments" // List of files attached in an MMS

            /**
             * Event flags
             * A message should have a bitwise-or of event flags before delivering the packet
             * Any events not supported by the receiving device should be ignored
             */
            const val EVENT_UNKNOWN = 0x0 // The message was of some type we did not understand
            const val EVENT_TEXT_MESSAGE = 0x1 // This message has a "body" field which contains

            // pure, human-readable text
            const val EVENT_MULTI_TARGET = 0x2 // Indicates that this message has multiple recipients

            /**
             * Define the columns which are to be extracted from the Android SMS database
             */
            val smsColumns = arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ,
                Telephony.Sms.THREAD_ID,
                U_ID
            )
            val mmsColumns = arrayOf(
                U_ID,
                Telephony.Mms.THREAD_ID,
                Telephony.Mms.DATE,
                Telephony.Mms.READ,
                Telephony.Mms.TEXT_ONLY,
                Telephony.Mms.MESSAGE_BOX
            )

            /**
             * These columns are for determining what SIM card the message belongs to, and therefore
             * are only defined on Android versions with multi-sim capabilities
             */
            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
            val multiSIMColumns = arrayOf(Telephony.Sms.SUBSCRIPTION_ID)
        }
    }

    /**
     * If anyone wants to subscribe to changes in the messages database, they will need a thread
     * to handle callbacks on
     * This singleton conveniently provides such a thread, accessed and used via its Looper object
     */
    class MessageLooper private constructor() : Thread() {
        init {
            setName("MessageHelperLooper")
        }

        override fun run() {
            looperReadyLock.lock()
            try {
                Looper.prepare()
                looper = Looper.myLooper()
                looperReady.signalAll()
            } finally {
                looperReadyLock.unlock()
            }
            Looper.loop()
        }

        companion object {
            private var singleton: MessageLooper? = null
            private var looper: Looper? = null
            private val looperReadyLock: Lock = ReentrantLock()
            private val looperReady = looperReadyLock.newCondition()

            /**
             * Get the Looper object associated with this thread
             *
             * If the Looper has not been prepared, it is prepared as part of this method call.
             * Since this means a thread has to be spawned, this method might block until that thread is
             * ready to serve requests
             */
            @JvmStatic
            fun getLooper(): Looper? {
                if (singleton == null) {
                    looperReadyLock.lock()
                    try {
                        singleton = MessageLooper().apply { start() }
                        while (looper == null) {
                            // Block until the looper is ready
                            looperReady.await()
                        }
                    } catch (e: InterruptedException) {
                        // I don't know when this would happen
                        Log.e("SMSHelper", "Interrupted while waiting for Looper", e)
                        return null
                    } finally {
                        looperReadyLock.unlock()
                    }
                }
                return looper
            }
        }
    }
}
