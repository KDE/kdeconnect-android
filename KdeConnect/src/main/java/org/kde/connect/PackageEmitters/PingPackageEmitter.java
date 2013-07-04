package org.kde.connect.PackageEmitters;

import android.content.Context;
import android.util.Log;

import org.kde.connect.Types.NetworkPackage;


public class PingPackageEmitter extends BasePackageEmitter {

    public PingPackageEmitter(Context ctx) {
    }

    public void sendPing() {
        Log.e("PingPackageEmitter", "sendPing to "+countLinkedComputers());

        NetworkPackage lastPackage = new NetworkPackage("kdeconnect.ping");
        sendPackage(lastPackage);
    }

}
