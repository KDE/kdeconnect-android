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

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.webkit.MimeTypeMap;

import org.kde.kdeconnect.NetworkPacket;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;

public class FilesHelper {

    public static final String LOG_TAG = "SendFileActivity";

    private static String getFileExt(String filename) {
        //return MimeTypeMap.getFileExtensionFromUrl(filename);
        return filename.substring((filename.lastIndexOf(".") + 1));
    }

    public static String getFileNameWithoutExt(String filename) {
        int dot = filename.lastIndexOf(".");
        return (dot < 0) ? filename : filename.substring(0, dot);
    }

    public static String getMimeTypeFromFile(String file) {
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(getFileExt(file));
        if (mime == null) mime = "*/*";
        return mime;
    }

    public static String findNonExistingNameForNewFile(String path, String filename) {
        int dot = filename.lastIndexOf(".");
        String name = (dot < 0) ? filename : filename.substring(0, dot);
        String ext = (dot < 0) ? "" : filename.substring(filename.lastIndexOf("."));

        int num = 1;
        while (new File(path + "/" + filename).exists()) {
            filename = name + " (" + num + ")" + ext;
            num++;
        }

        return filename;
    }

    //Following code from http://activemq.apache.org/maven/5.7.0/kahadb/apidocs/src-html/org/apache/kahadb/util/IOHelper.html

    /**
     * Converts any string into a string that is safe to use as a file name.
     * The result will only include ascii characters and numbers, and the "-","_", and "." characters.
     */
    private static String toFileSystemSafeName(String name, boolean dirSeparators, int maxFileLength) {
        int size = name.length();
        StringBuilder rc = new StringBuilder(size * 2);
        for (int i = 0; i < size; i++) {
            char c = name.charAt(i);
            boolean valid = c >= 'a' && c <= 'z';
            valid = valid || (c >= 'A' && c <= 'Z');
            valid = valid || (c >= '0' && c <= '9');
            valid = valid || (c == '_') || (c == '-') || (c == '.');
            valid = valid || (dirSeparators && ((c == '/') || (c == '\\')));

            if (valid) {
                rc.append(c);
            }
        }
        String result = rc.toString();
        if (result.length() > maxFileLength) {
            result = result.substring(result.length() - maxFileLength);
        }
        return result;
    }

    public static String toFileSystemSafeName(String name, boolean dirSeparators) {
        return toFileSystemSafeName(name, dirSeparators, 255);
    }

    public static String toFileSystemSafeName(String name) {
        return toFileSystemSafeName(name, true, 255);
    }

    private static int GetOpenFileCount() {
        return new File("/proc/self/fd").listFiles().length;
    }

    public static void LogOpenFileCount() {
        Log.e("KDE/FileCount", "" + GetOpenFileCount());
    }


