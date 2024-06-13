/*
 * SPDX-FileCopyrightText: 2024 ShellWen Chen <me@shellwen.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.SftpPlugin

import org.apache.sshd.common.digest.BuiltinDigests
import org.apache.sshd.common.kex.AbstractDH
import org.apache.sshd.common.kex.DHFactory
import org.apache.sshd.common.kex.DHG
import org.apache.sshd.common.kex.DHGroupData
import org.apache.sshd.common.util.security.SecurityUtils
import java.math.BigInteger

object DHG14_256Factory : DHFactory {
    override fun getName(): String = "diffie-hellman-group14-sha256"

    override fun isSupported(): Boolean = SecurityUtils.isBouncyCastleRegistered()

    override fun isGroupExchange(): Boolean = false

    override fun create(vararg params: Any?): AbstractDH {
        require(params.isEmpty()) { "No accepted parameters for $name" }
        return DHG(
            BuiltinDigests.sha256,
            BigInteger(DHGroupData.getP14()),
            BigInteger(DHGroupData.getG())
        )
    }
}