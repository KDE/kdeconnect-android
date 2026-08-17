/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 * SPDX-FileCopyrightText: 2018 Simon Redman <simon@ergotech.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.helpers

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.PhoneLookup
import android.util.Base64
import android.util.Base64OutputStream
import android.util.Log
import java.io.ByteArrayOutputStream

/**
 * Helper object for contacts-related operations.
 */
object ContactsHelper {
    /**
     * The tag used for logging.
     */
    private const val TAG: String = "ContactsHelper"

    data class PhoneNumberLookupResult(
        val name: String? = null,
        val photoId: String? = null
    )

    /**
     * Lookup the name and photoID of a contact given a phone number.
     *
     * Only the first match is returned.
     *
     * @param context the context running the request.
     * @param number the phone number to lookup.
     *
     * @return a map containing the `name` and `photoID` of the contact, or an empty map if the contact could not be found.
     */
    @JvmStatic
    fun phoneNumberLookup(
        context: Context,
        number: String
    ): PhoneNumberLookupResult {
        val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val columns = arrayOf(
            PhoneLookup.DISPLAY_NAME,
            PhoneLookup.PHOTO_URI,
        )
        try {
            context.contentResolver.query(uri, columns, null, null, null).use { cursor ->
                // Take the first match only
                if (cursor != null && cursor.moveToFirst()) {
                    val name = cursor.getColumnIndex(PhoneLookup.DISPLAY_NAME).takeIf { it != -1 }?.let {
                        cursor.getString(it)
                    }
                    val photoId = cursor.getColumnIndex(PhoneLookup.PHOTO_URI).takeIf { it != -1 }?.let {
                        cursor.getString(it)
                    }
                    return PhoneNumberLookupResult(name, photoId)
                }
            }
        } catch (ignore: Exception) {
        }
        return PhoneNumberLookupResult()
    }

    /**
     * Get the base64 encoded photo for a contact.
     *
     * @param context the context running the request.
     * @param photoId the photoId of the contact.
     *
     * @return the base64 encoded photo, or an empty string if the photo could not be encoded or [photoId] is null.
     */
    @JvmStatic
    fun photoId64Encoded(
        context: Context,
        photoId: String?
    ): String { // TODO: Make photoId notnull, make return type nullable to tag error
        if (photoId == null) {
            return ""
        }
        val photoUri = Uri.parse(photoId)

        val encodedPhoto = ByteArrayOutputStream()
        try {
            context.contentResolver.openInputStream(photoUri).use { input ->
                Base64OutputStream(encodedPhoto, Base64.DEFAULT).use { output ->
                    input!!.copyTo(output, 1024)
                }
            }
            return encodedPhoto.toString()
        } catch (ex: Exception) {
            Log.e(TAG, "Error encoding photo", ex)
            return ""
        }
    }

