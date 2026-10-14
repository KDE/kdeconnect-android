/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui.background

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

internal data class DisconnectLogEntry(
    val timestamp: Long,
    val deviceName: String,
    val connected: Boolean,
)

internal object DisconnectLog {
    private const val PREFERENCES = "ecosystem_background"
    private const val KEY_ENTRIES = "disconnect_log"
    private const val MAX_ENTRIES = 30

    @Synchronized
    fun record(context: Context, deviceName: String, connected: Boolean) {
        val entries = entries(context).toMutableList()
        entries.add(0, DisconnectLogEntry(System.currentTimeMillis(), deviceName, connected))
        val json = JSONArray()
        entries.take(MAX_ENTRIES).forEach { entry ->
            json.put(JSONObject()
                .put("timestamp", entry.timestamp)
                .put("device", entry.deviceName)
                .put("connected", entry.connected))
        }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit {
            putString(KEY_ENTRIES, json.toString())
        }
    }

    @Synchronized
    fun entries(context: Context): List<DisconnectLogEntry> = runCatching {
        val stored = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY_ENTRIES, null)
            ?: return emptyList()
        val json = JSONArray(stored)
        (0 until json.length()).mapNotNull { index ->
            val item = json.optJSONObject(index) ?: return@mapNotNull null
            val timestamp = item.optLong("timestamp", 0)
            val name = item.optString("device", "").takeIf(String::isNotBlank) ?: return@mapNotNull null
            if (timestamp <= 0) return@mapNotNull null
            DisconnectLogEntry(timestamp, name, item.optBoolean("connected"))
        }
    }.getOrDefault(emptyList())
}
