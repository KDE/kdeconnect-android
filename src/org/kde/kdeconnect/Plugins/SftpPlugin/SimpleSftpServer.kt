/*
 * SPDX-FileCopyrightText: 2014 Samoilenko Yuri <kinnalru@gmail.com>
 * SPDX-FileCopyrightText: 2024 ShellWen Chen <me@shellwen.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.Plugins.SftpPlugin

import android.content.Context
import android.os.Build
import android.util.Log
import org.apache.sshd.common.file.nativefs.NativeFileSystemFactory
import org.apache.sshd.common.keyprovider.AbstractKeyPairProvider
import org.apache.sshd.common.session.SessionContext
import org.apache.sshd.common.util.io.PathUtils
import org.apache.sshd.common.util.security.SecurityUtils.SECURITY_PROVIDER_REGISTRARS
import org.apache.sshd.scp.server.ScpCommandFactory
import org.apache.sshd.server.ServerBuilder
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator
import org.apache.sshd.server.session.ServerSession
import org.apache.sshd.server.subsystem.SubsystemFactory
import org.apache.sshd.sftp.server.FileHandle
import org.apache.sshd.sftp.server.SftpFileSystemAccessor
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import org.apache.sshd.sftp.server.SftpSubsystemProxy
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.Helpers.RandomHelper
import org.kde.kdeconnect.Helpers.SecurityHelpers.RsaHelper
import org.kde.kdeconnect.Helpers.SecurityHelpers.constantTimeCompare
import org.kde.kdeconnect.Plugins.SftpPlugin.saf.SafFileSystemFactory
import org.kde.kdeconnect.Plugins.SftpPlugin.saf.SafPath
import org.slf4j.impl.HandroidLoggerAdapter
import java.io.IOException
import java.nio.channels.SeekableByteChannel
import java.nio.charset.StandardCharsets
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.attribute.FileAttribute
import java.security.GeneralSecurityException
import java.security.KeyPair
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.PublicKey

internal class SimpleSftpServer {
    private lateinit var sshd: SshServer

    val port: Int
        get() {
            if (!::sshd.isInitialized) return -1
            return sshd.port
        }

    val isStarted: Boolean
        get() {
            if (!::sshd.isInitialized) return false
            return sshd.isStarted
        }

    val isClosed: Boolean
        get() {
            if (!::sshd.isInitialized) return false
            return sshd.isClosed
        }

    private val passwordAuth = SimplePasswordAuthenticator()
    private val keyAuth = SimplePublicKeyAuthenticator()

    val isInitialized: Boolean
        get() = ::sshd.isInitialized

    private var safFileSystemFactory: SafFileSystemFactory? = null

    fun setSafRoots(storageInfoList: List<SftpPlugin.StorageInfo>) {
        safFileSystemFactory!!.initRoots(storageInfoList)
    }

    @Throws(GeneralSecurityException::class)
    fun initialize(context: Context, device: Device) {
        val sshd = ServerBuilder.builder().apply {
            fileSystemFactory(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    NativeFileSystemFactory()
                } else {
                    safFileSystemFactory = SafFileSystemFactory(context)
                    safFileSystemFactory
                }
            )
        }.build()

        // Reuse this device keys for the ssh connection as well
        val keyPair = KeyPair(
            RsaHelper.getPublicKey(context),
            RsaHelper.getPrivateKey(context)
        )
        sshd.keyPairProvider = object : AbstractKeyPairProvider() {
            override fun loadKeys(session: SessionContext): Iterable<KeyPair> = listOf(keyPair)
        }

        sshd.commandFactory = ScpCommandFactory()
        sshd.subsystemFactories =
            listOf<SubsystemFactory>(SftpSubsystemFactory.Builder().apply {
                withFileSystemAccessor(object : SftpFileSystemAccessor {
                    override fun openFile(
                        subsystem: SftpSubsystemProxy?,
                        fileHandle: FileHandle?,
                        file: Path?,
                        handle: String?,
                        options: MutableSet<out OpenOption>?,
                        vararg attrs: FileAttribute<*>?
                    ): SeekableByteChannel {
                        if (file is SafPath) {
                            return file.fileSystem.provider().newByteChannel(file, options, *attrs)
                        }
                        return super.openFile(subsystem, fileHandle, file, handle, options, *attrs)
                    }
                })
            }.build())

        keyAuth.deviceKey = device.certificate.publicKey

        sshd.publickeyAuthenticator = keyAuth
        sshd.passwordAuthenticator = passwordAuth

        this.sshd = sshd
    }

    fun start(): Boolean {
        if (isStarted) return true

        regeneratePassword()

        PORT_RANGE.forEach { port ->
            try {
                sshd.port = port
                sshd.start()

                return true
            } catch (e: IOException) {
                Log.w("SftpServer", "Failed to start server on port $port, trying next port", e)
            }
        }

        Log.e("SftpServer", "No more ports available")
        return false
    }

    fun stop() {
        if (!::sshd.isInitialized) return

        try {
            sshd.stop(true)
        } catch (e: Exception) {
            Log.e("SFTP", "Exception while stopping the server", e)
        }
    }

    fun regeneratePassword(): String {
        return RandomHelper.randomString(28).also {
            passwordAuth.setPassword(it)
        }
    }

    internal class SimplePasswordAuthenticator : PasswordAuthenticator {
        private val sha: MessageDigest = try {
            MessageDigest.getInstance("SHA-256")
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException(e)
        }
        private var passwordHash: ByteArray = byteArrayOf()

        fun setPassword(password: String) {
            passwordHash = sha.digest(password.toByteArray(StandardCharsets.UTF_8))
        }

        override fun authenticate(user: String, password: String, session: ServerSession): Boolean {
            val receivedPasswordHash = sha.digest(password.toByteArray(StandardCharsets.UTF_8))
            return user == USER && constantTimeCompare(passwordHash, receivedPasswordHash)
        }
    }

    internal class SimplePublicKeyAuthenticator : PublickeyAuthenticator {
        var deviceKey: PublicKey? = null

        override fun authenticate(user: String, key: PublicKey, session: ServerSession): Boolean =
            user == USER && deviceKey == key
    }

    companion object {
        private val PORT_RANGE = 1739..1764

        const val USER: String = "kdeconnect"

        init {
            System.setProperty(SECURITY_PROVIDER_REGISTRARS, "") // disable BouncyCastle
            System.setProperty(
                "org.apache.sshd.common.io.IoServiceFactoryFactory",
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    // Use MINA instead NIO2 due to compatibility issues
                    // Android 7.1 (API 25) and below have issues with NIO2
                    // When we require API 26, we can remove this and the Mina dependency.
                    "org.apache.sshd.mina.MinaServiceFactoryFactory"
                } else {
                    "org.apache.sshd.common.io.nio2.Nio2ServiceFactoryFactory"
                }
            )
            // Remove it when SSHD Core is fixed.
            // Android has no user home folder, so we need to set it to something.
            // `System.getProperty("user.home")` is not available on Android,
            // but it exists in SSHD Core's `org.apache.sshd.common.util.io.PathUtils.LazyDefaultUserHomeFolderHolder`.
            PathUtils.setUserHomeFolderResolver { Path.of("/") }

            // Disable SSHD logging due to performance degradation and being very noisy even in development
            HandroidLoggerAdapter.DEBUG = false
        }
    }
}
