/*
 * SPDX-FileCopyrightText: 2024 ShellWen Chen <me@shellwen.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.sftp.saf

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.apache.sshd.common.file.util.BasePath
import java.net.URI
import java.nio.file.LinkOption

class SafPath(
    fileSystem: SafFileSystem,
    var safUri: Uri?,
    root: String?,
    names: List<String>
) : BasePath<SafPath, SafFileSystem>(fileSystem, root, names) {

    fun getNames(): List<String> = super.names

    override fun toRealPath(vararg options: LinkOption?): SafPath {
        return this.normalize()
    }

    override fun toUri(): URI {
        return URI.create(safUri.toString()) ?: throw IllegalStateException("SafUri is null")
    }

    fun getDocumentFile(ctx: Context): DocumentFile? {
        if (safUri == null) return null
        return DocumentFile.fromTreeUri(ctx, safUri!!)
    }

    fun isRoot(): Boolean {
        return (root == "/") && names.isEmpty()
    }

    companion object {
        fun newRootPath(fileSystem: SafFileSystem): SafPath {
            return SafPath(fileSystem, null, "/", emptyList())
        }
    }
}
