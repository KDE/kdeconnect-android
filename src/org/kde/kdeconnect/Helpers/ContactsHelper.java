/*
 * Copyright 2014 Albert Vaca Cintora <albertvaka@gmail.com>
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
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.PhoneLookup;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class ContactsHelper {

    public static Map<String, String> phoneNumberLookup(Context context, String number) {

        //Log.e("PhoneNumberLookup", number);

        Map<String, String> contactInfo = new HashMap<String, String>();

        Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    uri,
                    new String[] {
                            PhoneLookup.DISPLAY_NAME,
                            ContactsContract.PhoneLookup.PHOTO_URI
                            /*, PhoneLookup.TYPE
                              , PhoneLookup.LABEL
                              , PhoneLookup.ID */
                    },
                    null, null, null);
        } catch (IllegalArgumentException e) {
            return contactInfo;
        }

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

            if (!contactInfo.isEmpty()) {
                cursor.close();
                return contactInfo;
            }
        }

        return contactInfo;
    }

    public static String photoId64Encoded(Context context, String photoId) {
        if (photoId == null) {
            return new String();
        }
        Uri photoUri = Uri.parse(photoId);
        Uri displayPhotoUri = Uri.withAppendedPath(photoUri, ContactsContract.Contacts.Photo.DISPLAY_PHOTO);

        byte[] buffer = null;
        Base64OutputStream out = null;
        ByteArrayOutputStream encodedPhoto = null;
        try {
            encodedPhoto = new ByteArrayOutputStream();
            out = new Base64OutputStream(encodedPhoto, Base64.DEFAULT);
            InputStream fd2 = context.getContentResolver().openInputStream(photoUri);
            buffer = new byte[1024];
            int len;
            while ((len = fd2.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            return encodedPhoto.toString();
        } catch (Exception ex) {
            Log.e("ContactsHelper", ex.toString());
            return new String();
        }
    }
}

