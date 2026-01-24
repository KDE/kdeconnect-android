package org.kde.kdeconnect.helpers.security

fun constantTimeCompare(a: ByteArray, b: ByteArray): Boolean {
    if (a.size != b.size) {
        return false
    }
    var result = 0
    for (i in a.indices) {
        result = result or (a[i].toInt() xor b[i].toInt())
    }
    return result == 0
}
