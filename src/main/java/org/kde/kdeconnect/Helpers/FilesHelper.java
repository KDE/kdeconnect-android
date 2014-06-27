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

}
