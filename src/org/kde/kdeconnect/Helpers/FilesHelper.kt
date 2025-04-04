/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.Helpers

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import org.apache.commons.io.FilenameUtils
import org.kde.kdeconnect.NetworkPacket
import java.io.File
import kotlin.math.min

object FilesHelper {
    private const val LOG_TAG: String = "SendFileActivity"

    @JvmStatic
    fun getMimeTypeFromFile(file: String?): String {
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(FilenameUtils.getExtension(file))
        return mime ?: "*/*"
    }

    @JvmStatic
    fun findNonExistingNameForNewFile(path: String, filename: String): String {
        var newFilename = filename
        val name = FilenameUtils.getBaseName(newFilename)
        val ext = FilenameUtils.getExtension(newFilename)

        var num = 1
        while (File("$path/$newFilename").exists()) {
            newFilename = "$name ($num).$ext"
            num++
        }

        return newFilename
    }

    /**
     * Converts any string into a string that is safe to use as a file name.
     * The result will only include ascii characters and numbers, and the "-","_", and "." characters.
     */
    private fun toFileSystemSafeName(name: String, dirSeparators: Boolean, maxFileLength: Int): String {
        fun isSafeChar(c: Char): Boolean =
            c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' ||
            c == '_' || c == '-' || c == '.' ||
            (dirSeparators && ((c == '/') || (c == '\\')))

        val nameSafeChars = name.filter(::isSafeChar)
        val nameSafeLength = nameSafeChars.substring(nameSafeChars.length - min(nameSafeChars.length, maxFileLength))
        return nameSafeLength
    }
    fun toFileSystemSafeName(name: String, dirSeparators: Boolean): String = toFileSystemSafeName(name, dirSeparators, 255)
    fun toFileSystemSafeName(name: String): String = toFileSystemSafeName(name, true, 255)

    private fun getOpenFileCount(): Int? = File("/proc/self/fd").listFiles()?.size

    fun LogOpenFileCount() {
        Log.e("KDE/FileCount", "" + getOpenFileCount())
    }

    /**
     * Creates a network packet from the given URI
     */
    @JvmStatic
    fun uriToNetworkPacket(context: Context, uri: Uri, type: String?): NetworkPacket? {
        try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri)

            val packet = NetworkPacket(type!!)

            val sizeDefault = -1L

            // file:// is a non media uri, so we cannot query the ContentProvider
            fun fileSchemeExtract(): Triple<String?, Long, Long?> {
                val path = uri.path
                if (path != null) {
                    val mFile = File(path)

                    val filename = mFile.name
                    val size = mFile.length()
                    val lastModified = mFile.lastModified()
                    return Triple(filename, size, lastModified)
                }
                else {
                    Log.e(LOG_TAG, "Received bad file URI, path was null")
                    return Triple(null, sizeDefault, null)
                }
            }

            fun contentResolverExtract(): Triple<String?, Long, Long?> {
                // Since we used Intent.CATEGORY_OPENABLE, these two columns are the only ones we are guaranteed to have: https://developer.android.com/reference/android/provider/OpenableColumns
                val proj = arrayOf(OpenableColumns.SIZE, OpenableColumns.DISPLAY_NAME)

                try {
                    contentResolver.query(uri, proj, null, null, null).use { cursor ->
                        val nameColumnIndex = cursor!!.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                        val sizeColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                        cursor.moveToFirst()

                        val filename = cursor.getString(nameColumnIndex)
                        // It is recommended to check for the value to be null because there are situations were we don't know the size (for instance, if the file is not local to the device)
                        val size = if (!cursor.isNull(sizeColumnIndex)) cursor.getLong(sizeColumnIndex) else sizeDefault
                        val lastModified = getLastModifiedTime(context, uri)
                        return Triple(filename, size, lastModified)
                    }
                }
                catch (e: Exception) {
                    Log.e(LOG_TAG, "Problem getting file information", e)
                }
                return Triple(null, sizeDefault, null)
            }

            val (filename, size, lastModified) = when (uri.scheme) {
                "file" -> fileSchemeExtract()
                else -> contentResolverExtract()
            }

            if (filename != null) {
                packet["filename"] = filename
            }
            else {
                // It would be very surprising if this happens
                Log.e(LOG_TAG, "Unable to read filename")
            }

            if (lastModified != null) {
                packet["lastModified"] = lastModified
            }
            else {
                // This would not be too surprising, and probably means we need to improve FilesHelper.getLastModifiedTime
                Log.w(LOG_TAG, "Unable to read file last modified time")
            }

            packet.payload = NetworkPacket.Payload(inputStream, size)

            return packet
        }
        catch (e: Exception) {
            Log.e(LOG_TAG, "Exception creating network packet", e)
            return null
        }
    }

    /**
     * By hook or by crook, get the last modified time of the passed content:// URI
     *
     * This is a challenge because different content sources have different columns defined, and
     * I don't know how to tell what the source of the content is.
     *
     * Therefore, my brilliant solution is to just try everything until something works.
     *
     * Will return null if nothing worked.
     *
     * @return Time last modified in milliseconds or null
     */
    fun getLastModifiedTime(context: Context, uri: Uri): Long? {
        context.contentResolver.query(uri, null, null, null, null).use { cursor ->
            if (cursor != null && cursor.moveToFirst()) {
                val allColumns = cursor.columnNames

                // MediaStore.MediaColumns.DATE_MODIFIED resolves to "date_modified"
                // I see this column defined in case we used the Gallery app to select the file to transfer
                // This can occur both for devices running Storage Access Framework (SAF) if we select the Gallery to provide the file to transfer, as well as for older devices by doing the same
                val mediaDataModifiedColumnIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)

                // DocumentsContract.Document.COLUMN_LAST_MODIFIED resolves to "last_modified"
                // I see this column defined when, on a device using SAF we select a file using the file browser
                // According to https://developer.android.com/reference/kotlin/android/provider/DocumentsContract all "document providers" must provide certain columns.
                // Do we actually have a DocumentProvider here?
                // I do not think this code path will ever happen for a non-media file is selected on an API < KitKat device, since those will be delivered as a file:// URI and handled accordingly.
                // Therefore, it is safe to ignore the warning that this field requires API 19
                @SuppressLint("InlinedApi") val documentLastModifiedColumnIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                // If we have an image, it may be the case that MediaStore.MediaColumns.DATE_MODIFIED catches the modification date, but if not, here is another column we can look for.
                // This should be checked *after* DATE_MODIFIED since I think that column might give better information
                val imageDateTakenColumnIndex = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATE_TAKEN)

                // Report whether the captured timestamp is in milliseconds or seconds
                // The truthy-ness of this value for each different type of column is known from either experimentation or the docs (when docs exist...)
                val (properColumnIndex: Int, milliseconds: Boolean) = when {
                    mediaDataModifiedColumnIndex >= 0 -> Pair(mediaDataModifiedColumnIndex, false)
                    documentLastModifiedColumnIndex >= 0 -> Pair(documentLastModifiedColumnIndex, true)
                    imageDateTakenColumnIndex >= 0 -> Pair(imageDateTakenColumnIndex, true)
                    else -> {
                        // Nothing worked :(
                        Log.w("SendFileActivity", "Unable to get file modification time. Available columns were: ${allColumns.contentToString()}")
                        return null
                    }
                }

                val lastModifiedTime: Long? = if (!cursor.isNull(properColumnIndex)) cursor.getLong(properColumnIndex) else null

                return if (!milliseconds) lastModifiedTime!! * 1000 else lastModifiedTime
            }
        }
        return null
    }
}
