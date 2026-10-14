/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui.background

import android.content.Context
import android.os.Parcel
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Applies only Ecosystem's own Android power settings under Shizuku's shell identity. */
class KeepAliveUserService() : IKeepAliveService.Stub() {
    private var serviceContext: Context? = null

    constructor(context: Context) : this() {
        serviceContext = context
    }

    override fun applyProtections(packageName: String): String {
        requireOwnPackage(packageName)
        // Each command is bounded and uses arguments directly, never a shell command string.
        command("cmd", "deviceidle", "whitelist", "+$packageName")
        command("cmd", "appops", "set", packageName, "RUN_IN_BACKGROUND", "allow")
        command("cmd", "appops", "set", packageName, "RUN_ANY_IN_BACKGROUND", "allow")
        command("am", "set-inactive", packageName, "false")
        command("am", "set-standby-bucket", packageName, "active")
        return inspectProtections(packageName)
    }

    override fun inspectProtections(packageName: String): String {
        requireOwnPackage(packageName)
        val whitelist = command("cmd", "deviceidle", "whitelist")
        val background = command("cmd", "appops", "get", packageName, "RUN_IN_BACKGROUND")
        val anyBackground = command("cmd", "appops", "get", packageName, "RUN_ANY_IN_BACKGROUND")
        val bucket = command("am", "get-standby-bucket", packageName)
        return JSONObject()
            .put("batteryExempt", whitelist.lineSequence().any { it.split(',').getOrNull(1) == packageName })
            .put("backgroundAllowed", allowed(background) && allowed(anyBackground))
            .put("standbyProtected", bucket.trim() in setOf("5", "10"))
            .put("checkedAt", System.currentTimeMillis())
            .toString()
    }

    private fun requireOwnPackage(packageName: String) {
        require(packageName == serviceContext?.packageName &&
            packageName in setOf("com.ecosystem.personal", "com.ecosystem.personal.debug")) {
            "Only Ecosystem's own package can be changed"
        }
    }

    private fun allowed(output: String): Boolean = Regex("(?:^|[ :])allow(?:;|\\s|$)").containsMatchIn(output)

    private fun command(vararg arguments: String): String {
        val process = ProcessBuilder(listOf("/system/bin/${arguments.first()}") + arguments.drop(1))
            .redirectErrorStream(true).start()
        var output = ""
        val reader = thread(isDaemon = true) {
            runCatching { process.inputStream.bufferedReader().use { output = it.readText().take(16_000) } }
        }
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return "Command timed out"
            }
            reader.join(500)
            return output
        } finally {
            process.destroy()
        }
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == 16777114) {
            android.os.Process.killProcess(android.os.Process.myPid())
            return true
        }
        return super.onTransact(code, data, reply, flags)
    }
}
