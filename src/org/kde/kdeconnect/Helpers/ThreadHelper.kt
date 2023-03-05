package org.kde.kdeconnect.Helpers

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object ThreadHelper {

    private val executor: ExecutorService = Executors.newCachedThreadPool()

    @JvmStatic
    fun execute(command: Runnable) = executor.execute(command)

}