package org.kde.kdeconnect.Plugins.SftpPlugin.saf

import android.content.Context
import android.util.Log
import org.apache.sshd.common.file.util.BaseFileSystem
import java.nio.file.attribute.UserPrincipalLookupService
import java.nio.file.spi.FileSystemProvider

class SafFileSystem(
    fileSystemProvider: FileSystemProvider,
    roots: MutableMap<String, String?>,
    username: String,
    private val context: Context
) : BaseFileSystem<SafPath>(fileSystemProvider) {
    override fun close() {
        // no-op
        Log.v(TAG, "close")
    }

    override fun isOpen(): Boolean = true

    override fun supportedFileAttributeViews(): Set<String> = setOf("basic")

    override fun getUserPrincipalLookupService(): UserPrincipalLookupService {
        throw UnsupportedOperationException("SAF does not support user principal lookup")
    }

    override fun create(root: String, names: List<String>): SafPath {
        Log.v(TAG, "create: $root, $names")
        return SafPath(this, root, names)
    }

    companion object {
        private const val TAG = "SafFileSystem"
    }
}