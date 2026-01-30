package main.java.org.kde.kdeconnect.helpers

import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.Reader
import java.net.Socket

class LineTooLongException : IOException("Line too long")

/**
 * Reads until a \n is found or if maxLineSize is reached.
 * We only check for \n line terminators because that's what the KDE Connect protocol uses.
 */
@Throws(IOException::class)
fun readLineBounded(inputStream: InputStream, maxLineSize: Int): String {
    val buffer = ByteArrayOutputStream(1024)
    while (true) {
        val b = inputStream.read()
        if (b == -1) {
            throw IOException("Stream closed")
        }
        buffer.write(b)
        if (b == '\n'.code) {
            // Can't specify Charsets.UTF_8 until Android API 33,
            // but the default charset is always UTF-8 on Android
            return buffer.toString()
        }
        if (buffer.size() >= maxLineSize) {
            throw LineTooLongException()
        }
    }
}
