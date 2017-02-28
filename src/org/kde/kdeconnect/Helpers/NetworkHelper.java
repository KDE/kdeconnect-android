package org.kde.kdeconnect.Helpers;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.util.Log;

public class NetworkHelper {

    public static boolean isOnMobileNetwork(Context context) {
        if (context == null || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) {
            return false; //No good way to know it
        }
        boolean mobile = false;
        final ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network[] networks = connMgr.getAllNetworks();
        for (Network network : networks) {
            NetworkInfo info = connMgr.getNetworkInfo(network);
            if (info.getType() == ConnectivityManager.TYPE_MOBILE) {
                mobile = info.isConnected();
                continue;
            }
            Log.e(info.getTypeName(),""+info.isAvailable());
            if (info.isAvailable()) return false; //We are connected to at least one non-mobile network
        }
        return mobile;
    }

}
