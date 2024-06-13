/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 * SPDX-FileCopyrightText: 2024 ShellWen Chen <me@shellwen.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.Plugins.SftpPlugin

import org.apache.sshd.common.session.SessionContext
import org.apache.sshd.common.signature.AbstractSignature
import org.apache.sshd.common.signature.Signature
import org.apache.sshd.common.signature.SignatureFactory
import org.apache.sshd.common.util.ValidateUtils


class SignatureRSASHA256 : AbstractSignature("SHA256withRSA") {
    object Factory : SignatureFactory {
        override fun isSupported(): Boolean = true
        override fun getName(): String = "rsa-sha2-256"

        override fun create(): Signature {
            return SignatureRSASHA256()
        }
    }

    @Throws(Exception::class)
    override fun sign(session: SessionContext): ByteArray {
        return signature.sign()
    }

    @Throws(Exception::class)
    override fun verify(session: SessionContext, sig: ByteArray): Boolean {
        var data = sig
        val encoding = extractEncodedSignature(data) { type ->
            type == "rsa-sha2-256"
        }
        if (encoding != null) {
            data = encoding.value
        }

        return signature.verify(data)
    }
}
