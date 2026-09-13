package org.kde.kdeconnect.extensions

import java.io.Closeable
import java.io.IOException

fun Closeable.closeSafe() {
    try {
        close()
    } catch (_ : IOException) { }
}