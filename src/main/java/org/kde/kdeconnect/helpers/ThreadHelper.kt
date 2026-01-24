/*
 * SPDX-FileCopyrightText: 2025 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.helpers

import android.os.Looper
import android.util.Log
import org.kde.kdeconnect_tp.BuildConfig
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object ThreadHelper {

    private val executor: ExecutorService = Executors.newCachedThreadPool()

    @JvmStatic
    fun execute(command: Runnable) = executor.execute(command)

    fun assertMainThread() {
        if (BuildConfig.DEBUG) {
            if (Thread.currentThread() == Looper.getMainLooper().thread) {
                Log.w("ThreadHelper", "This function must be called from the Main thread.", Exception("assertMainThread"))
            }
        }
    }

    fun assertNotMainThread() {
        if (BuildConfig.DEBUG) {
            if (Thread.currentThread() != Looper.getMainLooper().thread) {
                Log.w("ThreadHelper", "This function must NOT be called from the Main thread.", Exception("assertNotMainThread"))
            }
        }
    }
}
