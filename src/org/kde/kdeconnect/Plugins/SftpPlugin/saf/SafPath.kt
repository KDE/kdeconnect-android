/*
 * SPDX-FileCopyrightText: 2024 ShellWen Chen <me@shellwen.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.Plugins.SftpPlugin.saf

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import org.apache.sshd.common.file.util.BasePath
import java.nio.file.LinkOption

class SafPath(
    fileSystem: SafFileSystem,
    var safUri: Uri?,
    val root: String?, val names: List<String>
) : BasePath<SafPath, SafFileSystem>(fileSystem, root, names) {
    override fun toRealPath(vararg options: LinkOption?): SafPath {
        return this.normalize()
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
