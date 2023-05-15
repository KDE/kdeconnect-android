/*
 * SPDX-FileCopyrightText: 2022 Wojciech Matuszewski <combaine@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Helpers

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object ThreadHelper {

    private val executor: ExecutorService = Executors.newCachedThreadPool()

    @JvmStatic
    fun execute(command: Runnable) = executor.execute(command)
}
