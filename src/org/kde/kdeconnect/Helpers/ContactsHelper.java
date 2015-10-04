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
import android.provider.ContactsContract.PhoneLookup;

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
            int nameIndex = cursor.getColumnIndex(PhoneLookup.DISPLAY_NAME);
            if (nameIndex != -1) {
                String name = cursor.getString(nameIndex);
                //Log.e("PhoneNumberLookup", "success: " + name);
                cursor.close();
                return name;
            }
        }

        if (cursor != null) cursor.close();

        return number;

    }
}
