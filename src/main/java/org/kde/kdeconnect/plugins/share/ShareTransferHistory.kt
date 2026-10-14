/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.share

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal enum class TransferDirection {
    SENT,
    RECEIVED,
}

internal enum class TransferStatus {
    SENDING,
    SENT,
    RECEIVING,
    RECEIVED,
    FAILED,
}

internal data class ShareTransferRecord(
    val id: String,
    val fileName: String,
    val size: Long,
    val direction: TransferDirection,
    val status: TransferStatus,
    val progress: Int,
    val deviceName: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

internal object ShareTransferHistory {
    const val PREFERENCE_KEY = "ecosystem_share_transfer_history"
    private const val MAX_RECORDS = 50
    private val lock = Any()

    fun newId(): String = UUID.randomUUID().toString()

    fun get(context: Context): List<ShareTransferRecord> = synchronized(lock) {
        read(context)
    }

    fun upsert(context: Context, record: ShareTransferRecord) = synchronized(lock) {
        val updated = (read(context).filterNot { it.id == record.id } + record)
            .sortedByDescending(ShareTransferRecord::updatedAt)
            .take(MAX_RECORDS)
        val records = JSONArray()
        updated.forEach { entry -> records.put(entry.toJson()) }
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(PREFERENCE_KEY, records.toString())
            .apply()
    }

    private fun read(context: Context): List<ShareTransferRecord> {
        val json = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PREFERENCE_KEY, null) ?: return emptyList()
        return runCatching {
            val records = JSONArray(json)
            buildList(records.length()) {
                for (index in 0 until records.length()) {
                    val item = records.getJSONObject(index)
                    add(
                        ShareTransferRecord(
                            id = item.getString("id"),
                            fileName = item.getString("fileName"),
                            size = item.optLong("size", -1L),
                            direction = TransferDirection.valueOf(item.getString("direction")),
                            status = TransferStatus.valueOf(item.getString("status")),
                            progress = item.optInt("progress", 0).coerceIn(0, 100),
                            deviceName = item.optString("deviceName"),
                            updatedAt = item.optLong("updatedAt", 0L),
                        ),
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun ShareTransferRecord.toJson() = JSONObject().apply {
        put("id", id)
        put("fileName", fileName)
        put("size", size)
        put("direction", direction.name)
        put("status", status.name)
        put("progress", progress)
        put("deviceName", deviceName)
        put("updatedAt", updatedAt)
    }
}
