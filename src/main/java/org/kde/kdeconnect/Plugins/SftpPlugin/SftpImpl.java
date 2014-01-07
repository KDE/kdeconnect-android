package org.kde.kdeconnect.Plugins.SftpPlugin;

import android.util.Log;

import org.apache.sshd.SshServer;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.PasswordAuthenticator;
import org.apache.sshd.server.command.ScpCommandFactory;
import org.apache.sshd.server.filesystem.NativeFileSystemFactory;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.sftp.SftpSubsystem;

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


    public void init() {
        passwordAuth.setUser(USER);
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider("key.ser"));

        sshd.setFileSystemFactory(new NativeFileSystemFactory());
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

// =======================================
// Comented to be example of customization od SSHD
// =======================================

//    static class SecureFileSystemFactory implements FileSystemFactory {
//        //
//        public SecureFileSystemFactory() {
//        }
//        //
//        @Override
//        public FileSystemView createFileSystemView(final Session username) {
//            final String userName = username.getUsername();
//            final String home = "/mnt/sdcard/";
//            return new SecureFileSystemView(home, userName, false);
//        }
//    }

//    static class SecureFileSystemView extends NativeFileSystemView {
//        // the first and the last character will always be '/'
//        // It is always with respect to the root directory.
//        private String currDir = "/";
//        private String rootDir = "/";
//        private String userName;
//        private boolean isReadOnly = true;
//        private boolean caseInsensitive = false;
//        //
//        public SecureFileSystemView(final String rootDir, final String userName, final boolean isReadOnly) {
//            super(userName);
//            this.rootDir = NativeSshFile.normalizeSeparateChar(rootDir);
//            this.userName = userName;
//            this.isReadOnly = isReadOnly;
//        }
//        //
//        @Override
//        public SshFile getFile(final String file) {
//            return getFile(currDir, file);
//        }
//
//        @Override
//        public SshFile getFile(final SshFile baseDir, final String file) {
//            return getFile(baseDir.getAbsolutePath(), file);
//        }
//
//        //
//        protected SshFile getFile(final String dir, final String file) {
//            // get actual file object
//            String physicalName = NativeSshFile.getPhysicalName("/", dir, file, caseInsensitive);
//            File fileObj = new File(rootDir, physicalName); // chroot
//
//            // strip the root directory and return
//            String userFileName = physicalName.substring("/".length() - 1);
//            return new SecureSshFile(userFileName, fileObj, userName, isReadOnly);
//        }
//    }
//
//    static class SecureSshFile extends NativeSshFile {
//        final boolean isReadOnly;
//        //
//        public SecureSshFile(final String fileName, final File file, final String userName, final boolean isReadOnly) {
//            super(fileName, file, userName);
//            this.isReadOnly = isReadOnly;
//        }
//        //
//        public boolean isWritable() {
//            if (isReadOnly)
//                return false;
//            return super.isWritable();
//        }
//    }