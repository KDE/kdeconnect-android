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
    var port: Int = -1
        private set
    var isStarted: Boolean = false
        private set

    private val passwordAuth = SimplePasswordAuthenticator()
    private val keyAuth = SimplePublicKeyAuthenticator()

    var isInitialized: Boolean = false

    private var sshd: SshServer? = null
    val isClosed: Boolean
        get() = sshd!!.isClosed

    private var safFileSystemFactory: SafFileSystemFactory? = null

    fun setSafRoots(storageInfoList: List<SftpPlugin.StorageInfo>) {
        safFileSystemFactory!!.initRoots(storageInfoList)
    }

    @Throws(GeneralSecurityException::class)
    fun initialize(context: Context?, device: Device) {
        val sshd = ServerBuilder.builder().apply {
            fileSystemFactory(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    NativeFileSystemFactory()
                } else {
                    safFileSystemFactory = SafFileSystemFactory(context!!)
                    safFileSystemFactory // FIXME: This is not working
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

        this.isInitialized = true

        this.sshd = sshd
    }

    fun start(): Boolean {
        if (!isStarted) {
            regeneratePassword()

            port = STARTPORT
            while (!isStarted) {
                try {
                    sshd!!.port = port
                    sshd!!.start()
                    isStarted = true
                } catch (e: IOException) {
                    port++
                    if (port >= ENDPORT) {
                        port = -1
                        Log.e("SftpServer", "No more ports available")
                        return false
                    }
                }
            }
        }

        return true
    }

    fun stop() {
        try {
            isStarted = false
            sshd!!.stop(true)
        } catch (e: Exception) {
            Log.e("SFTP", "Exception while stopping the server", e)
        }
    }

    fun regeneratePassword(): String {
        val password = RandomHelper.randomString(28)
        passwordAuth.setPassword(password)
        return password
    }

    internal class SimplePasswordAuthenticator : PasswordAuthenticator {
        private var sha: MessageDigest? = null

        init {
            try {
                sha = MessageDigest.getInstance("SHA-256")
            } catch (e: NoSuchAlgorithmException) {
                throw RuntimeException(e)
            }
        }

        fun setPassword(password: String) {
            sha!!.digest(password.toByteArray(StandardCharsets.UTF_8))
        }

        var passwordHash: ByteArray = byteArrayOf()

        override fun authenticate(user: String, password: String, session: ServerSession): Boolean {
            val receivedPasswordHash = sha!!.digest(password.toByteArray(StandardCharsets.UTF_8))
            return user == USER && constantTimeCompare(passwordHash, receivedPasswordHash)
        }
    }

    internal class SimplePublicKeyAuthenticator : PublickeyAuthenticator {
        var deviceKey: PublicKey? = null

        override fun authenticate(user: String, key: PublicKey, session: ServerSession): Boolean =
            deviceKey == key
    }

    companion object {
        private const val STARTPORT = 1739
        private const val ENDPORT = 1764

        const val USER: String = "kdeconnect"

        init {
            System.setProperty(SECURITY_PROVIDER_REGISTRARS, "") // disable BouncyCastle
            System.setProperty(
                "org.apache.sshd.common.io.IoServiceFactoryFactory",
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    // Use MINA instead NIO2 due to compatibility issues
                    // Android 7.0 (API 24) and below have issues with NIO2
                    "org.apache.sshd.mina.MinaServiceFactoryFactory"
                } else {
                    "org.apache.sshd.common.io.nio2.Nio2ServiceFactoryFactory"
                }
            )
            PathUtils.setUserHomeFolderResolver { Path.of("/") } // TODO: Remove it when SSHD Core is fixed
        }
    }
}