    /**
     * Get VCards using serial database lookups. This is tragically slow, so call only when needed.
     *
     * There is a faster API specified using ContactsContract.Contacts.CONTENT_MULTI_VCARD_URI,
     * but there does not seem to be a way to figure out which ID resulted in which VCard using that API.
     *
     * @param context [android.content.Context] running the request.
     * @param ids collection of uIDs to look up.
     * @return map of uIDs to the corresponding VCard.
     */
    private fun getVCardsSlow(context: Context, ids: Collection<uID>): Map<uID, VCardBuilder> {
        return ids.mapNotNull { id ->
            val lookupKey = id.toString()
            val vcardURI =
                Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, lookupKey)

            try {
                context.contentResolver.openInputStream(vcardURI).use { input ->
                    if (input == null) {
                        Log.w("Contacts", "ContentResolver did not give us a stream for the VCard for uID $id")
                        return@mapNotNull null
                    }
                    id to VCardBuilder(input.bufferedReader().readText())
                }
            } catch (e: Exception) {
                Log.e("Contacts", "Exception while fetching vcards", e)
                null
            }
        }.toMap()
    }

    /**
     * Get the VCard for every specified raw contact ID.
     *
     * @param context [android.content.Context] running the request.
     * @param ids collection of raw contact IDs to look up.
     * @return map of raw contact IDs to the corresponding VCard.
     */
    @JvmStatic
    fun getVCardsForContactIDs(context: Context, ids: Collection<uID>): Map<uID, VCardBuilder> {
        return getVCardsSlow(context, ids)
    }

    /**
     * Get the last-modified timestamp for every contact in the database.
     *
     * @param context [android.content.Context] running the request.
     * @return map of contact uID to last-modified timestamp.
     */
    @JvmStatic
    fun getAllContactTimestamps(context: Context): Map<uID, Long> {
        val projection =
            arrayOf(uID.COLUMN, ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP)

        val databaseValues = accessContactsDatabase(context, projection, null, null, null)

        val timestamps: Map<uID, Long> = databaseValues.keys.associateWith { contactId ->
            val data = databaseValues[contactId]!!
            data[ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP]!!.toLong()
        }

        return timestamps
    }

    /**
     * Get the last-modified timestamp for the specified contact.
     *
     * @param context [android.content.Context] running the request.
     * @param contactID contact uID to read.
     * @throws ContactNotFoundException if the given ID for some reason does not match a contact.
     * @return last-modified timestamp of the contact.
     */
    @JvmStatic
    @Throws(ContactNotFoundException::class)
    fun getContactTimestamp(context: Context, contactID: uID): Long {
        val projection =
            arrayOf(uID.COLUMN, ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP)
        val selection = uID.COLUMN + " = ?"
        val selectionArgs = arrayOf(contactID.toString())

        val databaseValue =
            accessContactsDatabase(context, projection, selection, selectionArgs, null)

        if (databaseValue.isEmpty()) {
            throw ContactNotFoundException("Querying for contact with id $contactID returned no results.")
        }

        if (databaseValue.size != 1) {
            Log.w(
                TAG,
                "Received an improper number of return values from the database in getContactTimestamp: ${databaseValue.size}"
            )
        }

        val timestamp =
            databaseValue[contactID]!![ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP]!!.toLong()

        return timestamp
    }

    /**
     * Return a mapping of contact IDs to a map of the requested data from the Contacts database.
     *
     * @param context [android.content.Context] running the request.
     * @param projection list of column names to extract, defined in [ContactsContract.Contacts], must contain [uID.COLUMN].
     * @param selection parameterizable filter to use with the [ContentResolver] query.
     * @param selectionArgs parameters for selection.
     * @param sortOrder sort order to request from the [ContentResolver] query.
     * @return map of contact uIDs to desired values, which are a mapping of column names to the data contained there.
     */
    private fun accessContactsDatabase(
        context: Context,
        projection: Array<String>,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Map<uID, Map<String, String>> {
        val contactsUri = ContactsContract.Contacts.CONTENT_URI

        val toReturn = HashMap<uID, Map<String, String>>()

        context.contentResolver.query(
            contactsUri,
            projection,
            selection,
            selectionArgs,
            sortOrder
        ).use { contactsCursor ->
            if (contactsCursor != null && contactsCursor.moveToFirst()) {
                do {
                    val requestedData: MutableMap<String, String> = HashMap()

                    val uIDIndex = contactsCursor.getColumnIndexOrThrow(uID.COLUMN)
                    val uID = uID(contactsCursor.getString(uIDIndex)!!)

                    // For each column, collect the data from that column
                    for (column in projection) {
                        val index = contactsCursor.getColumnIndex(column)
                        if (index == -1) {
                            // This contact didn't have the requested column? Something is very wrong.
                            // If you are experiencing this, please open a bug report indicating how you got here
                            Log.e(TAG, "Got a contact which does not have a requested column")
                            continue
                        }
                        // Since we might be getting various kinds of data, Object is the best we can do
                        val data = contactsCursor.getString(index)

                        requestedData[column] = data
                    }

                    toReturn[uID] = requestedData
                } while (contactsCursor.moveToNext())
            }
        }
        return toReturn
    }

    /**
     * This is a cheap ripoff of com.android.vcard.VCardBuilder.
     *
     * Maybe in the future that library will be made public and we can switch to using that!
     *
     * The main similarity is the usage of .toString() to produce the finalized VCard and the
     * usage of .appendLine(String, String) to add stuff to the vcard.
     *
     * @param vcard the vcard to build upon.
     */
    class VCardBuilder internal constructor(vcard: String) {
        private val vcardBody: StringBuilder = StringBuilder(
            // Remove the end tag. We will add it back on in .toString()
            // Throws if VCARD_END is missing, so a malformed vcard fails closed rather than being silently accepted.
            vcard.substring(0, vcard.indexOf(VCARD_END))
        )

        /**
         * Appends one line with a given property name and value.
         *
         * Please note that this method does not check the validity of the property name and value.
         * So, you need to make sure that the property name and value are valid.
         *
         * @param propertyName the name of the property to append.
         * @param rawValue the value of the property to append.
         */
        fun appendLine(propertyName: String, rawValue: String) {
            vcardBody.append(propertyName)
                .append(VCARD_DATA_SEPARATOR)
                .append(rawValue)
                .append("\n")
        }

        /**
         * Converts the VCard to standard VCard format.
         *
         * Please note that this method does not check the validity of the VCard.
         * So, you need to make sure that the VCard is valid.
         *
         * @return the VCard in standard VCard format.
         */
        override fun toString(): String = vcardBody.toString() + VCARD_END

        companion object {
            const val VCARD_END: String = "END:VCARD" // Written to terminate the vcard
            const val VCARD_DATA_SEPARATOR: String = ":"
        }
    }

    /**
     * Essentially a typedef of the type used for a unique identifier.
     *
     * @param contactLookupKey the lookup key of the contact.
     * We use the LOOKUP_KEY column of the Contacts table as a unique ID, since that's what it's.
     */
    @Suppress("ClassName")
    class uID(val contactLookupKey: String) {
        override fun toString(): String = this.contactLookupKey

        override fun hashCode(): Int = contactLookupKey.hashCode()

        override fun equals(other: Any?): Boolean {
            if (other is uID) {
                return contactLookupKey == other.contactLookupKey
            }
            return contactLookupKey == other
        }

        companion object {
            /**
             * Which Contacts column this uID is pulled from
             */
            const val COLUMN: String = ContactsContract.Contacts.LOOKUP_KEY
        }
    }

    /**
     * Exception to indicate that a specified contact was not found.
     */
    class ContactNotFoundException : Exception {
        constructor(contactId: uID) : super("Unable to find contact with ID $contactId")

        constructor(message: String?) : super(message)
    }
}
