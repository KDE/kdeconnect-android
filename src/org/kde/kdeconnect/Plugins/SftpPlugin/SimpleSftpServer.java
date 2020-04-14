/*
 * Copyright 2014 Samoilenko Yuri <kinnalru@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License or (at your option) version 3 or any later version
 * accepted by the membership of KDE e.V. (or its successor approved
 * by the membership of KDE e.V.), which shall act as a proxy
 * defined in Section 14 of version 3 of the license.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kde.kdeconnect.Plugins.SftpPlugin;

import android.content.Context;
import android.util.Log;

import org.apache.sshd.SshServer;
import org.apache.sshd.common.keyprovider.AbstractKeyPairProvider;
import org.apache.sshd.common.util.SecurityUtils;
import org.apache.sshd.server.PasswordAuthenticator;
import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.command.ScpCommandFactory;
import org.apache.sshd.server.kex.DHG14;
import org.apache.sshd.server.kex.ECDHP384;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.sftp.SftpSubsystem;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Helpers.RandomHelper;
import org.kde.kdeconnect.Helpers.SecurityHelpers.RsaHelper;
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
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
        Security.insertProviderAt(SslHelper.BC, 1);
        SecurityUtils.setRegisterBouncyCastle(false);
    }

    private final SshServer sshd = SshServer.setUpDefaultServer();
    private AndroidFileSystemFactory fileSystemFactory;

    void init(Context context, Device device) throws GeneralSecurityException {

        sshd.setKeyExchangeFactories(Arrays.asList(
                new ECDHP384.Factory(), // This is the best we have in mina-sshd 0.14.0 -- Upgrading is non-trivial
                new DHG14.Factory() // Left for backwards-compatibility, but should probably be removed
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

        fileSystemFactory = new AndroidFileSystemFactory(context);
        sshd.setFileSystemFactory(fileSystemFactory);
        sshd.setCommandFactory(new ScpCommandFactory());
        sshd.setSubsystemFactories(Collections.singletonList(new SftpSubsystem.Factory()));

        keyAuth.deviceKey = device.certificate.getPublicKey();

        sshd.setPublickeyAuthenticator(keyAuth);
        sshd.setPasswordAuthenticator(passwordAuth);
    }

    public boolean start(List<SftpPlugin.StorageInfo> storageInfoList) {
        if (!started) {
            fileSystemFactory.initRoots(storageInfoList);
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

    String getLocalIpAddress() {
        String ip6 = null;
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();

                // Anything with rmnet is related to cellular connections or USB
                // tethering mechanisms.  See:
                //
                // https://android.googlesource.com/kernel/msm/+/android-msm-flo-3.4-kitkat-mr1/Documentation/usb/gadget_rmnet.txt
                //
                // If we run across an interface that has this, we can safely
                // ignore it.  In fact, it's much safer to do.  If we don't, we
                // might get invalid IP adddresses out of it.
                if (intf.getDisplayName().contains("rmnet")) continue;

                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        String address = inetAddress.getHostAddress();
                        if (inetAddress instanceof Inet4Address) { //Prefer IPv4 over IPv6, because sshfs doesn't seem to like IPv6
                            return address;
                        } else {
                            ip6 = address;
                        }
                    }
                }
            }
        } catch (SocketException ignored) {
        }
        return ip6;
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
