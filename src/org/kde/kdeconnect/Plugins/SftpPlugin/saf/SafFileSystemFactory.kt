/*
 * SPDX-FileCopyrightText: 2018 Erik Duisters <e.duisters1@gmail.com>
 * SPDX-FileCopyrightText: 2024 ShellWen Chen <me@shellwen.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.Plugins.SftpPlugin.saf

import android.content.Context
import android.util.Log
import org.apache.sshd.common.session.Session
import org.apache.sshd.common.file.FileSystemFactory
import org.kde.kdeconnect.Plugins.SftpPlugin.SftpPlugin
import java.nio.file.FileSystem

class SafFileSystemFactory(private val context: Context) : FileSystemFactory {
    private val provider = SafFileSystemProvider()
    private val roots: MutableMap<String, String?> = HashMap()

    fun initRoots(storageInfoList: List<SftpPlugin.StorageInfo>) {
        Log.i(TAG, "initRoots: $storageInfoList")

        for (curStorageInfo in storageInfoList) {
            when {
                curStorageInfo.isFileUri -> {
                    TODO("File URI is not supported yet")
//                    if (curStorageInfo.uri.path != null) {
//                        roots[curStorageInfo.displayName] = curStorageInfo.uri.path
//                    }
                }
                curStorageInfo.isContentUri -> {
                    roots[curStorageInfo.displayName] = curStorageInfo.uri.toString()
                }
                else -> {
                    Log.e(TAG, "Unknown storage URI type: $curStorageInfo")
                }
            }
        }
    }

    override fun createFileSystem(session: Session): FileSystem {
        return SafFileSystem(provider, roots, session.username, context)
    }

    companion object {
        private const val TAG = "SafFileSystemFactory"
    }
}