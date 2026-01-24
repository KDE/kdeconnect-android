/*
 * SPDX-FileCopyrightText: 2020 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.helpers

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

object LifecycleHelper {
    fun initializeObserver() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(foregroundTracker)
    }

    @JvmStatic
    val isInForeground: Boolean
        get() = foregroundTracker.isInForeground

    private val foregroundTracker = object : DefaultLifecycleObserver {
        var isInForeground: Boolean = false
            private set

        override fun onStart(owner: LifecycleOwner) {
            this.isInForeground = true
        }

        override fun onStop(owner: LifecycleOwner) {
            this.isInForeground = false
        }
    }
}
