/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.SftpPlugin;

import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.Signature;
import org.apache.sshd.common.signature.AbstractSignature;

public class SignatureRSASHA256 extends AbstractSignature {

    public static class Factory implements NamedFactory<Signature> {

        public String getName() {
            return "rsa-sha2-256";
        }

        public Signature create() {
            return new SignatureRSASHA256();
        }

    }

    public SignatureRSASHA256() {
        super("SHA256withRSA");
    }

    public byte[] sign() throws Exception {
        return signature.sign();
    }

    public boolean verify(byte[] sig) throws Exception {
        sig = extractSig(sig);
        return signature.verify(sig);
    }

}
