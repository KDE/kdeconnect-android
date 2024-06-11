/*
 * SPDX-FileCopyrightText: 2018 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.Plugins.SftpPlugin

import android.content.Context
import android.net.Uri
import android.util.Log
import org.apache.commons.io.FileUtils
import org.apache.sshd.common.file.nativefs.NativeSshFile
import org.kde.kdeconnect.Helpers.MediaStoreHelper
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile

internal class AndroidSshFile(
    view: AndroidFileSystemView,
    name: String,
    file: File,
    userName: String,
    private val context: Context
) : NativeSshFile(view, name, file, userName) {
    @Throws(IOException::class)
    override fun createOutputStream(offset: Long): OutputStream {
        if (!isWritable) {
            throw IOException("No write permission : ${file.name}")
        }

        val raf = RandomAccessFile(file, "rw")
        try {
            if (offset < raf.length()) {
                throw IOException("Your SSHFS is bugged") // SSHFS 3.0 and 3.2 cause data corruption, abort the transfer if this happens
            }
            raf.setLength(offset)
            raf.seek(offset)

            return object : FileOutputStream(raf.fd) {
                @Throws(IOException::class)
                override fun close() {
                    super.close()
                    raf.close()
                }
            }
        } catch (e: IOException) {
            raf.close()
            throw e
        }
    }

    override fun delete(): Boolean {
        return super.delete().also {
            if (it) {
                MediaStoreHelper.indexFile(context, Uri.fromFile(file))
            }
        }
    }

    @Throws(IOException::class)
    override fun create(): Boolean {
        return super.create().also {
            if (it) {
                MediaStoreHelper.indexFile(context, Uri.fromFile(file))
            }
        }
    }

    // Based on https://github.com/wolpi/prim-ftpd/blob/master/primitiveFTPd/src/org/primftpd/filesystem/FsFile.java
    override fun doesExist(): Boolean {
        // file.exists() returns false when we don't have read permission
        // try to figure out if it really does not exist
        try {
            return file.exists() || FileUtils.directoryContains(file.parentFile, file)
        } catch (e: IOException) {
            // An IllegalArgumentException is thrown if the parent is null or not a directory.
            Log.d(TAG, "Exception: ", e)
        } catch (e: IllegalArgumentException) {
            Log.d(TAG, "Exception: ", e)
        }

        return false
    }

    companion object {
        private val TAG: String = AndroidSshFile::class.java.simpleName
    }
}
