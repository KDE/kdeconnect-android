/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui.background

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import org.json.JSONObject
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.helpers.ThreadHelper
import org.kde.kdeconnect.plugins.clipboard.ClipboardSyncManager
import rikka.shizuku.Shizuku

internal data class KeepAliveReport(
    val batteryExempt: Boolean = false,
    val backgroundAllowed: Boolean = false,
    val standbyProtected: Boolean = false,
    val checkedAt: Long = 0,
    val error: Boolean = false,
)

internal object KeepAliveSupport {
    private lateinit var appContext: Context
    private val handler = Handler(Looper.getMainLooper())
    private var initialized = false
    private var bound = false
    private var service: IKeepAliveService? = null
    private var applyWhenConnected = false
    var report by mutableStateOf(KeepAliveReport())
        private set
    var busy by mutableStateOf(false)
        private set
    private val args by lazy {
        Shizuku.UserServiceArgs(ComponentName(appContext, KeepAliveUserService::class.java))
            .tag("ecosystem_keep_alive").version(2).daemon(false).processNameSuffix("keep_alive")
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = IKeepAliveService.Stub.asInterface(binder)
            busy = false
            refresh(applyWhenConnected)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
            busy = false
        }

        override fun onBindingDied(name: ComponentName?) = onServiceDisconnected(name)
        override fun onNullBinding(name: ComponentName?) = onServiceDisconnected(name)
    }

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        val stored = appContext.getSharedPreferences("ecosystem_background", Context.MODE_PRIVATE)
            .getString("keep_alive_report", null)
        if (stored != null) report = parse(stored)
        Shizuku.addBinderReceivedListenerSticky { handler.post { refresh(apply = true) } }
        Shizuku.addBinderDeadListener { handler.post { connection.onServiceDisconnected(null) } }
        Shizuku.addRequestPermissionResultListener { _, _ -> handler.post { refresh(apply = true) } }
    }

    fun refresh(apply: Boolean = false) {
        if (!initialized || busy || !ShizukuSupport.state(appContext).authorized) return
        val remote = service
        if (remote == null) {
            if (bound) return
            applyWhenConnected = apply
            busy = true
            try {
                bound = true
                Shizuku.bindUserService(args, connection)
            } catch (e: RuntimeException) {
                bound = false
                busy = false
                report = report.copy(error = true)
                Log.w("EcosystemKeepAlive", "Could not bind power-settings service", e)
            }
            return
        }
        busy = true
        ThreadHelper.execute {
            val result = runCatching {
                if (apply) remote.applyProtections(appContext.packageName)
                else remote.inspectProtections(appContext.packageName)
            }
            handler.post {
                busy = false
                result.onSuccess { json ->
                    report = parse(json)
                    appContext.getSharedPreferences("ecosystem_background", Context.MODE_PRIVATE).edit {
                        putString("keep_alive_report", json)
                    }
                    if (apply) {
                        BackgroundService.Start(appContext)
                        ClipboardSyncManager.refresh()
                    }
                }.onFailure {
                    report = report.copy(error = true)
                    Log.w("EcosystemKeepAlive", "Could not apply or inspect power settings", it)
                }
            }
        }
    }

    private fun parse(json: String): KeepAliveReport = runCatching {
        val value = JSONObject(json)
        KeepAliveReport(value.optBoolean("batteryExempt"), value.optBoolean("backgroundAllowed"),
            value.optBoolean("standbyProtected"), value.optLong("checkedAt"))
    }.getOrDefault(KeepAliveReport(error = true))
}
