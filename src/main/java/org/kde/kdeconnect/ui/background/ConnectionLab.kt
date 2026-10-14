/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui.background

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

internal data class ConnectionLabRow(
    val scenario: String,
    val timestamp: Long,
    val noticeMs: Long,
    val recoveryMs: Long,
    val chosenLink: String,
    val lostItems: Int,
    val duplicateItems: Int,
    val clipboardDelayMs: Long,
    val transferMbps: Double,
    val batteryDrainPercent: Int,
    val reason: String,
)

/** Records measurements only while a named lab scenario is running. */
internal object ConnectionLab {
    private const val PREFS = "connection_lab"
    private const val KEY_ROWS = "results"
    private const val KEY_ACTIVE = "active_run"
    private const val MAX_ROWS = 300
    private var appContext: Context? = null
    private var scenario: String? = null
    private var pendingClipboardAtStart = 0
    private var startedAt = 0L
    private var dropAt = 0L
    private var noticedAt = 0L
    private var recoveredAt = 0L
    private var chosenLink = ""
    private var lostItems = 0
    private var duplicateItems = 0
    private var clipboardDelayMs = 0L
    private var clipboardDelaySamples = 0
    private var transferMbps = 0.0
    private var transferBytes = 0L
    private var transferElapsedMs = 0L
    private var batteryDrainPercent = -1
    private var batteryAtStart = -1
    private var reason = ""

