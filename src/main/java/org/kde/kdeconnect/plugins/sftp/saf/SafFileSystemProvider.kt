/*
 * SPDX-FileCopyrightText: 2024 ShellWen Chen <me@shellwen.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.sftp.saf

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.util.Log
import java.io.FileNotFoundException
import java.io.IOException
import java.lang.reflect.Method
import java.net.URI
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.AccessMode
import java.nio.file.CopyOption
import java.nio.file.DirectoryStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.FileAttributeView
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.GroupPrincipal
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.UserPrincipal
import java.nio.file.spi.FileSystemProvider

class SafFileSystemProvider(
    private val context: Context,
    val roots: MutableMap<String, Uri>
) : FileSystemProvider() {
    override fun getScheme(): String = "saf"

    override fun newFileSystem(uri: URI, env: MutableMap<String, *>?): FileSystem {
        // SSHD Core does not use this method, so we can just throw an exception
        Log.w(TAG, "newFileSystem($uri) not implemented")
        throw NotImplementedError("newFileSystem($uri) not implemented")
    }

    override fun getFileSystem(uri: URI): FileSystem {
        // SSHD Core does not use this method, so we can just throw an exception
        Log.w(TAG, "getFileSystem($uri) not implemented")
        throw NotImplementedError("getFileSystem($uri) not implemented")
    }

    override fun getPath(uri: URI): Path {
        // SSHD Core does not use this method, so we can just throw an exception
        Log.w(TAG, "getPath($uri) not implemented")
        throw NotImplementedError("getPath($uri) not implemented")
    }

    /**
     * @see org.apache.sshd.sftp.server.FileHandle.getOpenOptions
     */
    override fun newByteChannel(
        path: Path,
        options: Set<OpenOption>,
        vararg attrs_: FileAttribute<*>
    ): SeekableByteChannel {
        val channel = newFileChannel(path, options, *attrs_)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return convertMaybeLegacyFileChannelFromLibraryFunction.invoke(
                null,
                channel
            ) as SeekableByteChannel
        }

        return channel
    }

    private fun createFile(path: SafPath, failedWhenExists: Boolean): Uri {
        if (path.isRoot()) {
            throw IOException("Cannot create root directory")
        }
        if (failedWhenExists && Files.exists(path)) {
            throw FileAlreadyExistsException(path.toString())
        }
        val parent = path.parent.getDocumentFile(context)
            ?: throw IOException("Parent directory does not exist")
        val docFile = parent.createFile(Files.probeContentType(path), path.getNames().last())
            ?: throw IOException("Failed to create $path")
        val uri = docFile.uri
        path.safUri = uri
        return uri
    }

    /**
     * @see org.apache.sshd.sftp.server.FileHandle.getOpenOptions
     */
    override fun newFileChannel(
        path: Path,
        options: Set<OpenOption>,
        vararg attrs_: FileAttribute<*>
    ): FileChannel {
        check(path is SafPath)
        check(!path.isRoot())

        /*
         * According to https://tools.ietf.org/html/draft-ietf-secsh-filexfer-13#page-33
         *
         * The 'attrs' field is ignored if an existing file is opened.
         */
        val attrs = if (Files.exists(path)) {
            emptyArray()
        } else {
            attrs_
        }

        when {
            // READ
            options.contains(StandardOpenOption.READ) -> {
                if (options.contains(StandardOpenOption.WRITE)) {
                    throw IllegalArgumentException("Cannot open a file for both reading and writing")
                }
                if (options.contains(StandardOpenOption.CREATE_NEW) || options.contains(StandardOpenOption.CREATE)) {
                    createFile(path, options.contains(StandardOpenOption.CREATE_NEW))
                }
                val docFile = path.getDocumentFile(context)!!
                return ParcelFileDescriptor.AutoCloseInputStream(
                    context.contentResolver.openFileDescriptor(docFile.uri, "r")!!
                ).channel
            }
            // WRITE
            options.contains(StandardOpenOption.WRITE) -> {
                if (options.contains(StandardOpenOption.CREATE_NEW) || options.contains(StandardOpenOption.CREATE)) {
                    createFile(path, options.contains(StandardOpenOption.CREATE_NEW))
                }
                val docFile =
                    path.getDocumentFile(context) ?: throw IOException("Failed to create $path")
                check(docFile.exists())
                val mode = when {
                    options.contains(StandardOpenOption.APPEND) -> "wa"
                    options.contains(StandardOpenOption.TRUNCATE_EXISTING) -> "wt"
                    else -> "w"
                }
                return ParcelFileDescriptor.AutoCloseOutputStream(
                    context.contentResolver.openFileDescriptor(docFile.uri, mode)!!
                ).channel
            }

            else -> {
                Log.w(TAG, "newFileChannel($path, $options, $attrs) not implemented")
                throw IOException("newFileChannel($path, $options, $attrs) not implemented")
            }
        }
    }

    override fun newDirectoryStream(
        dir: Path,
        filter: DirectoryStream.Filter<in Path>
    ): DirectoryStream<Path> {
        check(dir is SafPath)

        if (dir.isRoot()) {
            return object : DirectoryStream<Path> {
                override fun iterator(): MutableIterator<Path> {
                    return roots.mapNotNull { (name, uri) ->
                        val newPath = SafPath(dir.fileSystem, uri, null, listOf(name))
                        if (filter.accept(newPath)) newPath else null
                    }.toMutableList().iterator()
                }

                override fun close() {
                    // no-op
                }
            }
        }

        check(dir.getNames().isNotEmpty())

        return object : DirectoryStream<Path> {
            override fun iterator(): MutableIterator<Path> {
                val documentFile = dir.getDocumentFile(context)!!
                return documentFile.listFiles().mapNotNull {
                    if (it.uri.path?.endsWith(".android_secure") == true) return@mapNotNull null

                    val newPath = SafPath(dir.fileSystem, it.uri, null, dir.getNames() + it.name!!)
                    if (filter.accept(newPath)) newPath else null
                }.toMutableList().iterator()
            }

            override fun close() {
                // no-op
            }
        }
    }

    override fun createDirectory(dir: Path, vararg attrs: FileAttribute<*>) {
        check(dir is SafPath)
        if (dir.isRoot()) {
            throw IOException("Cannot create root directory")
        }
        if (dir.parent == null) {
            throw IOException("Parent directory does not exist")
        }
        val parent = dir.parent.getDocumentFile(context)
            ?: throw IOException("Parent directory does not exist")
        parent.createDirectory(dir.getNames().last())
    }

    override fun delete(path: Path) {
        check(path is SafPath)
        val docFile = path.getDocumentFile(context)
            ?: throw java.nio.file.NoSuchFileException(
                path.toString(),
            ) // No kotlin.NoSuchFileException, they are different
        if (!docFile.delete()) {
            throw IOException("Failed to delete $path")
        }
    }

    override fun copy(source: Path, target: Path, vararg options: CopyOption) {
        check(source is SafPath)
        check(target is SafPath)

        val sourceDocFile = source.getDocumentFile(context)
            ?: throw java.nio.file.NoSuchFileException(
                source.toString(),
            ) // No kotlin.NoSuchFileException, they are different

        val targetDocFile = target.apply {
            createFile(this, false)
        }.getDocumentFile(context)
            ?: throw java.nio.file.NoSuchFileException(
                target.toString(),
            ) // No kotlin.NoSuchFileException, they are different

        context.contentResolver.openOutputStream(targetDocFile.uri)?.use { os ->
            context.contentResolver.openInputStream(sourceDocFile.uri)?.use { is_ ->
                is_.copyTo(os)
            }
        }
    }

    override fun move(source: Path, target: Path, vararg options: CopyOption) {
        check(source is SafPath)
        check(target is SafPath)

        val sourceUri = source.getDocumentFile(context)!!.uri
        val parentUri = source.parent.getDocumentFile(context)!!.uri
        val destParentUri = target.parent.getDocumentFile(context)!!.uri

        // 1. If dest parent is the same as source parent, rename the file
        run firstStep@{
            if (parentUri == destParentUri) {
                try {
                    val newUri = DocumentsContract.renameDocument(
                        context.contentResolver,
                        sourceUri,
                        target.getNames().last()
                    )
                    if (newUri == null) { // renameDocument returns null on failure
                        return@firstStep
                    }
                    source.safUri = newUri
                    return
                } catch (ignored: FileNotFoundException) {
                    // no-op: fallback to the next method
                }
            }
        }

        val sourceTreeDocumentId = DocumentsContract.getTreeDocumentId(parentUri)
        val destTreeDocumentId = DocumentsContract.getTreeDocumentId(destParentUri)

        // 2. If source and dest are in the same tree, and the API level is high enough, move the file
        if (sourceTreeDocumentId == destTreeDocumentId &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
        ) {
            val newUri = DocumentsContract.moveDocument(
                context.contentResolver,
                sourceUri,
                parentUri,
                destParentUri
            )
            source.safUri = newUri!!
            return
        }

        // 3. Else copy and delete the file
        copy(source, target, *options)
        delete(source)
    }

    override fun isSameFile(p1: Path, p2: Path): Boolean {
        check(p1 is SafPath)
        check(p2 is SafPath)
        return p1.root == p2.root && p1.getNames() == p2.getNames() &&
                p1.getDocumentFile(context)!!.uri == p2.getDocumentFile(context)!!.uri
    }

    override fun isHidden(path: Path): Boolean {
        check(path is SafPath)

        if (path.isRoot()) {
            return false
        }

        return path.getNames().last().startsWith(".")
    }

    override fun getFileStore(path: Path): FileStore? {
        // SAF does not support file store
        Log.i(TAG, "getFileStore($path) not implemented")
        return null
    }

    override fun checkAccess(path: Path, vararg modes: AccessMode) {
        check(path is SafPath)
        if (path.isRoot()) {
            modes.forEach {
                when (it) {
                    AccessMode.READ -> {
                        // Root is always readable
                    }

                    AccessMode.WRITE -> {
                        // Root is not writable
                        throw java.nio.file.AccessDeniedException("/") // No kotlin.AccessDeniedException, they are different
                    }

                    AccessMode.EXECUTE -> {
                        // Root is not executable
                        throw java.nio.file.AccessDeniedException("/") // No kotlin.AccessDeniedException, they are different
                    }
                }
            }
            return
        }
        val docFile = path.getDocumentFile(context)
            ?: throw java.nio.file.NoSuchFileException(
                path.toString(),
            ) // No kotlin.NoSuchFileException, they are different
        if (!docFile.exists()) {
            throw java.nio.file.NoSuchFileException(
                docFile.uri.toString(),
            ) // No kotlin.NoSuchFileException, they are different
        }
        modes.forEach {
            when (it) {
                AccessMode.READ -> {
                    if (!docFile.canRead()) {
                        throw java.nio.file.AccessDeniedException(docFile.uri.toString()) // No kotlin.AccessDeniedException, they are different
                    }
                }

                AccessMode.WRITE -> {
                    if (!docFile.canWrite()) {
                        throw java.nio.file.AccessDeniedException(docFile.uri.toString()) // No kotlin.AccessDeniedException, they are different
                    }
                }

                AccessMode.EXECUTE -> {
                    // SAF files is not executable
                    throw java.nio.file.AccessDeniedException(docFile.uri.toString()) // No kotlin.AccessDeniedException, they are different
                }
            }
        }
    }

    override fun <V : FileAttributeView> getFileAttributeView(
        path: Path,
        type: Class<V>,
        vararg options: LinkOption?
    ): V? {
        check(path is SafPath)
        if (path.isRoot()) {
            if (type == BasicFileAttributeView::class.java) {
                @Suppress("UNCHECKED_CAST")
                return RootBasicFileAttributeView as V
            }
            if (type == PosixFileAttributeView::class.java) {
                @Suppress("UNCHECKED_CAST")
                return RootPosixFileAttributeView as V
            }
        }

        if (type == BasicFileAttributeView::class.java) {
            @Suppress("UNCHECKED_CAST")
            return object : BasicFileAttributeView {
                override fun name(): String = "basic"

                override fun readAttributes(): BasicFileAttributes =
                    readAttributes(path, BasicFileAttributes::class.java)

                override fun setTimes(
                    lastModifiedTime: FileTime?,
                    lastAccessTime: FileTime?,
                    createTime: FileTime?
                ) {
                    Log.w(
                        TAG,
                        "setTimes($path, $lastModifiedTime, $lastAccessTime, $createTime) for SAF is impossible. Ignored."
                    )
                }
            } as V
        }
        if (type == PosixFileAttributeView::class.java) {
            @Suppress("UNCHECKED_CAST")
            return object : PosixFileAttributeView {
                override fun name(): String = "posix"

                override fun readAttributes(): PosixFileAttributes =
                    readAttributes(path, PosixFileAttributes::class.java)

                override fun setTimes(
                    lastModifiedTime: FileTime?,
                    lastAccessTime: FileTime?,
                    createTime: FileTime?
                ) {
                    Log.w(
                        TAG,
                        "setTimes($path, $lastModifiedTime, $lastAccessTime, $createTime) for SAF is impossible. Ignored."
                    )
                }

                override fun getOwner(): UserPrincipal? {
                    Log.i(TAG, "getOwner($path) not implemented")
                    return null
                }

                override fun setOwner(owner: UserPrincipal?) {
                    Log.i(TAG, "setOwner($path, $owner) not implemented")
                }

                override fun setPermissions(perms: MutableSet<PosixFilePermission>?) {
                    Log.i(TAG, "setPermissions($path, $perms) not implemented")
                }

                override fun setGroup(group: GroupPrincipal?) {
                    Log.i(TAG, "setGroup($path, $group) not implemented")
                }
            } as V
        }
        Log.w(TAG, "getFileAttributeView($path)[${type.getSimpleName()}] not implemented")
        return null
    }

    override fun <A : BasicFileAttributes> readAttributes(
        path: Path,
        type: Class<A>,
        vararg options: LinkOption?
    ): A {
        check(path is SafPath)

        if (path.isRoot()) {
            if (type == BasicFileAttributes::class.java || type == PosixFileAttributes::class.java) {
                @Suppress("UNCHECKED_CAST")
                return RootFileAttributes as A
            }
        }

        path.getDocumentFile(context).let {
            if (it == null) {
                throw java.nio.file.NoSuchFileException(
                    path.toString(),
                ) // No kotlin.NoSuchFileException, they are different
            }
            if (type == BasicFileAttributes::class.java || type == PosixFileAttributes::class.java) {
                @Suppress("UNCHECKED_CAST")
                return object : PosixFileAttributes {
                    override fun lastModifiedTime(): FileTime =
                        FileTime.fromMillis(it.lastModified())

                    override fun lastAccessTime(): FileTime = FileTime.fromMillis(it.lastModified())
                    override fun creationTime(): FileTime = FileTime.fromMillis(it.lastModified())
                    override fun size(): Long = it.length()
                    override fun fileKey(): Any? = null
                    override fun isDirectory(): Boolean = it.isDirectory
                    override fun isRegularFile(): Boolean = it.isFile
                    override fun isSymbolicLink(): Boolean = false
                    override fun isOther(): Boolean = false

                    override fun owner(): UserPrincipal? = null
                    override fun group(): GroupPrincipal? = null
                    override fun permissions(): Set<PosixFilePermission> = // 660 for SAF
                        setOf(
                            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE,
                        )
                } as A
            }
        }

        Log.w(TAG, "readAttributes($path)[${type.getSimpleName()}] not implemented")
        throw UnsupportedOperationException("readAttributes($path)[${type.getSimpleName()}] N/A")
    }

    override fun readAttributes(
        path: Path,
        attributes: String,
        vararg options: LinkOption?
    ): Map<String, Any?> {
        check(path is SafPath)
        if (path.isRoot()) {
            if (attributes == "basic" || attributes.startsWith("basic:")) {
                return mapOf(
                    "isDirectory" to true,
                    "isRegularFile" to false,
                    "isSymbolicLink" to false,
                    "isOther" to false,
                    "size" to 0L,
                    "fileKey" to null,
                    "lastModifiedTime" to FileTime.fromMillis(0),
                    "lastAccessTime" to FileTime.fromMillis(0),
                    "creationTime" to FileTime.fromMillis(0)
                )
            }
            if (attributes == "posix" || attributes.startsWith("posix:")) {
                return mapOf(
                    "owner" to null,
                    "group" to null,
                    "permissions" to setOf(
                        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE,
                    )
                )
            }
        }
        val documentFile = path.getDocumentFile(context)
        check(documentFile != null)
        if (attributes == "basic" || attributes.startsWith("basic:")) {
            return mapOf(
                "isDirectory" to documentFile.isDirectory,
                "isRegularFile" to documentFile.isFile,
                "isSymbolicLink" to false,
                "isOther" to false,
                "size" to documentFile.length(),
                "fileKey" to null,
                "lastModifiedTime" to FileTime.fromMillis(documentFile.lastModified()),
                "lastAccessTime" to FileTime.fromMillis(documentFile.lastModified()),
                "creationTime" to FileTime.fromMillis(documentFile.lastModified())
            )
        }
        if (attributes == "posix" || attributes.startsWith("posix:")) {
            return mapOf(
                "owner" to null,
                "group" to null,
                "permissions" to setOf(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE,
                )
            )
        }
        Log.w(TAG, "readAttributes($path, $attributes) not implemented")
        throw UnsupportedOperationException("readAttributes($path, $attributes) N/A")
    }

    override fun setAttribute(
        path: Path,
        attribute: String,
        value: Any?,
        vararg options: LinkOption?
    ) {
        check(path is SafPath)
        when (attribute) {
            "basic:lastModifiedTime", "basic:lastAccessTime", "basic:creationTime" -> {
                check(value is FileTime)
                throw UnsupportedOperationException("$attribute is read-only")
            }

            "posix:owner", "posix:group", "posix:permissions" -> {
                Log.w(TAG, "set posix attribute $attribute not implemented")
                // We can't throw an exception here because the SSHD server will crash
                return
            }

            else -> {
                Log.w(TAG, "setAttribute($path, $attribute, $value) not implemented")
                // We can't throw an exception here because the SSHD server will crash
            }
        }
    }

    companion object {
        private const val TAG = "SafFileSystemProvider"

        private val convertMaybeLegacyFileChannelFromLibraryFunction: Method by lazy {
            val clazz = Class.forName("j$.nio.channels.DesugarChannels")
            clazz.getDeclaredMethod(
                "convertMaybeLegacyFileChannelFromLibrary",
                FileChannel::class.java
            )
        }
    }
}
