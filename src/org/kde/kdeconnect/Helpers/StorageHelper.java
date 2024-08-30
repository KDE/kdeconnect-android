/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Helpers;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;

import java.util.List;

//Code from http://stackoverflow.com/questions/9340332/how-can-i-get-the-list-of-mounted-external-storage-of-android-device/19982338#19982338
//modified to work on Lollipop and other devices
public class StorageHelper {

    /* treeUri                                                                       documentId
     * ==================================================================================================
     * content://com.android.providers.downloads.documents/tree/downloads         => downloads
     * content://com.android.externalstorage.documents/tree/1715-1D1F:            => 1715-1D1F:
     * content://com.android.externalstorage.documents/tree/1715-1D1F:My%20Photos => 1715-1D1F:My Photos
     * content://com.android.externalstorage.documents/tree/primary:              => primary:
     * content://com.android.externalstorage.documents/tree/primary:DCIM          => primary:DCIM
     * content://com.android.externalstorage.documents/tree/primary:Download/bla  => primary:Download/bla
     */
    public static String getDisplayName(@NonNull Context context, @NonNull Uri treeUri) {
        List<String> pathSegments = treeUri.getPathSegments();

        if (!pathSegments.get(0).equals("tree")) {
            throw new IllegalArgumentException("treeUri is not valid");
        }

        String documentId = DocumentsContract.getTreeDocumentId(treeUri);

        int colonIdx = pathSegments.get(1).indexOf(':');

        if (colonIdx >= 0) {
            String tree = pathSegments.get(1).substring(0, colonIdx + 1);

            if (!documentId.equals(tree)) {
                return documentId.substring(tree.length());
            } else {
                return documentId.substring(0, colonIdx);
            }
        }

        return documentId;
    }
}
