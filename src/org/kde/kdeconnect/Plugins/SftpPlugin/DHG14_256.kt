/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.Plugins.SftpPlugin

import org.apache.sshd.common.KeyExchange
import org.apache.sshd.common.NamedFactory
import org.apache.sshd.common.digest.SHA256
import org.apache.sshd.common.kex.AbstractDH
import org.apache.sshd.common.kex.DH
import org.apache.sshd.common.kex.DHGroupData
import org.apache.sshd.server.kex.AbstractDHGServer

class DHG14_256 : AbstractDHGServer() {
    class Factory : NamedFactory<KeyExchange> {
        override fun getName(): String = "diffie-hellman-group14-sha256"

        override fun create(): KeyExchange {
            return DHG14_256()
        }
    }

    @Throws(Exception::class)
    override fun getDH(): AbstractDH {
        return DH(SHA256.Factory()).apply {
            setG(DHGroupData.getG())
            setP(DHGroupData.getP14())
        }
    }
}
