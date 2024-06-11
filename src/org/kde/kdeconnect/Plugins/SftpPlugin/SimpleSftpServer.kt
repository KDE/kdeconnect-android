/*
 * SPDX-FileCopyrightText: 2014 Samoilenko Yuri <kinnalru@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.Plugins.SftpPlugin

import android.content.Context
import android.os.Build
import android.util.Log
import org.apache.sshd.SshServer
import org.apache.sshd.common.NamedFactory
import org.apache.sshd.common.file.nativefs.NativeFileSystemFactory
import org.apache.sshd.common.keyprovider.AbstractKeyPairProvider
import org.apache.sshd.common.signature.SignatureDSA
import org.apache.sshd.common.signature.SignatureECDSA.NISTP256Factory
import org.apache.sshd.common.signature.SignatureECDSA.NISTP384Factory
import org.apache.sshd.common.signature.SignatureECDSA.NISTP521Factory
import org.apache.sshd.common.signature.SignatureRSA
import org.apache.sshd.common.util.SecurityUtils
import org.apache.sshd.server.Command
import org.apache.sshd.server.PasswordAuthenticator
import org.apache.sshd.server.PublickeyAuthenticator
import org.apache.sshd.server.command.ScpCommandFactory
import org.apache.sshd.server.kex.DHG14
import org.apache.sshd.server.kex.ECDHP256
import org.apache.sshd.server.kex.ECDHP384
import org.apache.sshd.server.kex.ECDHP521
import org.apache.sshd.server.session.ServerSession
import org.apache.sshd.server.sftp.SftpSubsystem
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.Helpers.RandomHelper
import org.kde.kdeconnect.Helpers.SecurityHelpers.RsaHelper
import org.kde.kdeconnect.Helpers.SecurityHelpers.constantTimeCompare
import java.io.IOException
import java.nio.charset.StandardCharsets
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

    private val sshd: SshServer = SshServer.setUpDefaultServer()

    private var safFileSystemFactory: AndroidFileSystemFactory? = null

    fun setSafRoots(storageInfoList: List<SftpPlugin.StorageInfo>) {
        safFileSystemFactory!!.initRoots(storageInfoList)
    }

    @Throws(GeneralSecurityException::class)
    fun initialize(context: Context?, device: Device) {
        sshd.signatureFactories =
            listOf(
                NISTP256Factory(),
                NISTP384Factory(),
                NISTP521Factory(),
                SignatureDSA.Factory(),
                SignatureRSASHA256.Factory(),
                SignatureRSA.Factory() // Insecure SHA1, left for backwards compatibility
            )

        sshd.keyExchangeFactories =
            listOf(
                ECDHP256.Factory(),  // ecdh-sha2-nistp256
                ECDHP384.Factory(),  // ecdh-sha2-nistp384
                ECDHP521.Factory(),  // ecdh-sha2-nistp521
                DHG14_256.Factory(),  // diffie-hellman-group14-sha256
                DHG14.Factory() // Insecure diffie-hellman-group14-sha1, left for backwards-compatibility.
            )

        // Reuse this device keys for the ssh connection as well
        val keyPair = KeyPair(
            RsaHelper.getPublicKey(context),
            RsaHelper.getPrivateKey(context)
        )
        sshd.keyPairProvider = object : AbstractKeyPairProvider() {
            override fun loadKeys(): Iterable<KeyPair> = listOf(keyPair)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            sshd.fileSystemFactory = NativeFileSystemFactory()
        } else {
            safFileSystemFactory = AndroidFileSystemFactory(context)
            sshd.fileSystemFactory = safFileSystemFactory
        }
        sshd.commandFactory = ScpCommandFactory()
        sshd.subsystemFactories =
            listOf<NamedFactory<Command>>(SftpSubsystem.Factory())

        keyAuth.deviceKey = device.certificate.publicKey

        sshd.publickeyAuthenticator = keyAuth
        sshd.passwordAuthenticator = passwordAuth

        this.isInitialized = true
    }

    fun start(): Boolean {
        if (!isStarted) {
            regeneratePassword()

            port = STARTPORT
            while (!isStarted) {
                try {
                    sshd.port = port
                    sshd.start()
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
            sshd.stop(true)
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
            SecurityUtils.setRegisterBouncyCastle(false)
        }
    }
}
