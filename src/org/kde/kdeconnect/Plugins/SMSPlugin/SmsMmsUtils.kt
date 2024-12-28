/*
 * SPDX-FileCopyrightText: 2020 Aniket Kumar <anikketkumar786@gmail.com>
 * SPDX-FileCopyrightText: 2021 Simon Redman <simon@ergotech.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.SMSPlugin;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.android.mms.dom.smil.parser.SmilXmlSerializer;
import com.google.android.mms.ContentType;
import com.google.android.mms.InvalidHeaderValueException;
import com.google.android.mms.MMSPart;
import com.google.android.mms.pdu_alt.CharacterSets;
import com.google.android.mms.pdu_alt.EncodedStringValue;
import com.google.android.mms.pdu_alt.MultimediaMessagePdu;
import com.google.android.mms.pdu_alt.PduBody;
import com.google.android.mms.pdu_alt.PduComposer;
import com.google.android.mms.pdu_alt.PduHeaders;
import com.google.android.mms.pdu_alt.PduPart;
import com.google.android.mms.pdu_alt.RetrieveConf;
import com.google.android.mms.pdu_alt.SendReq;
import com.google.android.mms.smil.SmilHelper;
import com.klinker.android.send_message.Message;
import com.klinker.android.send_message.Settings;
import com.klinker.android.send_message.Transaction;
import com.klinker.android.send_message.Utils;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.kde.kdeconnect.Helpers.SMSHelper;
import org.kde.kdeconnect.Helpers.TelephonyHelper;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect_tp.R;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class SmsMmsUtils {

    private static final String SENDING_MESSAGE = "Sending message";

    /**
     * Sends SMS or MMS message.
     *
     * @param context       context in which the method is called.
     * @param textMessage   text body of the message to be sent.
     * @param addressList   List of addresses.
     * @param attachedFiles List of attachments. Pass empty list if none.
     * @param subID         Note that here subID is of type int and not long because klinker library requires it as int
     *                      I don't really know the exact reason why they implemented it as int instead of long
     */
    public static void sendMessage(
            Context context,
            String textMessage,
            @NonNull List<SMSHelper.Attachment> attachedFiles,
            List<SMSHelper.Address> addressList,
            int subID
    ) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean longTextAsMms = prefs.getBoolean(context.getString(R.string.set_long_text_as_mms), false);
        boolean groupMessageAsMms = prefs.getBoolean(context.getString(R.string.set_group_message_as_mms), true);
        int sendLongAsMmsAfter = Integer.parseInt(
                prefs.getString(context.getString(R.string.convert_to_mms_after),
                context.getString(R.string.convert_to_mms_after_default)));

        TelephonyHelper.LocalPhoneNumber sendingPhoneNumber;
        List<TelephonyHelper.LocalPhoneNumber> allPhoneNumbers = TelephonyHelper.getAllPhoneNumbers(context);

        Optional<TelephonyHelper.LocalPhoneNumber> maybeSendingPhoneNumber = allPhoneNumbers.stream()
                .filter(localPhoneNumber -> localPhoneNumber.subscriptionID == subID)
                .findAny();

        if (maybeSendingPhoneNumber.isPresent()) {
            sendingPhoneNumber = maybeSendingPhoneNumber.get();
        } else {
            if (allPhoneNumbers.isEmpty()) {
                // We were not able to get any phone number for the user's device
                // Use a null "dummy" number instead. This should behave the same as not setting
                // the FromAddress (below) since the default value there is null.
                // The only more-correct thing we could do here is query the user (maybe in a
                // persistent configuration) for their phone number(s).
                sendingPhoneNumber = new TelephonyHelper.LocalPhoneNumber(null, subID);
                Log.w(SENDING_MESSAGE, "We do not know *any* phone numbers for this device. "
                        + "Attempting to send a message without knowing the local phone number is likely "
                        + "to result in strange behavior, such as the message being sent to yourself, "
                        + "or might entirely fail to send (or be received).");
            } else {
                // Pick an arbitrary phone number
                sendingPhoneNumber = allPhoneNumbers.get(0);
            }
            Log.w(SENDING_MESSAGE, "Unable to determine correct outgoing address for sub ID " + subID + ". Using " + sendingPhoneNumber);
        }

        if (sendingPhoneNumber.number != null) {
            // If the message is going to more than one target (to allow the user to send a message to themselves)
            if (addressList.size() > 1) {
                // Remove the user's phone number if present in the list of recipients
                addressList.removeIf(address -> sendingPhoneNumber.isMatchingPhoneNumber(address.getAddress()));
            }
        }

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

            settings.setSendLongAsMms(longTextAsMms);
            settings.setSendLongAsMmsAfter(sendLongAsMmsAfter);

            settings.setGroup(groupMessageAsMms);

            if (subID != -1) {
                settings.setSubscriptionId(subID);
            }

            Transaction transaction = new Transaction(context, settings);

            List<String> addresses = new ArrayList<>();
            for (SMSHelper.Address address : addressList) {
                addresses.add(address.toString());
            }

            Message message = new Message(textMessage, addresses.toArray(ArrayUtils.EMPTY_STRING_ARRAY));

            // If there are any attachment files add those into the message
            for (SMSHelper.Attachment attachedFile : attachedFiles) {
                byte[] file = Base64.decode(attachedFile.base64EncodedFile, Base64.DEFAULT);
                String mimeType = attachedFile.mimeType;
                String fileName = attachedFile.uniqueIdentifier;
                message.addMedia(file, mimeType, fileName);
            }

            message.setFromAddress(sendingPhoneNumber.number);
            message.setSave(true);

            // Sending MMS on android requires the app to be set as the default SMS app,
            // but sending SMS doesn't needs the app to be set as the default app.
            // This is the reason why there are separate branch handling for SMS and MMS.
            if (transaction.checkMMS(message)) {
                Log.v("", "Sending new MMS");
                //transaction.sendNewMessage(message, Transaction.NO_THREAD_ID);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    sendMmsMessageNative(context, message, settings);
                } else {
                    // Cross fingers and hope Klinker's library works for this case
                    transaction.sendNewMessage(message, Transaction.NO_THREAD_ID);
                }
            } else {
                Log.v(SENDING_MESSAGE, "Sending new SMS");
                transaction.sendNewMessage(message, Transaction.NO_THREAD_ID);
            }
            //TODO: Notify other end
        } catch (Exception e) {
            //TODO: Notify other end
            Log.e(SENDING_MESSAGE, "Exception", e);
        }
    }

    /**
     * Send an MMS message using SmsManager.sendMultimediaMessage
     *
     * @param context
     * @param message
     * @param klinkerSettings
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    protected static void sendMmsMessageNative(Context context, Message message, Settings klinkerSettings) {
        ArrayList<MMSPart> data = new ArrayList<>();

        for (Message.Part p : message.getParts()) {
            MMSPart part = new MMSPart();
            if (p.getName() != null) {
                part.Name = p.getName();
            } else {
                part.Name = p.getContentType().split("/")[0];
            }
            part.MimeType = p.getContentType();
            part.Data = p.getMedia();
            data.add(part);
        }

        if (message.getText() != null && !message.getText().equals("")) {
            // add text to the end of the part and send
            MMSPart part = new MMSPart();
            part.Name = "text";
            part.MimeType = "text/plain";
            part.Data = message.getText().getBytes();
            data.add(part);
        }

        SendReq sendReq = buildPdu(context, message.getFromAddress(), message.getAddresses(), message.getSubject(), data, klinkerSettings);

        Bundle configOverrides = new Bundle();
        configOverrides.putBoolean(SmsManager.MMS_CONFIG_GROUP_MMS_ENABLED, klinkerSettings.getGroup());

        // Write the PDUs to disk so that we can pass them to the SmsManager
        final String fileName = "send." + Math.abs(new Random().nextLong()) + ".dat";
        File mSendFile = new File(context.getCacheDir(), fileName);

        Uri contentUri = (new Uri.Builder())
                .authority(context.getPackageName() + ".MmsFileProvider")
                .path(fileName)
                .scheme(ContentResolver.SCHEME_CONTENT)
                .build();

        try (FileOutputStream writer = new FileOutputStream(mSendFile)) {
            writer.write(new PduComposer(context, sendReq).make());
        } catch (final IOException e)
        {
            android.util.Log.e(SENDING_MESSAGE, "Error while writing temporary PDU file: ", e);
        }

        SmsManager mSmsManager;

        if (klinkerSettings.getSubscriptionId() < 0)
        {
            mSmsManager = SmsManager.getDefault();
        } else {
            mSmsManager = SmsManager.getSmsManagerForSubscriptionId(klinkerSettings.getSubscriptionId());
        }

        mSmsManager.sendMultimediaMessage(context, contentUri, null, null, null);
    }

    public static final long DEFAULT_EXPIRY_TIME = 7 * 24 * 60 * 60;
    public static final int DEFAULT_PRIORITY = PduHeaders.PRIORITY_NORMAL;

    /**
     * Copy of the same-name method from https://github.com/klinker41/android-smsmms
     */
    private static SendReq buildPdu(Context context, String fromAddress, String[] recipients, String subject,
                                    List<MMSPart> parts, Settings settings) {
        final SendReq req = new SendReq();
        // From, per spec
        req.prepareFromAddress(context, fromAddress, settings.getSubscriptionId());
        // To
        for (String recipient : recipients) {
            req.addTo(new EncodedStringValue(recipient));
        }
        // Subject
        if (!TextUtils.isEmpty(subject)) {
            req.setSubject(new EncodedStringValue(subject));
        }
        // Date
        req.setDate(System.currentTimeMillis() / 1000);
        // Body
        PduBody body = new PduBody();
        // Add text part. Always add a smil part for compatibility, without it there
        // may be issues on some carriers/client apps
        int size = 0;
        for (int i = 0; i < parts.size(); i++) {
            MMSPart part = parts.get(i);
            size += addTextPart(body, part, i);
        }

        // add a SMIL document for compatibility
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SmilXmlSerializer.serialize(SmilHelper.createSmilDocument(body), out);
        PduPart smilPart = new PduPart();
        smilPart.setContentId("smil".getBytes());
        smilPart.setContentLocation("smil.xml".getBytes());
        smilPart.setContentType(ContentType.APP_SMIL.getBytes());
        smilPart.setData(out.toByteArray());
        body.addPart(0, smilPart);

        req.setBody(body);
        // Message size
        req.setMessageSize(size);
        // Message class
        req.setMessageClass(PduHeaders.MESSAGE_CLASS_PERSONAL_STR.getBytes());
        // Expiry
        req.setExpiry(DEFAULT_EXPIRY_TIME);
        try {
            // Priority
            req.setPriority(DEFAULT_PRIORITY);
            // Delivery report
            req.setDeliveryReport(PduHeaders.VALUE_NO);
            // Read report
            req.setReadReport(PduHeaders.VALUE_NO);
        } catch (InvalidHeaderValueException e) {}

        return req;
    }

    /**
     * Copy of the same-name method from https://github.com/klinker41/android-smsmms
     */
    private static int addTextPart(PduBody pb, MMSPart p, int id) {
        String filename = p.Name;
        final PduPart part = new PduPart();
        // Set Charset if it's a text media.
        if (p.MimeType.startsWith("text")) {
            part.setCharset(CharacterSets.UTF_8);
        }
        // Set Content-Type.
        part.setContentType(p.MimeType.getBytes());
        // Set Content-Location.
        part.setContentLocation(filename.getBytes());
        int index = filename.lastIndexOf(".");
        String contentId = (index == -1) ? filename
                : filename.substring(0, index);
        part.setContentId(contentId.getBytes());
        part.setData(p.Data);
        pb.addPart(part);

        return part.getData().length;
    }

    /**
     * Returns the Address of the sender of the MMS message.
     * @return                   sender's Address
     */
    public static SMSHelper.Address getMmsFrom(Context context, MultimediaMessagePdu msg) {
        if (msg == null) { return null; }
        EncodedStringValue encodedStringValue = msg.getFrom();
        return new SMSHelper.Address(context, encodedStringValue.getString());
    }

    /**
     * returns a List of Addresses of all the recipients of a MMS message.
     * @return          List of Addresses of all recipients of an MMS message
     */
    public static List<SMSHelper.Address> getMmsTo(Context context, MultimediaMessagePdu msg) {
        if (msg == null) { return null; }
        StringBuilder toBuilder = new StringBuilder();
        EncodedStringValue[] to = msg.getTo();

        if (to != null) {
            toBuilder.append(EncodedStringValue.concat(to));
        }

        if (msg instanceof RetrieveConf) {
            EncodedStringValue[] cc = ((RetrieveConf) msg).getCc();
            if (cc != null && cc.length != 0) {
                toBuilder.append(";");
                toBuilder.append(EncodedStringValue.concat(cc));
            }
        }

        String built = toBuilder.toString().replace(";", ", ");
        if (built.startsWith(", ")) {
            built = built.substring(2);
        }

        return stripDuplicatePhoneNumbers(context, built);
    }

    /**
     * Removes duplicate addresses from the string and returns List of Addresses
     */
    public static List<SMSHelper.Address> stripDuplicatePhoneNumbers(Context context, String phoneNumbers) {
        if (phoneNumbers == null) {
            return null;
        }

        String[] numbers = phoneNumbers.split(", ");

        List<SMSHelper.Address> uniqueNumbers = new ArrayList<>();

        for (String number : numbers) {
            // noinspection SuspiciousMethodCalls
            if (!uniqueNumbers.contains(number.trim())) {
                uniqueNumbers.add(new SMSHelper.Address(context, number.trim()));
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
        Uri partURI = ContentUris.withAppendedId(SMSHelper.mMSPartUri, id);
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
        Uri partURI = ContentUris.withAppendedId(SMSHelper.mMSPartUri, id);
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
