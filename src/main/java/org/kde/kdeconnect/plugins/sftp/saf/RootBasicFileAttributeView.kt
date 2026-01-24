/*
 * SPDX-FileCopyrightText: 2024 ShellWen Chen <me@shellwen.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.sftp.saf

import java.io.IOException
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime

object RootBasicFileAttributeView : BasicFileAttributeView {
    override fun name(): String = "basic"

    override fun readAttributes(): BasicFileAttributes = RootFileAttributes

    override fun setTimes(
        lastModifiedTime: FileTime?,
        lastAccessTime: FileTime?,
        createTime: FileTime?
    ) {
        throw IOException("Set times of root directory is not supported")
    }
}
