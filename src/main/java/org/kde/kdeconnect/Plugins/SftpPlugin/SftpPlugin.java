package org.kde.kdeconnect.Plugins.SftpPlugin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.widget.Button;

import org.kde.kdeconnect.Helpers.StorageHelper;
import org.kde.kdeconnect.NetworkPackage;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect_tp.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SftpPlugin extends Plugin {

    private static final SimpleSftpServer server = new SimpleSftpServer();

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
    public boolean hasSettings() {
        return false;
    }

    @Override
    public boolean isEnabledByDefault() { return true; }

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

                //Kept for compatibility, but new desktop clients will read "multiPaths" instead,
                // that supports devices with more than one external storage
                np2.set("path", Environment.getExternalStorageDirectory().getAbsolutePath());

                List<StorageHelper.StorageInfo> storageList = StorageHelper.getStorageList();
                ArrayList<String> paths = new ArrayList<String>();
                ArrayList<String> pathNames = new ArrayList<String>();

                for (StorageHelper.StorageInfo storage : storageList) {
                    paths.add(storage.path);
                    StringBuilder res = new StringBuilder();

                    if (storageList.size() > 1) {
                        if (!storage.removable) {
                            res.append(context.getString(R.string.sftp_internal_storage));
                        } else if (storage.number > 1) {
                            res.append(context.getString(R.string.sftp_sdcard_num, storage.number));
                        } else {
                            res.append(context.getString(R.string.sftp_sdcard));
                        }
                    } else {
                        res.append(context.getString(R.string.sftp_all_files));
                    }
                    if (storage.readonly) {
                        res.append(" ");
                        res.append(context.getString(R.string.sftp_readonly));
                    }
                    pathNames.add(res.toString());
                }

                //Shortcut for users that only want to browse camera pictures
                String cameraDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera";
                if (new File(cameraDir).exists()) {
                    paths.add(cameraDir);
                    pathNames.add(context.getString(R.string.sftp_camera));
                }

                np2.set("multiPaths", paths);
                np2.set("pathNames", pathNames);

                device.sendPackage(np2);

                return true;
            }
        }
        return false;
    }

    @Override
    public AlertDialog getErrorDialog(Activity deviceActivity) { return null; }

    @Override
    public Button getInterfaceButton(Activity activity) { return null; }

}
