/*
 * SPDX-FileCopyrightText: 2020 Aniket Kumar <anikketkumar786@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.SMSPlugin;

public final class MimeType {

    public static final String TYPE_TEXT = "text/plain";
    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_VIDEO = "video";
    public static final String TYPE_AUDIO = "audio";

    public static boolean isTypeText(String mimeType) { return  mimeType.startsWith(TYPE_TEXT); }

    public static boolean isTypeImage(String mimeType) {
        return mimeType.startsWith(TYPE_IMAGE);
    }

    public static boolean isTypeVideo(String mimeType) { return mimeType.startsWith(TYPE_VIDEO); }

    public static boolean isTypeAudio(String mimeType) { return mimeType.startsWith(TYPE_AUDIO); }

    public static String postfixOf(String mimeType) { return mimeType.substring(mimeType.lastIndexOf('/')+1); }
}

