package org.kde.kdeconnect.Plugins.SftpPlugin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.widget.Button;

import org.apache.sshd.SshServer;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.PasswordAuthenticator;
import org.apache.sshd.server.command.ScpCommandFactory;
import org.apache.sshd.server.filesystem.NativeFileSystemFactory;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.sftp.SftpSubsystem;
import org.kde.kdeconnect.NetworkPackage;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect_tp.R;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Enumeration;

public class SftpPlugin extends Plugin {

    private static final int PORT = 8022;
    private static final String USER = "kdeconnect";
    private static final String PASSWORD = "kdeconnectpassword";

    private final SshServer sshd = SshServer.setUpDefaultServer();
    private boolean started = false;

    public class SimplePasswordAuthenticator implements PasswordAuthenticator {

        public void setUser(String user) {this.user = user;}

        public void setPassword(String password) {this.password = password;}

        @Override
        public boolean authenticate(String user, String password, ServerSession session) {
            return user.equals(this.user) && password.equals(this.password);
        }

        private String user;
        private String password;
    }


    private final SimplePasswordAuthenticator passwordAuth = new SimplePasswordAuthenticator();
    //private final SimplePublicKeyAuthenticator publicKeyAuth = new SimplePublicKeyAuthenticator();

    /*static {
        PluginFactory.registerPlugin(BatteryPlugin.class);
    }*/

    @Override
    public String getPluginName() {
        return "plugin_sftp";
    }

    @Override
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_sftp);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_sftp_desc);
    }

    @Override
    public Drawable getIcon() {
        return context.getResources().getDrawable(R.drawable.icon);
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent batteryIntent) {
//
//            Intent batteryChargeIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
//            int level = batteryChargeIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
//            int scale = batteryChargeIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 1);
//            int currentCharge = level*100 / scale;
//            boolean isCharging = (0 != batteryChargeIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0));
//            boolean lowBattery = Intent.ACTION_BATTERY_LOW.equals(batteryIntent.getAction());
//            int thresholdEvent = lowBattery? THRESHOLD_EVENT_BATTERY_LOW : THRESHOLD_EVENT_NONE;
//
//            if (lastInfo != null
//                && isCharging != lastInfo.getBoolean("isCharging")
//                && currentCharge != lastInfo.getInt("currentCharge")
//                && thresholdEvent != lastInfo.getInt("thresholdEvent")
//            ) {
//
//                //Do not send again if nothing has changed
//                return;
//
//            } else {
//
//                NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_BATTERY);
//                np.set("currentCharge", currentCharge);
//                np.set("isCharging", isCharging);
//                np.set("thresholdEvent", thresholdEvent);
//                device.sendPackage(np);
//                lastInfo = np;
//
//            }

        }
    };

    @Override
    public boolean onCreate() {
        passwordAuth.setUser(USER);
        passwordAuth.setPassword(PASSWORD);
        sshd.setPort(PORT);
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider("key.ser"));

        sshd.setFileSystemFactory(new NativeFileSystemFactory());
        //sshd.setShellFactory(new ProcessShellFactory(new String[] { "/bin/sh", "-i", "-l" }));
        sshd.setCommandFactory(new ScpCommandFactory());
        sshd.setSubsystemFactories(Arrays.<NamedFactory<Command>>asList(new SftpSubsystem.Factory()));

        sshd.setPasswordAuthenticator(passwordAuth);
        return true;
    }

    @Override
    public void onDestroy() {
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

    @Override
    public boolean onPackageReceived(NetworkPackage np) {
        if (!np.getType().equals(NetworkPackage.PACKAGE_TYPE_SFTP)) return false;
//
        if (np.getBoolean("startBrowsing")) {
            try {
                if (!started) {
                    sshd.start();
                    started = true;
                }

                NetworkPackage np2 = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_SFTP);
                np2.set("ip", getLocalIpAddress());
                np2.set("port", PORT);
                np2.set("user", USER);
                np2.set("password", PASSWORD);
                np2.set("home", Environment.getExternalStorageDirectory().getAbsolutePath());
                device.sendPackage(np2);
                return true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    @Override
    public AlertDialog getErrorDialog(Context baseContext) {
        return null;
    }

    @Override
    public Button getInterfaceButton(Activity activity) {
        return null;
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
}
