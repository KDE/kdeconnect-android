package org.kde.kdeconnect.Helpers;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.PhoneLookup;
import android.util.Log;

public class ContactsHelper {

    public static String phoneNumberLookup(Context context, String number) {

        //Log.e("PhoneNumberLookup", number);

        Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    uri,
                    new String[] {
                            PhoneLookup.DISPLAY_NAME
                            /*, PhoneLookup.TYPE
                              , PhoneLookup.LABEL
                              , PhoneLookup.ID */
                    },
                    null, null, null);
        } catch (IllegalArgumentException e) {
            return number;
        }

        // Take the first match only
        if (cursor != null && cursor.moveToFirst()) {
            int nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);
            if (nameIndex != -1) {
                String name = cursor.getString(nameIndex);
                //Log.e("PhoneNumberLookup", "success: " + name);
                cursor.close();
                return name + " (" + number + ")";
            }
        }

        if (cursor != null) cursor.close();

        return number;

    }
}