package org.kde.connect.Plugins;

import android.content.Context;

import org.kde.connect.Device;
import org.kde.connect.NetworkPackage;

public abstract class Plugin {

    protected Device device;
    protected Context context;

    public void setContext(Context context, Device device) {
        this.device = device;
        this.context = context;
    }

    //Functions to override
    public abstract boolean onCreate();
    public abstract void onDestroy();
    public abstract boolean onPackageReceived(NetworkPackage np);

}
