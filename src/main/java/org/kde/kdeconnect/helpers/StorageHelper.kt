/*
 * SPDX-FileCopyrightText: 2024 TPJ Schikhof <kde@schikhof.eu>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.helpers

import android.net.Uri

object StorageHelper {
    fun getDisplayName(treeUri: Uri): String {
        val pathSegments = treeUri.pathSegments
        require(pathSegments[0] == "tree") { "treeUri is not valid" }

        val segmentsWithoutTree = pathSegments.drop(1).joinToString("/")
        return segmentsWithoutTree.split(":").last(String::isNotBlank)
    }
}
