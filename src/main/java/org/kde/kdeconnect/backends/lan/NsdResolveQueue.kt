/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.backends.lan

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import java.util.LinkedList

class NsdResolveQueue {
    val LOG_TAG: String = "NsdResolveQueue"

    private val nsdManager: NsdManager
    private val lock: Any

    private data class PendingResolve(val serviceInfo: NsdServiceInfo, val listener: NsdManager.ResolveListener)
    private val resolveRequests: LinkedList<PendingResolve>

    constructor(nsdManager: NsdManager) {
        this.nsdManager = nsdManager
        this.lock = Any()
        this.resolveRequests = LinkedList<PendingResolve>()
    }

    fun resolveOrEnqueue(serviceInfo: NsdServiceInfo, listener: NsdManager.ResolveListener) {
        synchronized(lock) {
            if (resolveRequests.any { r -> serviceInfo.serviceName == r.serviceInfo.serviceName }) {
                Log.i(LOG_TAG, "Not enqueuing a new resolve request for the same service: " + serviceInfo.serviceName)
                return
            }
            resolveRequests.addLast(PendingResolve(serviceInfo, ListenerWrapper(listener)))
            if (resolveRequests.size == 1) {
                resolveNextRequest()
            }
        }
    }

    private inner class ListenerWrapper(private val listener: NsdManager.ResolveListener) : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            listener.onResolveFailed(serviceInfo, errorCode)
            postResolve()
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            listener.onServiceResolved(serviceInfo)
            postResolve()
        }

        private fun postResolve() {
            synchronized(lock) {
                resolveRequests.pop()
                resolveNextRequest()
            }
        }
    }

    private fun resolveNextRequest() {
        if (resolveRequests.isNotEmpty()) {
            val request = resolveRequests.first()
            nsdManager.resolveService(request.serviceInfo, request.listener)
        }
    }
}
