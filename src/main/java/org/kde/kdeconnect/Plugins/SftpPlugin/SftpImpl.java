package org.kde.kdeconnect.Plugins.SftpPlugin;

import android.content.Context;
import android.util.Log;

import org.apache.sshd.SshServer;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.Session;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.FileSystemFactory;
import org.apache.sshd.server.FileSystemView;
import org.apache.sshd.server.PasswordAuthenticator;
import org.apache.sshd.server.SshFile;
import org.apache.sshd.server.command.ScpCommandFactory;
import org.apache.sshd.server.filesystem.NativeFileSystemView;
import org.apache.sshd.server.filesystem.NativeSshFile;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.sftp.SftpSubsystem;

import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Enumeration;

class SimplePasswordAuthenticator implements PasswordAuthenticator {

    public void setUser(String user) {this.user = user;}
    public String getUser() {return this.user;}

    public void setPassword(String password) {this.password = password;}
    public String getPassword() {return this.password;}

    @Override
    public boolean authenticate(String user, String password, ServerSession session) {
        return user.equals(this.user) && password.equals(this.password);
    }

    private String user;
    private String password;
}

class SimpleSftpServer {
    private static final int STARTPORT = 1739;
    private static final int ENDPORT = 1764;

    private static final String USER = "kdeconnect";

    public static int port = -1;
    private static boolean started = false;

    public final SimplePasswordAuthenticator passwordAuth = new SimplePasswordAuthenticator();
    private final SshServer sshd = SshServer.setUpDefaultServer();


    public void init(Context ctx) {
        passwordAuth.setUser(USER);
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(ctx.getFilesDir() + "/sftpd.ser"));

        //sshd.setFileSystemFactory(new NativeFileSystemFactory());
        sshd.setFileSystemFactory(new SecureFileSystemFactory());
        //sshd.setShellFactory(new ProcessShellFactory(new String[] { "/bin/sh", "-i", "-l" }));
        sshd.setCommandFactory(new ScpCommandFactory());
        sshd.setSubsystemFactories(Arrays.<NamedFactory<Command>>asList(new SftpSubsystem.Factory()));

        sshd.setPasswordAuthenticator(passwordAuth);
    }

    public boolean start() {
        if (!started) {
            String password = Long.toHexString(Double.doubleToLongBits(Math.random()));
            passwordAuth.setPassword(password);

            port = STARTPORT;
            while(!started) {
                try {
                    sshd.setPort(port);
                    sshd.start();
                    started = true;
                } catch(Exception e) {
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
        } catch (InterruptedException e) {

        }
    }

    public String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        return inetAddress.getHostAddress().toString();
                    }
                }
            }
        } catch (SocketException ex) {
        }
        return null;
    }

}

    class SecureFileSystemFactory implements FileSystemFactory {

        public SecureFileSystemFactory() {}

       @Override
        public FileSystemView createFileSystemView(final Session username) {
            final String base = "/";
            return new SecureFileSystemView(base, username.getUsername());
        }
    }

    class SecureFileSystemView extends NativeFileSystemView {
        // the first and the last character will always be '/'
        // It is always with respect to the root directory.
        private String currDir = "/";
        private String rootDir = "/";
        private String userName;
        private boolean caseInsensitive = false;
        //
        public SecureFileSystemView(final String rootDir, final String userName) {
            super(userName);
            this.rootDir = NativeSshFile.normalizeSeparateChar(rootDir);
            this.userName = userName;
        }
        //
        @Override
        public SshFile getFile(final String file) {
            Log.e("getFile:", file);
            return getFile(currDir, file);
        }

        @Override
        public SshFile getFile(final SshFile baseDir, final String file) {
            Log.e("getFile2:", baseDir.getAbsolutePath() + " -- " + file);
            return getFile(baseDir.getAbsolutePath(), file);
        }

        //
        protected SshFile getFile(final String dir, final String file) {
            // get actual file object
            String physicalName = NativeSshFile.getPhysicalName("/", dir, file, caseInsensitive);
            File fileObj = new File(rootDir, physicalName); // chroot

            // strip the root directory and return
            String userFileName = physicalName.substring("/".length() - 1);
            return new SecureSshFile(this, userFileName, fileObj, userName);
        }
    }

    class SecureSshFile extends NativeSshFile {
        //
        public SecureSshFile(final SecureFileSystemView view, final String fileName, final File file, final String userName) {
            super(fileName, file, userName);
        }
    }