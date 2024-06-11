/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.Plugins.SftpPlugin

import org.apache.sshd.common.NamedFactory
import org.apache.sshd.common.Signature
import org.apache.sshd.common.signature.AbstractSignature

class SignatureRSASHA256 : AbstractSignature("SHA256withRSA") {
    class Factory : NamedFactory<Signature> {
        override fun getName(): String = "rsa-sha2-256"

        override fun create(): Signature {
            return SignatureRSASHA256()
        }
    }

    @Throws(Exception::class)
    override fun sign(): ByteArray {
        return signature.sign()
    }

    @Throws(Exception::class)
    override fun verify(sig: ByteArray): Boolean {
        return signature.verify(extractSig(sig))
    }
}
