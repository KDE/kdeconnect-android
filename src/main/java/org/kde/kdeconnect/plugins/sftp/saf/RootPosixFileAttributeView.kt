/*
 * SPDX-FileCopyrightText: 2024 ShellWen Chen <me@shellwen.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.sftp.saf

import java.io.IOException
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.GroupPrincipal
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.UserPrincipal

object RootPosixFileAttributeView : PosixFileAttributeView {
    override fun name(): String = "posix"

    override fun readAttributes(): PosixFileAttributes = RootFileAttributes
    override fun setTimes(
        lastModifiedTime: FileTime?,
        lastAccessTime: FileTime?,
        createTime: FileTime?
    ) {
        throw IOException("Set times of root directory is not supported")
    }

    override fun getOwner(): UserPrincipal? = null

    override fun setOwner(owner: UserPrincipal?) {
        throw IOException("Set owner of root directory is not supported")
    }

    override fun setPermissions(perms: MutableSet<PosixFilePermission>?) {
        throw IOException("Set permissions of root directory is not supported")
    }

    override fun setGroup(group: GroupPrincipal?) {
        throw IOException("Set group of root directory is not supported")
    }
}
