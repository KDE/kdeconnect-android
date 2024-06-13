package org.kde.kdeconnect.Plugins.SftpPlugin.saf

import org.apache.sshd.common.file.util.BasePath
import org.apache.sshd.common.file.util.ImmutableList
import java.nio.file.LinkOption
import java.nio.file.Path

class SafPath(
    fileSystem: SafFileSystem,
    root: String, names: ImmutableList<String>
) : BasePath<SafPath, SafFileSystem>(fileSystem, root, names) {
    override fun toRealPath(vararg options: LinkOption?): Path {
        return this // FIXME
    }
}