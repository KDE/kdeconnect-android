/*
 * Copyright 2014 Albert Vaca Cintora <albertvaka@gmail.com>
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

import android.annotation.TargetApi;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.provider.ContactsContract.PhoneLookup;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ContactsHelper {

    static final String LOG_TAG = "ContactsHelper";

    /**
     * Lookup the name and photoID of a contact given a phone number
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static Map<String, String> phoneNumberLookup(Context context, String number) {

        Map<String, String> contactInfo = new HashMap<>();

        Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        String[] columns = new String[]{
                PhoneLookup.DISPLAY_NAME,
                PhoneLookup.PHOTO_URI
                /*, PhoneLookup.TYPE
                  , PhoneLookup.LABEL
                  , PhoneLookup.ID */
        };
        try (Cursor cursor = context.getContentResolver().query(uri, columns,null, null, null)) {
            // Take the first match only
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(PhoneLookup.DISPLAY_NAME);
                if (nameIndex != -1) {
                    contactInfo.put("name", cursor.getString(nameIndex));
                }

                nameIndex = cursor.getColumnIndex(PhoneLookup.PHOTO_URI);
                if (nameIndex != -1) {
                    contactInfo.put("photoID", cursor.getString(nameIndex));
                }
            }
        } catch (Exception ignored) {
        }
        return contactInfo;
    }

    public static String photoId64Encoded(Context context, String photoId) {
        if (photoId == null) {
            return "";
        }
        Uri photoUri = Uri.parse(photoId);

        ByteArrayOutputStream encodedPhoto = new ByteArrayOutputStream();
        try (InputStream input = context.getContentResolver().openInputStream(photoUri); Base64OutputStream output = new Base64OutputStream(encodedPhoto, Base64.DEFAULT)) {
            byte[] buffer = new byte[1024];
            int len;
            //noinspection ConstantConditions
            while ((len = input.read(buffer)) != -1) {
                output.write(buffer, 0, len);
            }
            return encodedPhoto.toString();
        } catch (Exception ex) {
            Log.e(LOG_TAG, ex.toString());
            return "";
        }
    }

    /**
     * Return all the NAME_RAW_CONTACT_IDS which contribute an entry to a Contact in the database
     * <p>
     * If the user has, for example, joined several contacts, on the phone, the IDs returned will
     * be representative of the joined contact
     * <p>
     * See here: https://developer.android.com/reference/android/provider/ContactsContract.Contacts.html
     * for more information about the connection between contacts and raw contacts
     *
     * @param context android.content.Context running the request
     * @return List of each NAME_RAW_CONTACT_ID in the Contacts database
     */
    public static List<uID> getAllContactContactIDs(Context context) {
        ArrayList<uID> toReturn = new ArrayList<>();

        // Define the columns we want to read from the Contacts database
        final String[] columns = new String[]{
                ContactsContract.Contacts.LOOKUP_KEY
        };

        Uri contactsUri = ContactsContract.Contacts.CONTENT_URI;
        try (Cursor contactsCursor = context.getContentResolver().query(contactsUri, columns, null, null, null)) {
            if (contactsCursor != null && contactsCursor.moveToFirst()) {
                do {
                    uID contactID;

                    int idIndex = contactsCursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY);
                    if (idIndex != -1) {
                        contactID = new uID(contactsCursor.getString(idIndex));
                    } else {
                        // Something went wrong with this contact
                        // If you are experiencing this, please open a bug report indicating how you got here
                        Log.e(LOG_TAG, "Got a contact which does not have a LOOKUP_KEY");
                        continue;
                    }

                    if (!toReturn.contains(contactID)) {
                        toReturn.add(contactID);
                    }
                } while (contactsCursor.moveToNext());
            }
        }

        return toReturn;
    }

    /**
     * Get VCards using serial database lookups. This is tragically slow, so call only when needed.
     *
     * There is a faster API specified using ContactsContract.Contacts.CONTENT_MULTI_VCARD_URI,
     * but there does not seem to be a way to figure out which ID resulted in which VCard using that API
     *
     * @param context    android.content.Context running the request
     * @param IDs        collection of uIDs to look up
     * @return Mapping of uIDs to the corresponding VCard
     */
    private static Map<uID, VCardBuilder> getVCardsSlow(Context context, Collection<uID> IDs) {
        Map<uID, VCardBuilder> toReturn = new HashMap<>();

        for (uID ID : IDs) {
            String lookupKey = ID.toString();
            Uri vcardURI = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, lookupKey);

            try (InputStream input = context.getContentResolver().openInputStream(vcardURI)) {

                if (input == null)
                {
                    throw new NullPointerException("ContentResolver did not give us a stream for the VCard for uID " + ID);
                }

                BufferedReader bufferedInput = new BufferedReader(new InputStreamReader(input));

                StringBuilder vcard = new StringBuilder();
                String line;
                while ((line = bufferedInput.readLine()) != null) {
                    vcard.append(line).append('\n');
                }

                toReturn.put(ID, new VCardBuilder(vcard.toString()));
            } catch (IOException e) {
                // If you are experiencing this, please open a bug report indicating how you got here
                Log.e("Contacts", "Exception while fetching vcards", e);
            } catch (NullPointerException e) {
                // If you are experiencing this, please open a bug report indicating how you got here
                Log.e("Contacts", "Exception while fetching vcards", e);
            }
        }

        return toReturn;
    }

    /**
     * Get the VCard for every specified raw contact ID
     *
     * @param context android.content.Context running the request
     * @param IDs     collection of raw contact IDs to look up
     * @return Mapping of raw contact IDs to the corresponding VCard
     */
    public static Map<uID, VCardBuilder> getVCardsForContactIDs(Context context, Collection<uID> IDs) {
        return getVCardsSlow(context, IDs);
    }

    /**
     * Get the last-modified timestamp for every contact in the database
     *
     * @param context android.content.Context running the request
     * @return Mapping of contact uID to last-modified timestamp
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2) // Need API 18 for contact timestamps
    public static Map<uID, Long> getAllContactTimestamps(Context context) {
        String[] projection = { uID.COLUMN, ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP };

        Map<uID, Map<String, String>> databaseValues = accessContactsDatabase(context, projection, null, null, null);

        Map<uID, Long> timestamps = new HashMap<>();
        for (uID contactID : databaseValues.keySet()) {
            Map<String, String> data = databaseValues.get(contactID);
            timestamps.put(
              contactID,
              Long.parseLong(data.get(ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP))
            );
        }

        return timestamps;
    }

    /**
     * Get the last-modified timestamp for the specified contact
     *
     * @param context   android.content.Context running the request
     * @param contactID Contact uID to read
     * @throws ContactNotFoundException If the given ID for some reason does not match a contact
     * @return          Last-modified timestamp of the contact
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2) // Need API 18 for contact timestamps
    public static Long getContactTimestamp(Context context, uID contactID) throws ContactNotFoundException {
        String[] projection = { uID.COLUMN, ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP };
        String selection = uID.COLUMN + " = ?";
        String[] selectionArgs = { contactID.toString() };

        Map<uID, Map<String, String>> databaseValue = accessContactsDatabase(context, projection, selection, selectionArgs, null);

        if (databaseValue.size() == 0) {
            throw new ContactNotFoundException("Querying for contact with id " + contactID + " returned no results.");
        }

        if (databaseValue.size() != 1) {
            Log.w(LOG_TAG, "Received an improper number of return values from the database in getContactTimestamp: " + databaseValue.size());
        }

        Long timestamp = Long.parseLong(databaseValue.get(contactID).get(ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP));

        return timestamp;
    }

    /**
     * Return a mapping of contact IDs to a map of the requested data from the Contacts database.
     *
     * @param context    android.content.Context running the request
     * @param projection List of column names to extract, defined in ContactsContract.Contacts. Must contain uID.COLUMN
     * @param selection  Parameterizable filter to use with the ContentResolver query. May be null.
     * @param selectionArgs Parameters for selection. May be null.
     * @param sortOrder  Sort order to request from the ContentResolver query. May be null.
     * @return mapping of contact uIDs to desired values, which are a mapping of column names to the data contained there
     */
    private static Map<uID, Map<String, String>> accessContactsDatabase(
            @NonNull Context context,
            @NonNull String[] projection,
            @Nullable String   selection,
            @Nullable String[] selectionArgs,
            @Nullable String   sortOrder
            ) {
        Uri contactsUri = ContactsContract.Contacts.CONTENT_URI;

        HashMap<uID, Map<String, String>> toReturn = new HashMap<>();

        try (Cursor contactsCursor = context.getContentResolver().query(
                contactsUri,
                projection,
                selection,
                selectionArgs,
                sortOrder
        )) {
            if (contactsCursor != null && contactsCursor.moveToFirst()) {
                do {
                    Map<String, String> requestedData = new HashMap<>();

                    int uIDIndex = contactsCursor.getColumnIndexOrThrow(uID.COLUMN);
                    uID uID = new uID(contactsCursor.getString(uIDIndex));

                    // For each column, collect the data from that column
                    for (String column : projection) {
                        int index = contactsCursor.getColumnIndex(column);
                        // Since we might be getting various kinds of data, Object is the best we can do
                        String data;
                        if (index == -1) {
                            // This contact didn't have the requested column? Something is very wrong.
                            // If you are experiencing this, please open a bug report indicating how you got here
                            Log.e(LOG_TAG, "Got a contact which does not have a requested column");
                            continue;
                        }
                        data = contactsCursor.getString(index);

                        requestedData.put(column, data);
                    }

                    toReturn.put(uID, requestedData);
                } while (contactsCursor.moveToNext());
            }
        }
        return toReturn;
    }

    /**
     * This is a cheap ripoff of com.android.vcard.VCardBuilder
     * <p>
     * Maybe in the future that library will be made public and we can switch to using that!
     * <p>
     * The main similarity is the usage of .toString() to produce the finalized VCard and the
     * usage of .appendLine(String, String) to add stuff to the vcard
     */
    public static class VCardBuilder {
        static final String VCARD_END = "END:VCARD"; // Written to terminate the vcard
        static final String VCARD_DATA_SEPARATOR = ":";

        final StringBuilder vcardBody;

        /**
         * Take a partial vcard as a string and make a VCardBuilder
         *
         * @param vcard vcard to build upon
         */
        VCardBuilder(String vcard) {
            // Remove the end tag. We will add it back on in .toString()
            vcard = vcard.substring(0, vcard.indexOf(VCARD_END));

            vcardBody = new StringBuilder(vcard);
        }

        /**
         * Appends one line with a given property name and value.
         */
        public void appendLine(final String propertyName, final String rawValue) {
            vcardBody.append(propertyName)
                    .append(VCARD_DATA_SEPARATOR)
                    .append(rawValue)
                    .append("\n");
        }

        @NonNull
        public String toString() {
            return vcardBody.toString() + VCARD_END;
        }
    }

    /**
     * Essentially a typedef of the type used for a unique identifier
     */
    public static class uID {
        /**
         * We use the LOOKUP_KEY column of the Contacts table as a unique ID, since that's what it's
         * for
         */
        final String contactLookupKey;

        /**
         * Which Contacts column this uID is pulled from
         */
        static final String COLUMN = ContactsContract.Contacts.LOOKUP_KEY;

        public uID(String lookupKey) {

            if (lookupKey == null)
                throw new IllegalArgumentException("lookUpKey should not be null");

            contactLookupKey = lookupKey;
        }

        @NonNull
        public String toString() {
            return this.contactLookupKey;
        }

        @Override
        public int hashCode() {
            return contactLookupKey.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof uID) {
                return contactLookupKey.equals(((uID) other).contactLookupKey);
            }
            return contactLookupKey.equals(other);
        }
    }

    /**
     * Exception to indicate that a specified contact was not found
     */
    public static class ContactNotFoundException extends Exception {
        public ContactNotFoundException(uID contactID) {
            super("Unable to find contact with ID " + contactID);
        }

        public ContactNotFoundException(String message) {
            super(message);
        }
    }
}
