/*
 * SPDX-FileCopyrightText: 2024 ShellWen Chen <me@shellwen.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.sftp.saf

import java.nio.file.attribute.FileTime
import java.nio.file.attribute.GroupPrincipal
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.UserPrincipal

object RootFileAttributes : PosixFileAttributes {
    override fun lastModifiedTime(): FileTime = FileTime.fromMillis(0)
    override fun lastAccessTime(): FileTime = FileTime.fromMillis(0)
    override fun creationTime(): FileTime = FileTime.fromMillis(0)
    override fun size(): Long = 0
    override fun fileKey(): Any? = null
    override fun isDirectory(): Boolean = true
    override fun isRegularFile(): Boolean = false
    override fun isSymbolicLink(): Boolean = false
    override fun isOther(): Boolean = false

    override fun owner(): UserPrincipal? = null

    override fun group(): GroupPrincipal? = null

    override fun permissions(): Set<PosixFilePermission> = // 660 for SAF
        setOf(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE,
        )
}
