package org.kde.connect.PackageInterfaces;

import android.R;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.util.Log;

import org.kde.connect.Device;
import org.kde.connect.NetworkPackage;


public class MprisControlPackageInterface extends BasePackageInterface {

    Context context;

    public MprisControlPackageInterface(Context ctx) {
        context = ctx;
    }

    public void sendAction(String s) {
        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_MPRIS);
        np.set("action",s);
        sendPackage(np);
    }

    @Override
    public boolean onPackageReceived(Device d, NetworkPackage np) {
        if (np.getType().equals(NetworkPackage.PACKAGE_TYPE_MPRIS)) {
            //TODO
        }
        return false;
    }

}
