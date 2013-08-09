package org.kde.connect.PackageInterfaces;

import android.text.ClipboardManager;
import android.content.Context;

import org.kde.connect.Device;
import org.kde.connect.NetworkPackage;

public class CompatClipboardPackageInterface extends BasePackageInterface {

    private Context context;
    private ClipboardManager cm;

    @Override
    public boolean onCreate(Context context) {

        this.context = context;

        cm = (ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);

        return false;

    }

    @Override
    public void onDestroy() {

    }

    @Override
    public boolean onPackageReceived(Device d, NetworkPackage np) {
        if (np.getType().equals(NetworkPackage.PACKAGE_TYPE_CLIPBOARD)) {
            cm.setText(np.getString("content"));
            return true;
        }
        return false;
    }

    public boolean onDeviceConnected(Device d) {
        return false;
    }

}
