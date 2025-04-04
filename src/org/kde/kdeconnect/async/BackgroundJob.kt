/*
 * SPDX-FileCopyrightText: 2018 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.async

import java.util.concurrent.atomic.AtomicLong

abstract class BackgroundJob<I, R> : Runnable {
    private val callback: Callback<R>
    val requestInfo: I
    val id: Long

    constructor(requestInfo: I, callback: Callback<R>) {
        this.callback = callback
        this.requestInfo = requestInfo
        this.id = idIncrementer.incrementAndGet()
    }

    @Volatile
    var isCancelled: Boolean = false
        protected set

    private var backgroundJobHandler: BackgroundJobHandler? = null

    /** Used by the job handler to register itself as the handler */
    fun setBackgroundJobHandler(handler: BackgroundJobHandler) {
        this.backgroundJobHandler = handler
    }

    open fun cancel() {
        isCancelled = true
        backgroundJobHandler!!.cancelJob(this)
    }

    interface Callback<R> {
        fun onResult(job: BackgroundJob<*, *>, result: R)
        fun onError(job: BackgroundJob<*, *>, error: Throwable)
    }

    protected fun reportResult(result: R) {
        backgroundJobHandler!!.runOnUiThread {
            callback.onResult(this, result)
            backgroundJobHandler!!.onFinished(this)
        }
    }

    fun reportError(error: Throwable) {
        backgroundJobHandler!!.runOnUiThread {
            callback.onError(this, error)
            backgroundJobHandler!!.onFinished(this)
        }
    }

    companion object {
        private val idIncrementer = AtomicLong(0)
    }
}
