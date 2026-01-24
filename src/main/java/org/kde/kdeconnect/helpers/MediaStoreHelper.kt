/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri

object MediaStoreHelper {
    // Maybe this class could batch successive calls together
    @JvmStatic
    fun indexFile(context: Context, path: Uri?) {
        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        mediaScanIntent.setData(path)
        context.sendBroadcast(mediaScanIntent)
    }
}
