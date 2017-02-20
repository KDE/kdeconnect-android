package org.kde.kdeconnect.Helpers;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

public class NetworkHelper {

    public static boolean isOnMobileNetwork(Context context) {
        return false; //This looks a bit dangerous and I prefer not to use it in the next stable release.
        /*final ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = connMgr.getActiveNetworkInfo();
        return (info != null && info.getType() == ConnectivityManager.TYPE_MOBILE);
        */
    }

}
