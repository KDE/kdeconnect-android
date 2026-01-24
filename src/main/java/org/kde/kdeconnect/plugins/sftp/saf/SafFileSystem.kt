/*
 * SPDX-FileCopyrightText: 2024 ShellWen Chen <me@shellwen.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.sftp.saf

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import org.apache.sshd.common.file.util.BaseFileSystem
import java.nio.file.attribute.UserPrincipalLookupService
import java.nio.file.spi.FileSystemProvider

class SafFileSystem(
    fileSystemProvider: FileSystemProvider,
    private val roots: MutableMap<String, Uri>,
    private val context: Context
) : BaseFileSystem<SafPath>(fileSystemProvider) {
    override fun close() {
        throw UnsupportedOperationException("SAF does not support closing")
    }

    override fun isOpen(): Boolean = true

    override fun supportedFileAttributeViews(): Set<String> = setOf("basic", "posix")

    override fun getUserPrincipalLookupService(): UserPrincipalLookupService {
        throw UnsupportedOperationException("SAF does not support user principal lookup")
    }

    private tailrec fun getDocumentFileFromPath(
        docFile: DocumentFile,
        names: List<String>
    ): DocumentFile? {
        if (names.isEmpty()) {
            return docFile
        }
        val nextName = names.first()
        val nextNames = names.drop(1)
        val nextDocFile = docFile.findFile(nextName)
        return if (nextDocFile != null) {
            getDocumentFileFromPath(nextDocFile, nextNames)
        } else {
            null
        }
    }

    override fun create(root: String?, names: List<String>): SafPath {
        Log.v(TAG, "create: $root, $names")
        if ((root == "/") && names.isEmpty()) {
            return SafPath.newRootPath(this)
        }
        val dirName = names.getOrNull(0)
        if (dirName != null) {
            roots.forEach { (k, v) ->
                if (k == dirName) {
                    if (names.size == 1) {
                        return SafPath(this, v, root, names)
                    } else {
                        val docFile = getDocumentFileFromPath(
                            DocumentFile.fromTreeUri(context, v)!!,
                            names.drop(1)
                        )
                        return SafPath(this, docFile?.uri, root, names)
                    }
                }
            }
        }

        return SafPath(this, null, root, names)
    }

    companion object {
        private const val TAG = "SafFileSystem"
    }
}
