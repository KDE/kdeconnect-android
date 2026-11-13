/*
 * SPDX-FileCopyrightText: 2026 Albert Vaca Cintora <albert.vacacintora@datadoghq.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File

/**
 * Receives the result of SmsManager.sendMultimediaMessage, once the system is done with the
 * temporary PDU file we passed to it, and deletes that file.
 */
class MmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.v(TAG, "MMS send finished with result code $resultCode")
        val fileName = intent.getStringExtra(EXTRA_PDU_FILE_NAME) ?: return
        deletePduFile(context, fileName)
    }

    companion object {
        private const val TAG = "MmsSentReceiver"
        const val EXTRA_PDU_FILE_NAME = "org.kde.kdeconnect.plugins.sms.PDU_FILE_NAME"

        fun deletePduFile(context: Context, fileName: String) {
            // Only file names we generated, so this can never delete anything outside cacheDir
            if (fileName.contains('/')) return
            val file = File(context.cacheDir, fileName)
            if (file.exists() && !file.delete()) {
                Log.w(TAG, "Could not delete temporary PDU file $fileName")
            }
        }
    }
}
