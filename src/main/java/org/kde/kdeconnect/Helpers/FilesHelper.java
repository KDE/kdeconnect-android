package org.kde.kdeconnect.Helpers;

import android.webkit.MimeTypeMap;

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
     *
     * @param name
     * @param dirSeparators
     * @param maxFileLength
     * @return
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

}