    @Synchronized
    fun initialize(context: Context) {
        appContext = context.applicationContext
        val active = runCatching {
            JSONObject(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ACTIVE, "{}") ?: "{}")
        }.getOrDefault(JSONObject())
        if (active.optString("scenario").isBlank()) return
        scenario = active.optString("scenario")
        pendingClipboardAtStart = active.optInt("pendingClipboardAtStart")
        startedAt = active.optLong("startedAt")
        dropAt = active.optLong("dropAt")
        noticedAt = active.optLong("noticedAt")
        recoveredAt = active.optLong("recoveredAt")
        chosenLink = active.optString("chosenLink")
        lostItems = active.optInt("lostItems")
        duplicateItems = active.optInt("duplicateItems")
        clipboardDelayMs = active.optLong("clipboardDelayMs")
        clipboardDelaySamples = active.optInt("clipboardDelaySamples")
        transferMbps = active.optDouble("transferMbps")
        transferBytes = active.optLong("transferBytes")
        transferElapsedMs = active.optLong("transferElapsedMs")
        batteryDrainPercent = active.optInt("batteryDrainPercent", -1)
        batteryAtStart = active.optInt("batteryAtStart", -1)
        reason = active.optString("reason")
        event("resume", "Recovered active run after process restart")
    }

    @Synchronized
    fun start(context: Context, name: String, pendingClipboardCount: Int = 0) {
        appContext = context.applicationContext
        scenario = name.ifBlank { "Manual run" }
        pendingClipboardAtStart = pendingClipboardCount.coerceAtLeast(0)
        batteryAtStart = batteryPercent(context)
        startedAt = System.currentTimeMillis()
        dropAt = 0
        noticedAt = 0
        recoveredAt = 0
        chosenLink = ""
        lostItems = 0
        duplicateItems = 0
        clipboardDelayMs = 0
        clipboardDelaySamples = 0
        transferMbps = 0.0
        transferBytes = 0
        transferElapsedMs = 0
        batteryDrainPercent = -1
        reason = ""
        event("start", "")
    }

    @Synchronized
    fun markDrop(why: String = "Manual test mark") {
        if (scenario == null) return
        dropAt = System.currentTimeMillis()
        noticedAt = 0
        recoveredAt = 0
        reason = why
        event("drop-start", why)
    }

    @Synchronized
    fun recordNoticed(link: String, why: String) {
        if (scenario == null) return
        if (dropAt > 0 && noticedAt == 0L) noticedAt = System.currentTimeMillis()
        reason = why
        if (link.isNotBlank()) chosenLink = link
        event("drop-noticed", why)
    }

    @Synchronized
    fun recordRecovered(link: String, why: String = "") {
        if (scenario == null) return
        if (dropAt > 0 && recoveredAt == 0L) recoveredAt = System.currentTimeMillis()
        if (link.isNotBlank()) chosenLink = link
        if (why.isNotBlank()) reason = why
        event("recovered", why)
    }

    @Synchronized
    fun recordSwitch(from: String, to: String, why: String) {
        if (scenario == null) return
        chosenLink = to
        if (dropAt > 0 && recoveredAt == 0L) recoveredAt = System.currentTimeMillis()
        reason = why
        event("link-switch", "$from -> $to; $why")
    }

    @Synchronized
    fun recordChosenLink(link: String) {
        if (scenario != null && link.isNotBlank()) {
            chosenLink = link
            persistActive()
        }
    }

    @Synchronized
    fun recordNote(note: String) {
        if (scenario == null || note.isBlank()) return
        reason = note.take(500)
        event("note", reason)
    }

    @Synchronized
    fun recordClipboardDelay(delayMs: Long) {
        if (scenario == null) return
        clipboardDelayMs = ((clipboardDelayMs * clipboardDelaySamples) + delayMs.coerceAtLeast(0)) /
            (clipboardDelaySamples + 1)
        clipboardDelaySamples++
        persistActive()
    }

    @Synchronized
    fun recordClipboardLoss(count: Int = 1) {
        if (scenario != null) {
            lostItems += count.coerceAtLeast(0)
            persistActive()
        }
    }

    @Synchronized
    fun recordClipboardDuplicate(count: Int = 1) {
        if (scenario != null) {
            duplicateItems += count.coerceAtLeast(0)
            persistActive()
        }
    }

    @Synchronized
    fun recordTransfer(bytes: Long, elapsedMs: Long) {
        if (scenario == null || bytes <= 0 || elapsedMs <= 0) return
        transferBytes += bytes
        transferElapsedMs += elapsedMs
        transferMbps = transferBytes * 8.0 / transferElapsedMs / 1000.0
        persistActive()
    }

    @Synchronized
    fun recordBatteryDrain(percent: Int) {
        if (scenario != null) {
            batteryDrainPercent = percent.coerceIn(0, 100)
            persistActive()
        }
    }

    @Synchronized
    fun finish(context: Context, pendingClipboardCount: Int = 0): ConnectionLabRow? {
        val name = scenario ?: return null
        lostItems += (pendingClipboardCount - pendingClipboardAtStart).coerceAtLeast(0)
        if (batteryDrainPercent < 0 && batteryAtStart >= 0) {
            val batteryNow = batteryPercent(context)
            if (batteryNow >= 0) batteryDrainPercent = (batteryAtStart - batteryNow).coerceAtLeast(0)
        }
        val row = ConnectionLabRow(
            scenario = name,
            timestamp = startedAt,
            noticeMs = if (dropAt > 0 && noticedAt > 0) noticedAt - dropAt else -1,
            recoveryMs = if (dropAt > 0 && recoveredAt > 0) recoveredAt - dropAt else -1,
            chosenLink = chosenLink,
            lostItems = lostItems,
            duplicateItems = duplicateItems,
            clipboardDelayMs = clipboardDelayMs,
            transferMbps = transferMbps,
            batteryDrainPercent = batteryDrainPercent,
            reason = reason,
        )
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = runCatching { JSONArray(prefs.getString(KEY_ROWS, "[]")) }.getOrDefault(JSONArray())
        val all = JSONArray()
        (0 until existing.length()).toList().takeLast(MAX_ROWS - 1).forEach { all.put(existing.get(it)) }
        all.put(row.toJson())
        prefs.edit { putString(KEY_ROWS, all.toString()); remove(KEY_ACTIVE) }
        scenario = null
        event("finish", "")
        return row
    }

    fun rows(context: Context): List<ConnectionLabRow> {
        val json = runCatching {
            JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ROWS, "[]"))
        }.getOrDefault(JSONArray())
        return (0 until json.length()).mapNotNull { index ->
            runCatching { json.getJSONObject(index).toRow() }.getOrNull()
        }.asReversed()
    }

    fun exportTsv(context: Context): String = buildString {
        appendLine("scenario\ttime\tnotice_ms	recovery_ms\tchosen_link\tlost_items\tduplicate_items\tclipboard_delay_ms\ttransfer_Mbps\tbattery_drain_percent\treason")
        rows(context).asReversed().forEach { row ->
            appendLine(listOf(row.scenario, row.timestamp, row.noticeMs, row.recoveryMs, row.chosenLink,
                row.lostItems, row.duplicateItems, row.clipboardDelayMs, "%.3f".format(row.transferMbps),
                row.batteryDrainPercent, row.reason).joinToString("\t") { it.toString().replace('\t', ' ') })
        }
    }

    @Synchronized
    private fun event(type: String, detail: String) {
        val context = appContext ?: return
        persistActive()
        android.util.Log.i("ConnectionLab", "$type scenario=${scenario.orEmpty()} detail=$detail")
    }

    @Synchronized
    private fun persistActive() {
        val context = appContext ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (scenario == null) {
            prefs.edit { remove(KEY_ACTIVE) }
            return
        }
        prefs.edit {
            putString(KEY_ACTIVE, JSONObject()
                .put("scenario", scenario).put("pendingClipboardAtStart", pendingClipboardAtStart)
                .put("startedAt", startedAt).put("dropAt", dropAt).put("noticedAt", noticedAt)
                .put("recoveredAt", recoveredAt).put("chosenLink", chosenLink).put("lostItems", lostItems)
                .put("duplicateItems", duplicateItems).put("clipboardDelayMs", clipboardDelayMs)
                .put("clipboardDelaySamples", clipboardDelaySamples).put("transferMbps", transferMbps)
                .put("transferBytes", transferBytes).put("transferElapsedMs", transferElapsedMs)
                .put("batteryDrainPercent", batteryDrainPercent).put("batteryAtStart", batteryAtStart)
                .put("reason", reason).toString())
        }
    }

    private fun ConnectionLabRow.toJson() = JSONObject()
        .put("scenario", scenario).put("timestamp", timestamp).put("noticeMs", noticeMs)
        .put("recoveryMs", recoveryMs).put("chosenLink", chosenLink).put("lostItems", lostItems)
        .put("duplicateItems", duplicateItems).put("clipboardDelayMs", clipboardDelayMs)
        .put("transferMbps", transferMbps).put("batteryDrainPercent", batteryDrainPercent).put("reason", reason)

    private fun JSONObject.toRow() = ConnectionLabRow(
        scenario = optString("scenario"), timestamp = optLong("timestamp"), noticeMs = optLong("noticeMs", -1),
        recoveryMs = optLong("recoveryMs", -1), chosenLink = optString("chosenLink"),
        lostItems = optInt("lostItems"), duplicateItems = optInt("duplicateItems"),
        clipboardDelayMs = optLong("clipboardDelayMs"), transferMbps = optDouble("transferMbps"),
        batteryDrainPercent = optInt("batteryDrainPercent", -1), reason = optString("reason"),
    )

    private fun batteryPercent(context: Context): Int {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return -1
        val level = battery.getIntExtra("level", -1)
        val scale = battery.getIntExtra("scale", -1)
        if (level < 0 || scale <= 0) return -1
        return (level * 100 / scale).coerceIn(0, 100)
    }
}
