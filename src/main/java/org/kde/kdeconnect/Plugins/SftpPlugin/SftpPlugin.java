package org.kde.kdeconnect.Plugins.SftpPlugin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.widget.Button;

import org.kde.kdeconnect.NetworkPackage;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect_tp.R;

public class SftpPlugin extends Plugin {

    private static final SimpleSftpServer server = new SimpleSftpServer();

    /*static {
        PluginFactory.registerPlugin(SftpPlugin.class);
    }*/

    @Override
    public String getPluginName() {return "plugin_sftp";}

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
    public boolean isEnabledByDefault() {return true;}

    @Override
    public boolean onCreate() {
        server.init(context, device);
        return true;
    }

    @Override
    public void onDestroy() {
        server.stop();
    }

    @Override
    public boolean onPackageReceived(NetworkPackage np) {
        if (!np.getType().equals(NetworkPackage.PACKAGE_TYPE_SFTP)) return false;

        if (np.getBoolean("startBrowsing")) {
            if (server.start()) {
                NetworkPackage np2 = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_SFTP);
                np2.set("ip", server.getLocalIpAddress());
                np2.set("port", server.port);
                np2.set("user", server.passwordAuth.getUser());
                np2.set("password", server.passwordAuth.getPassword());
                np2.set("path", Environment.getExternalStorageDirectory().getAbsolutePath());
                device.sendPackage(np2);
                return true;
            }
        }
        return false;
    }

    @Override
    public AlertDialog getErrorDialog(Context baseContext) {return null;}

    @Override
    public Button getInterfaceButton(Activity activity) {return null;}

}
