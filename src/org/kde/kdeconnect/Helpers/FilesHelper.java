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

import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.File;

public class FilesHelper {

    public static String getFileExt(String fileName) {
        //return MimeTypeMap.getFileExtensionFromUrl(fileName);
        return fileName.substring((fileName.lastIndexOf(".") + 1), fileName.length());
    }

    public static String getMimeTypeFromFile(String file) {
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(getFileExt(file));
        if (mime == null) mime = "*/*";
        return mime;
    }

    //Following code from http://activemq.apache.org/maven/5.7.0/kahadb/apidocs/src-html/org/apache/kahadb/util/IOHelper.html
    /**
     * Converts any string into a string that is safe to use as a file name.
     * The result will only include ascii characters and numbers, and the "-","_", and "." characters.
     */
    public static String toFileSystemSafeName(String name, boolean dirSeparators, int maxFileLength) {
        int size = name.length();
        StringBuilder rc = new StringBuilder(size * 2);
        for (int i = 0; i < size; i++) {
            char c = name.charAt(i);
            boolean valid = c >= 'a' && c <= 'z';
            valid = valid || (c >= 'A' && c <= 'Z');
            valid = valid || (c >= '0' && c <= '9');
            valid = valid || (c == '_') || (c == '-') || (c == '.');
            valid = valid || (dirSeparators && ( (c == '/') || (c == '\\')));

            if (valid) {
                rc.append(c);
            }
        }
        String result = rc.toString();
        if (result.length() > maxFileLength) {
            result = result.substring(result.length()-maxFileLength,result.length());
        }
        return result;
    }
    public static String toFileSystemSafeName(String name, boolean dirSeparators) {
        return toFileSystemSafeName(name, dirSeparators, 255);
    }
    public static String toFileSystemSafeName(String name) {
        return toFileSystemSafeName(name, true, 255);
    }

    public static int GetOpenFileCount() {
        return new File("/proc/self/fd").listFiles().length;
    }

    public static void LogOpenFileCount() {
        Log.e("KDE/FileCount",""+GetOpenFileCount());
    }
}
