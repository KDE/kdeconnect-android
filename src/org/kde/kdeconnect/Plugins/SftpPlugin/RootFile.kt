/*
 * SPDX-FileCopyrightText: 2018 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.Plugins.SftpPlugin

import org.apache.sshd.common.file.SshFile
import java.io.InputStream
import java.io.OutputStream
import java.util.Calendar
import java.util.Collections
import java.util.EnumMap
import java.util.EnumSet

// TODO: ls .. and ls / only show .. and / respectively I would expect a listing
// TODO: cd .. to / does not work and prints "Can't change directory: Can't check target"
internal class RootFile(
    private val files: List<SshFile>,
    private val userName: String,
    private val exists: Boolean
) : SshFile {
    override fun getAbsolutePath(): String = "/"

    override fun getName(): String = "/"

    override fun getAttributes(followLinks: Boolean): Map<SshFile.Attribute, Any> {
        val attrs: MutableMap<SshFile.Attribute, Any> = EnumMap(SshFile.Attribute::class.java)

        attrs[SshFile.Attribute.Size] = 0
        attrs[SshFile.Attribute.Owner] = userName
        attrs[SshFile.Attribute.Group] = userName

        val p = EnumSet.noneOf(
            SshFile.Permission::class.java
        )
        p.add(SshFile.Permission.UserExecute)
        p.add(SshFile.Permission.GroupExecute)
        p.add(SshFile.Permission.OthersExecute)
        attrs[SshFile.Attribute.Permissions] = p

        val now = Calendar.getInstance().timeInMillis
        attrs[SshFile.Attribute.LastAccessTime] = now
        attrs[SshFile.Attribute.LastModifiedTime] = now

        attrs[SshFile.Attribute.IsSymbolicLink] = false
        attrs[SshFile.Attribute.IsDirectory] = true
        attrs[SshFile.Attribute.IsRegularFile] = false

        return attrs
    }

    override fun setAttributes(attributes: Map<SshFile.Attribute, Any>) {}

    override fun getAttribute(attribute: SshFile.Attribute, followLinks: Boolean): Any? = null

    override fun setAttribute(attribute: SshFile.Attribute, value: Any) {}

    override fun readSymbolicLink(): String = ""

    override fun createSymbolicLink(destination: SshFile) {}

    override fun getOwner(): String? = null

    override fun isDirectory(): Boolean = true

    override fun isFile(): Boolean = false

    override fun doesExist(): Boolean = exists

    override fun isReadable(): Boolean = true

    override fun isWritable(): Boolean = false

    override fun isExecutable(): Boolean = true

    override fun isRemovable(): Boolean = false

    override fun getParentFile(): SshFile = this

    override fun getLastModified(): Long = 0

    override fun setLastModified(time: Long): Boolean = false

    override fun getSize(): Long = 0

    override fun mkdir(): Boolean = false

    override fun delete(): Boolean = false

    override fun create(): Boolean = false

    override fun truncate() {}

    override fun move(destination: SshFile): Boolean = false

    override fun listSshFiles(): List<SshFile> = Collections.unmodifiableList(files)

    override fun createOutputStream(offset: Long): OutputStream? = null

    override fun createInputStream(offset: Long): InputStream? = null

    override fun handleClose() {
    }
}
