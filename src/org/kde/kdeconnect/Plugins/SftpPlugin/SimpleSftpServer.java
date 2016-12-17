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
import android.net.Uri;
import android.util.Log;

import org.apache.sshd.SshServer;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.Session;
import org.apache.sshd.common.util.SecurityUtils;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.FileSystemFactory;
import org.apache.sshd.server.FileSystemView;
import org.apache.sshd.server.PasswordAuthenticator;
import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.SshFile;
import org.apache.sshd.server.command.ScpCommandFactory;
import org.apache.sshd.server.filesystem.NativeFileSystemView;
import org.apache.sshd.server.filesystem.NativeSshFile;
import org.apache.sshd.server.kex.DHG1;
import org.apache.sshd.server.kex.DHG14;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.sftp.SftpSubsystem;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Helpers.MediaStoreHelper;
import org.kde.kdeconnect.Helpers.RandomHelper;
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.PublicKey;
import java.security.Security;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;

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

    public void init(Context context, Device device) {

        sshd.setKeyExchangeFactories(Arrays.asList(
                new DHG14.Factory(),
                new DHG1.Factory()));

        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(context.getFilesDir() + "/sftpd.ser"));

        sshd.setFileSystemFactory(new AndroidFileSystemFactory(context));
        sshd.setCommandFactory(new ScpCommandFactory());
        sshd.setSubsystemFactories(Collections.singletonList((NamedFactory<Command>)new SftpSubsystem.Factory()));

        if (device.publicKey != null) {
            keyAuth.deviceKey = device.publicKey;
            sshd.setPublickeyAuthenticator(keyAuth);
        }
        sshd.setPasswordAuthenticator(passwordAuth);
    }

    public boolean start() {
        if (!started) {

            passwordAuth.password = RandomHelper.randomString(28);

            port = STARTPORT;
            while(!started) {
                try {
                    sshd.setPort(port);
                    sshd.start();
                    started = true;
                } catch(Exception e) {
                    e.printStackTrace();
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
            sshd.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getPassword() {
        return passwordAuth.password;
    }

    public int getPort() {
        return port;
    }

    public String getLocalIpAddress() {
        String ip6 = null;
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        String address = inetAddress.getHostAddress();
                        if(inetAddress instanceof Inet4Address) { //Prefer IPv4 over IPv6, because sshfs doesn't seem to like IPv6
                            return address;
                        } else {
                            ip6 = address;
                        }
                    }
                }
            }
        } catch (SocketException ex) {
        }
        return ip6;
    }

    static class AndroidFileSystemFactory implements FileSystemFactory {

        final private Context context;

        public AndroidFileSystemFactory(Context context) {
            this.context = context;
        }

        @Override
        public FileSystemView createFileSystemView(final Session username) {
            return new AndroidFileSystemView(username.getUsername(), context);
        }
    }

    static class AndroidFileSystemView extends NativeFileSystemView {

        final private String userName;
        final private Context context;

        public AndroidFileSystemView(final String userName, Context context) {
            super(userName, true);
            this.userName = userName;
            this.context = context;
        }

        @Override
        protected SshFile getFile(final String dir, final String file) {
            File fileObj = new File(dir, file);
            return new AndroidSshFile(fileObj, userName, context);
        }
    }

    static class AndroidSshFile extends NativeSshFile {

        final private Context context;
        final private File file;

        public AndroidSshFile(final File file, final String userName, Context context) {
            super(file.getAbsolutePath(), file, userName);
            this.context = context;
            this.file = file;
        }

        @Override
        public boolean delete() {
            //Log.e("Sshd", "deleting file");
            boolean ret = super.delete();
            if (ret) {
                MediaStoreHelper.indexFile(context, Uri.fromFile(file));
            }
            return ret;

        }

        @Override
        public boolean create() throws IOException {
            //Log.e("Sshd", "creating file");
            boolean ret = super.create();
            if (ret) {
                MediaStoreHelper.indexFile(context, Uri.fromFile(file));
            }
            return ret;

        }
    }

    static class SimplePasswordAuthenticator implements PasswordAuthenticator {

        public String password;

        @Override
        public boolean authenticate(String user, String password, ServerSession session) {
            return user.equals(SimpleSftpServer.USER) && password.equals(this.password);
        }
    }

    static class SimplePublicKeyAuthenticator implements PublickeyAuthenticator {

        public PublicKey deviceKey;

        @Override
        public boolean authenticate(String user, PublicKey key, ServerSession session) {
            return deviceKey.equals(key);
        }

    }

}
