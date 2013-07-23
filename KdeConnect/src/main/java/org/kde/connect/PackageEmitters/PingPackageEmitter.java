package org.kde.connect.PackageEmitters;

import android.content.Context;
import android.util.Log;

import org.kde.connect.Device;
import org.kde.connect.NetworkPackage;


public class PingPackageEmitter extends BasePackageEmitter {

    public PingPackageEmitter(Context ctx) {
    }

    @Override
    public void addDevice(Device d) {
        super.addDevice(d);
        Log.e("PinkPackageEmitter","addDevice: "+d.getName());
    }

    public void sendPing() {
        Log.e("PingPackageEmitter", "sendPing to "+countLinkedDevices());

        NetworkPackage lastPackage = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_PING);
        sendPackage(lastPackage);
    }

}