    //Create the network package from the URI
    public static NetworkPacket uriToNetworkPacket(final Context context, final Uri uri, String type) {

        try {

            ContentResolver cr = context.getContentResolver();
            InputStream inputStream = cr.openInputStream(uri);

            NetworkPacket np = new NetworkPacket(type);

            String filename = null;
            long size = -1;
            Long lastModified = null;

            if (uri.getScheme().equals("file")) {
                // file:// is a non media uri, so we cannot query the ContentProvider

                try {
                    File mFile = new File(uri.getPath());

                    filename = mFile.getName();
                    size = mFile.length();
                    lastModified = mFile.lastModified();
                } catch (NullPointerException e) {
                    Log.e(LOG_TAG, "Received bad file URI", e);
                }

            } else {
                // Since we used Intent.CATEGORY_OPENABLE, these two columns are the only ones we are
                // guaranteed to have: https://developer.android.com/reference/android/provider/OpenableColumns
                String[] proj = {
                        OpenableColumns.SIZE,
                        OpenableColumns.DISPLAY_NAME,
                };

                try (Cursor cursor = cr.query(uri, proj, null, null, null)) {
                    int nameColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
                    int sizeColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE);
                    cursor.moveToFirst();

                    filename = cursor.getString(nameColumnIndex);

                    // It is recommended to check for the value to be null because there are
                    // situations were we don't know the size (for instance, if the file is
                    // not local to the device)
                    if (!cursor.isNull(sizeColumnIndex)) {
                        size = cursor.getLong(sizeColumnIndex);
                    }

                    lastModified = getLastModifiedTime(context, uri);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Problem getting file information", e);
                }
            }

            if (filename != null) {
                np.set("filename", filename);
            } else {
                // It would be very surprising if this happens
                Log.e(LOG_TAG, "Unable to read filename");
            }

            if (lastModified != null) {
                np.set("lastModified", lastModified);
            } else {
                // This would not be too surprising, and probably means we need to improve
                // FilesHelper.getLastModifiedTime
                Log.w(LOG_TAG, "Unable to read file last modified time");
            }

            np.setPayload(new NetworkPacket.Payload(inputStream, size));

            return np;
        } catch (Exception e) {
            Log.e(LOG_TAG, "Exception creating network packet", e);
            return null;
        }
    }

    /**
     * By hook or by crook, get the last modified time of the passed content:// URI
     *
     * This is a challenge because different content sources have different columns defined, and
     * I don't know how to tell what the source of the content is.
     *
     * Therefore, my brilliant solution is to just try everything until something works.
     *
     * Will return null if nothing worked.
     */
    public static Long getLastModifiedTime(final Context context, final Uri uri) {
        ContentResolver cr = context.getContentResolver();

        Long lastModifiedTime = null;

        // Open a cursor without a column because we do not yet know what columns are defined
        try (Cursor cursor = cr.query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String[] allColumns = cursor.getColumnNames();

                // MediaStore.MediaColumns.DATE_MODIFIED resolves to "date_modified"
                // I see this column defined in case we used the Gallery app to select the file to transfer
                // This can occur both for devices running Storage Access Framework (SAF) if we select
                // the Gallery to provide the file to transfer, as well as for older devices by doing the same
                int mediaDataModifiedColumnIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED);

                // DocumentsContract.Document.COLUMN_LAST_MODIFIED resolves to "last_modified"
                // I see this column defined when, on a device using SAF we select a file using the
                // file browser
                // According to https://developer.android.com/reference/kotlin/android/provider/DocumentsContract
                // all "document providers" must provide certain columns. Do we actually have a DocumentProvider here?
                // I do not think this code path will ever happen for a non-media file is selected on
                // an API < KitKat device, since those will be delivered as a file:// URI and handled
                // accordingly. Therefore, it is safe to ignore the warning that this field requires
                // API 19
                @SuppressLint("InlinedApi")
                int documentLastModifiedColumnIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED);

                // If we have an image, it may be the case that MediaStore.MediaColumns.DATE_MODIFIED
                // catches the modification date, but if not, here is another column we can look for.
                // This should be checked *after* DATE_MODIFIED since I think that column might give
                // better information
                int imageDateTakenColumnIndex = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATE_TAKEN);

                // Report whether the captured timestamp is in milliseconds or seconds
                // The truthy-ness of this value for each different type of column is known from either
                // experimentation or the docs (when docs exist...)
                boolean milliseconds;

                int properColumnIndex;
                if (mediaDataModifiedColumnIndex >= 0) {
                    properColumnIndex = mediaDataModifiedColumnIndex;
                    milliseconds = false;
                } else if (documentLastModifiedColumnIndex >= 0) {
                    properColumnIndex = documentLastModifiedColumnIndex;
                    milliseconds = true;
                } else if (imageDateTakenColumnIndex >= 0) {
                    properColumnIndex = imageDateTakenColumnIndex;
                    milliseconds = true;
                } else {
                    // Nothing worked :(
                    String formattedColumns = Arrays.toString(allColumns);
                    Log.w("SendFileActivity", "Unable to get file modification time. Available columns were: " + formattedColumns);
                    return null;
                }

                if (!cursor.isNull(properColumnIndex)) {
                    lastModifiedTime = cursor.getLong(properColumnIndex);
                }

                if (!milliseconds) {
                    lastModifiedTime *= 1000;
                    milliseconds = true;
                }
            }
        }
        return lastModifiedTime;
    }
}
