/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.SftpPlugin;

import org.apache.sshd.common.KeyExchange;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.digest.SHA256;
import org.apache.sshd.common.kex.AbstractDH;
import org.apache.sshd.common.kex.DH;
import org.apache.sshd.common.kex.DHGroupData;
import org.apache.sshd.server.kex.AbstractDHGServer;

public class DHG14_256 extends AbstractDHGServer {

    public static class Factory implements NamedFactory<KeyExchange> {

        public String getName() {
            return "diffie-hellman-group14-sha256";
        }

        public KeyExchange create() {
            return new DHG14_256();
        }

    }

    @Override
    protected AbstractDH getDH() throws Exception {
        DH dh = new DH(new SHA256.Factory());
        dh.setG(DHGroupData.getG());
        dh.setP(DHGroupData.getP14());
        return dh;
    }

}
