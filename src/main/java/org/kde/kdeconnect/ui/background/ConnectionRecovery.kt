/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui.background

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.helpers.TrustedDevices
import kotlin.random.Random

/** One timer per connection service; no alarms, polling while offline, or permanent wake locks. */
internal class ConnectionRecovery(private val context: Context, private val discover: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var online = false
    private var stopped = true
    private var scheduled = false
    private var delayIndex = 0
    private val delays = longArrayOf(5_000, 15_000, 30_000, 60_000, 120_000, 300_000)
    private val retry = Runnable {
        scheduled = false
        if (shouldRetry()) {
            Log.d("EcosystemRecovery", "Rediscovering; next retry delay index=$delayIndex")
            discover()
            scheduleNext()
        }
    }

    fun start() {
        stopped = false
        KdeConnect.getInstance().addDeviceListChangedCallback("ConnectionRecovery") {
            handler.post { update() }
        }
    }

    fun stop() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        scheduled = false
        KdeConnect.getInstance().removeDeviceListChangedCallback("ConnectionRecovery")
    }

    fun networkChanged(available: Boolean) {
        handler.post {
            val becameAvailable = !online && available
            online = available
            if (!available) cancel()
            else if (becameAvailable) requestImmediate()
            else update()
        }
    }

    fun requestImmediate() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { requestImmediate() }
            return
        }
        cancel()
        if (shouldRetry()) {
            scheduled = true
            handler.post(retry)
        }
    }

    private fun shouldRetry(): Boolean = !stopped && online &&
        KdeConnect.getInstance().devices.values.any {
            it.isPaired && !it.isReachable && TrustedDevices.isTrustedDevice(context, it.deviceId)
        }

    private fun update() {
        if (!shouldRetry()) cancel()
        else if (!scheduled) scheduleNext()
    }

    private fun scheduleNext() {
        if (!shouldRetry() || scheduled) return
        val delay = delays[delayIndex]
        delayIndex = (delayIndex + 1).coerceAtMost(delays.lastIndex)
        // Jitter prevents both devices repeatedly broadcasting at exactly the same instant.
        val jitter = Random.nextLong(-delay / 10, delay / 10 + 1)
        scheduled = true
        handler.postDelayed(retry, delay + jitter)
    }

    private fun cancel() {
        handler.removeCallbacks(retry)
        scheduled = false
        delayIndex = 0
    }
}
