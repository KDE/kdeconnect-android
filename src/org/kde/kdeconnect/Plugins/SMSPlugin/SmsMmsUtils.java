/*
 * SPDX-FileCopyrightText: 2020 Aniket Kumar <anikketkumar786@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.SMSPlugin;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

import com.google.android.mms.pdu_alt.EncodedStringValue;
import com.google.android.mms.pdu_alt.MultimediaMessagePdu;
import com.google.android.mms.pdu_alt.PduPersister;
import com.google.android.mms.pdu_alt.RetrieveConf;
import com.klinker.android.send_message.Message;
import com.klinker.android.send_message.Settings;
import com.klinker.android.send_message.Transaction;
import com.klinker.android.send_message.Utils;

import android.content.ContentUris;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.Telephony;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.RequiresApi;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.io.IOUtils;
import org.kde.kdeconnect.Helpers.SMSHelper;
import org.kde.kdeconnect.Helpers.TelephonyHelper;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect_tp.R;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class SmsMmsUtils {

    private static final String SENDING_MESSAGE = "Sending message";

    /**
     * Sends SMS or MMS message.
     *
     * @param context       context in which the method is called.
     * @param textMessage   text body of the message to be sent.
     * @param addressList   List of addresses.
     * @param subID         Note that here subID is of type int and not long because klinker library requires it as int
     *                      I don't really know the exact reason why they implemented it as int instead of long
     */
    public static void sendMessage(Context context, String textMessage, List<SMSHelper.Address> addressList, int subID) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean longTextAsMms = prefs.getBoolean(context.getString(R.string.set_long_text_as_mms), false);
        boolean groupMessageAsMms = prefs.getBoolean(context.getString(R.string.set_group_message_as_mms), true);
        int sendLongAsMmsAfter = Integer.parseInt(
                prefs.getString(context.getString(R.string.convert_to_mms_after),
                context.getString(R.string.convert_to_mms_after_default)));

        try {
            Settings settings = new Settings();
            TelephonyHelper.ApnSetting apnSettings = TelephonyHelper.getPreferredApn(context, subID);

            if (apnSettings != null) {
                settings.setMmsc(apnSettings.getMmsc().toString());
                settings.setProxy(apnSettings.getMmsProxyAddressAsString());
                settings.setPort(Integer.toString(apnSettings.getMmsProxyPort()));
            } else {
                settings.setUseSystemSending(true);
            }

            if (Utils.isDefaultSmsApp(context)) {
                settings.setSendLongAsMms(longTextAsMms);
                settings.setSendLongAsMmsAfter(sendLongAsMmsAfter);
            }

            settings.setGroup(groupMessageAsMms);

            if (subID != -1) {
                settings.setSubscriptionId(subID);
            }

            Transaction transaction = new Transaction(context, settings);
            transaction.setExplicitBroadcastForSentSms(new Intent(context, SmsSentReceiver.class));
            transaction.setExplicitBroadcastForSentMms(new Intent(context, MmsSentReceiverImpl.class));

            List<String> addresses = new ArrayList<>();
            for (SMSHelper.Address address : addressList) {
                addresses.add(address.toString());
            }

            Message message = new Message(textMessage, addresses.toArray(ArrayUtils.EMPTY_STRING_ARRAY));
            message.setSave(true);

            // Sending MMS on android requires the app to be set as the default SMS app,
            // but sending SMS doesn't needs the app to be set as the default app.
            // This is the reason why there are separate branch handling for SMS and MMS.
            if (transaction.checkMMS(message)) {
                if (Utils.isDefaultSmsApp(context)) {
                    if (Utils.isMobileDataEnabled(context)) {
                        com.klinker.android.logger.Log.v("", "Sending new MMS");
                        transaction.sendNewMessage(message, Transaction.NO_THREAD_ID);
                    }
                } else {
                    com.klinker.android.logger.Log.v(SENDING_MESSAGE, "KDE Connect is not set to default SMS app.");
                    //TODO: Notify other end that they need to enable the mobile data in order to send MMS
                }
            } else {
                com.klinker.android.logger.Log.v(SENDING_MESSAGE, "Sending new SMS");
                transaction.sendNewMessage(message, Transaction.NO_THREAD_ID);
            }
            //TODO: Notify other end
        } catch (Exception e) {
            //TODO: Notify other end
            com.klinker.android.logger.Log.e(SENDING_MESSAGE, "Exception", e);
        }
    }

    /**
     * Returns the Address of the sender of the MMS message.
     * @param uri                content://mms/msgId/addr
     * @param context            context in which the method is called.
     * @return                   sender's Address
     */
    public static SMSHelper.Address getMmsFrom(Context context, Uri uri) {
        MultimediaMessagePdu msg;

        try {
            msg = (MultimediaMessagePdu) PduPersister.getPduPersister(context).load(uri);
        } catch (Exception e) {
            return null;
        }

        EncodedStringValue encodedStringValue = msg.getFrom();
        SMSHelper.Address from = new SMSHelper.Address(encodedStringValue.getString());
        return from;
    }

    /**
     * returns a List of Addresses of all the recipients of a MMS message.
     * @param uri       content://mms/part_id
     * @param context   Context in which the method is called.
     * @return          List of Addresses of all recipients of an MMS message
     */
    public static List<SMSHelper.Address> getMmsTo(Context context, Uri uri) {
        MultimediaMessagePdu msg;

        try {
            msg = (MultimediaMessagePdu) PduPersister.getPduPersister(context).load(uri);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        StringBuilder toBuilder = new StringBuilder();
        EncodedStringValue to[] = msg.getTo();

        if (to != null) {
            toBuilder.append(EncodedStringValue.concat(to));
        }

        if (msg instanceof RetrieveConf) {
            EncodedStringValue cc[] = ((RetrieveConf) msg).getCc();
            if (cc != null && cc.length == 0) {
                toBuilder.append(";");
                toBuilder.append(EncodedStringValue.concat(cc));
            }
        }

        String built = toBuilder.toString().replace(";", ", ");
        if (built.startsWith(", ")) {
            built = built.substring(2);
        }

        return stripDuplicatePhoneNumbers(built);
    }

    /**
     * Removes duplicate addresses from the string and returns List of Addresses
     */
    public static List<SMSHelper.Address> stripDuplicatePhoneNumbers(String phoneNumbers) {
        if (phoneNumbers == null) {
            return null;
        }

        String numbers[] = phoneNumbers.split(", ");

        List<SMSHelper.Address> uniqueNumbers = new ArrayList<>();

        for (String number : numbers) {
            if (!uniqueNumbers.contains(number.trim())) {
                uniqueNumbers.add(new SMSHelper.Address(number.trim()));
            }
        }

        return uniqueNumbers;
    }

    /**
     * Converts a given bitmap to an encoded Base64 string for sending to desktop
     * @param bitmap    bitmap to be encoded into string*
     * @return          Returns the Base64 encoded string
     */
    public static String bitMapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new  ByteArrayOutputStream();

        // The below line is not really compressing to PNG so much as encoding as PNG, since PNG is lossless
        boolean isCompressed = bitmap.compress(Bitmap.CompressFormat.PNG,100, byteArrayOutputStream);
        if (isCompressed) {
            byte[] b = byteArrayOutputStream.toByteArray();
            String encodedString = Base64.encodeToString(b, Base64.DEFAULT);
            return encodedString;
        }
        return null;
    }

    /**
     * Reads the image files attached with an MMS from MMS database
     * @param context    Context in which the method is called
     * @param id         part ID of the image file attached with an MMS message
     * @return           Returns the image as a bitmap
     */
    public static Bitmap getMmsImage(Context context, long id) {
        Uri partURI = ContentUris.withAppendedId(SMSHelper.getMMSPartUri(), id);
        Bitmap bitmap = null;

        try (InputStream inputStream = context.getContentResolver().openInputStream(partURI)) {
            bitmap = BitmapFactory.decodeStream(inputStream);
        } catch (IOException e) {
            Log.e("SmsMmsUtils", "Exception", e);
        }

        return bitmap;
    }

    /**
     * This method loads the byteArray of attachment file stored in the MMS database
     * @param context    Context in which the method is called
     * @param id         part ID of the particular multimedia attachment file of MMS
     * @return           returns the byteArray of the attachment
     */
    public static byte[] loadAttachment(Context context, long id) {
        Uri partURI = ContentUris.withAppendedId(SMSHelper.getMMSPartUri(), id);
        byte[] byteArray = new byte[0];

        // Open inputStream from the specified URI
        try (InputStream inputStream = context.getContentResolver().openInputStream(partURI)) {
            // Try read from the InputStream
            if (inputStream != null) {
                byteArray = IOUtils.toByteArray(inputStream);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return byteArray;
    }

    /**
     * Create a SMS attachment packet using the partID of the file requested by the device
     */
    public static NetworkPacket partIdToMessageAttachmentPacket(
            final Context context,
            final long partID,
            final String filename,
            String type
    ) {
        byte[] attachment = loadAttachment(context, partID);
        long size = attachment.length;
        if (size == 0) {
            Log.e("SmsMmsUtils", "Loaded attachment is empty.");
        }

        try {
            InputStream inputStream = new ByteArrayInputStream(attachment);

            NetworkPacket np = new NetworkPacket(type);
            np.set("filename", filename);

            np.setPayload(new NetworkPacket.Payload(inputStream, size));

            return np;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Marks a conversation as read in the database.
     *
     * @param context      the context to get the content provider with.
     * @param recipients   the phone numbers to find the conversation with.
     */
    public static void markConversationRead(Context context, HashSet<String> recipients) {
        new Thread() {
            @RequiresApi(api = Build.VERSION_CODES.KITKAT)
            @Override
            public void run() {
                try {
                    long threadId = Utils.getOrCreateThreadId(context, recipients);
                    markAsRead(context, ContentUris.withAppendedId(Telephony.Threads.CONTENT_URI, threadId), threadId);
                } catch (Exception e) {
                    // the conversation doesn't exist
                    e.printStackTrace();
                }
            }
        }.start();
    }

    private static void markAsRead(Context context, Uri uri, long threadId) {
        Log.v("SMSPlugin", "marking thread with threadId " + threadId + " as read at Uri" + uri);

        if (uri != null && context != null) {
            ContentValues values = new ContentValues(2);
            values.put("read", 1);
            values.put("seen", 1);

            context.getContentResolver().update(uri, values, "(read=0 OR seen=0)", null);
        }
    }
}
