/*
 * Copyright 2020 Aniket Kumar <anikketkumar786@gmail.com>
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

