/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui.background

import android.content.Context
import android.os.Parcel
import android.util.Log
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Starts or joins the private, local-only AP as Shizuku's shell user. */
class HotspotUserService() : IHotspotControl.Stub() {
    constructor(context: Context) : this() { serviceContext = context }

    override fun startHotspot(ssid: String, passphrase: String): String {
        validate(ssid, passphrase)
        return command("cmd", "wifi", "start-softap", ssid, "wpa2", passphrase)
    }

    override fun joinHotspot(ssid: String, passphrase: String): String {
        validate(ssid, passphrase)
        return command("cmd", "wifi", "connect-network", ssid, "wpa2", passphrase, "-r", "persistent")
    }

    override fun stopHotspot(): String = command("cmd", "wifi", "stop-softap")

    private fun validate(ssid: String, passphrase: String) {
        require(ssid.matches(Regex("[A-Za-z0-9_-]{1,32}"))) { "Invalid private hotspot SSID" }
        require(passphrase.length in 8..63 && passphrase.none(Char::isWhitespace)) { "Invalid private hotspot key" }
    }

    private fun command(vararg args: String): String = try {
        val process = ProcessBuilder(*args).redirectErrorStream(true).start()
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor(1, TimeUnit.SECONDS)
            throw IllegalStateException("Command timed out: ${args.take(3).joinToString(" ")}")
        }
        val output = process.inputStream.bufferedReader().use { it.readText().take(4096) }
        val result = JSONObject().put("ok", process.exitValue() == 0)
            .put("exitCode", process.exitValue()).put("command", args.take(3).joinToString(" "))
            .put("output", output).toString()
        Log.i(TAG, result)
        result
    } catch (e: Exception) {
        Log.e(TAG, "Hotspot shell command failed", e)
        JSONObject().put("ok", false).put("error", e.message ?: e.javaClass.simpleName).toString()
    }

    override fun onTransact(code: Int, data: android.os.Parcel, reply: android.os.Parcel?, flags: Int): Boolean {
        if (code == 16777114) {
            android.os.Process.killProcess(android.os.Process.myPid())
            return true
        }
        return super.onTransact(code, data, reply, flags)
    }

    companion object {
        private const val TAG = "EcosystemHotspot"
        @Volatile private var serviceContext: Context? = null
    }
}
