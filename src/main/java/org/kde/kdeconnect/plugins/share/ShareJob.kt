/*
 * SPDX-FileCopyrightText: 2026 Albert Vaca Cintora <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.share

import org.kde.kdeconnect.Device
import org.kde.kdeconnect.helpers.ThreadHelper
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * A file transfer that runs on its own thread. [onFinished] is called on that thread once [run] returns,
 * whether it succeeded, failed or was cancelled.
 */
abstract class ShareJob(protected val device: Device, private val onFinished: (ShareJob) -> Unit) : Runnable {
    val id: Long = idIncrementer.incrementAndGet()

    @Volatile
    var isCancelled: Boolean = false
        private set

    open fun cancel() {
        isCancelled = true
    }

    fun start() {
        runningJobs[id] = this
        ThreadHelper.runOnNewThread {
            try {
                run()
            } finally {
                runningJobs.remove(id)
                onFinished(this)
            }
        }
    }

    companion object {
        private val idIncrementer = AtomicLong(0)

        // Lets the cancel button of a notification find its job
        private val runningJobs = ConcurrentHashMap<Long, ShareJob>()

        fun cancel(jobId: Long) {
            runningJobs[jobId]?.cancel()
        }
    }
}
