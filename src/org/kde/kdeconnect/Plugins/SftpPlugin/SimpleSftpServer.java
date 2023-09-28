/*
 * SPDX-FileCopyrightText: 2014 Samoilenko Yuri <kinnalru@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.SftpPlugin;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import org.apache.sshd.SshServer;
import org.apache.sshd.common.file.nativefs.NativeFileSystemFactory;
import org.apache.sshd.common.keyprovider.AbstractKeyPairProvider;
import org.apache.sshd.common.util.SecurityUtils;
import org.apache.sshd.server.PasswordAuthenticator;
import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.command.ScpCommandFactory;
import org.apache.sshd.server.kex.DHG14;
import org.apache.sshd.server.kex.ECDHP256;
import org.apache.sshd.server.kex.ECDHP384;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.sftp.SftpSubsystem;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Helpers.RandomHelper;
import org.kde.kdeconnect.Helpers.SecurityHelpers.RsaHelper;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class SimpleSftpServer {
    private static final int STARTPORT = 1739;
    private static final int ENDPORT = 1764;

    static final String USER = "kdeconnect";

    private int port = -1;
    private boolean started = false;

    private final SimplePasswordAuthenticator passwordAuth = new SimplePasswordAuthenticator();
    private final SimplePublicKeyAuthenticator keyAuth = new SimplePublicKeyAuthenticator();

    static {
        SecurityUtils.setRegisterBouncyCastle(false);
    }

    boolean initialized = false;

    private final SshServer sshd = SshServer.setUpDefaultServer();
    private AndroidFileSystemFactory safFileSystemFactory;

    public void setSafRoots(List<SftpPlugin.StorageInfo> storageInfoList) {
        safFileSystemFactory.initRoots(storageInfoList);
    }

    void initialize(Context context, Device device) throws GeneralSecurityException {

        sshd.setKeyExchangeFactories(Arrays.asList(
                new ECDHP256.Factory(), // ecdh-sha2-nistp256
                new ECDHP384.Factory(), // ecdh-sha2-nistp384
                new DHG14_256.Factory(), // diffie-hellman-group14-sha256
                new DHG14.Factory() // diffie-hellman-group14-sha1. Left for backwards-compatibility.
        ));

        //Reuse this device keys for the ssh connection as well
        final KeyPair keyPair;
        PrivateKey privateKey = RsaHelper.getPrivateKey(context);
        PublicKey publicKey = RsaHelper.getPublicKey(context);
        keyPair = new KeyPair(publicKey, privateKey);
        sshd.setKeyPairProvider(new AbstractKeyPairProvider() {
            @Override
            public Iterable<KeyPair> loadKeys() {
                return Collections.singletonList(keyPair);
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            sshd.setFileSystemFactory(new NativeFileSystemFactory());
        } else {
            safFileSystemFactory = new AndroidFileSystemFactory(context);
            sshd.setFileSystemFactory(safFileSystemFactory);
        }
        sshd.setCommandFactory(new ScpCommandFactory());
        sshd.setSubsystemFactories(Collections.singletonList(new SftpSubsystem.Factory()));

        keyAuth.deviceKey = device.getCertificate().getPublicKey();

        sshd.setPublickeyAuthenticator(keyAuth);
        sshd.setPasswordAuthenticator(passwordAuth);

        initialized = true;
    }

    public boolean start() {
        if (!started) {
            passwordAuth.password = RandomHelper.randomString(28);

            port = STARTPORT;
            while (!started) {
                try {
                    sshd.setPort(port);
                    sshd.start();
                    started = true;
                } catch (IOException e) {
                    port++;
                    if (port >= ENDPORT) {
                        port = -1;
                        Log.e("SftpServer", "No more ports available");
                        return false;
                    }
                }
            }
        }

        return true;
    }

    public void stop() {
        try {
            started = false;
            sshd.stop(true);
        } catch (Exception e) {
            Log.e("SFTP", "Exception while stopping the server", e);
        }
    }

    public boolean isStarted() {
        return started;
    }

    String getPassword() {
        return passwordAuth.password;
    }

    int getPort() {
        return port;
    }

    public boolean isInitialized() {
        return initialized;
    }

    static class SimplePasswordAuthenticator implements PasswordAuthenticator {

        String password;

        @Override
        public boolean authenticate(String user, String password, ServerSession session) {
            return user.equals(SimpleSftpServer.USER) && password.equals(this.password);
        }
    }

    static class SimplePublicKeyAuthenticator implements PublickeyAuthenticator {

        PublicKey deviceKey;

        @Override
        public boolean authenticate(String user, PublicKey key, ServerSession session) {
            return deviceKey.equals(key);
        }

    }

}
