package org.kde.kdeconnect.Helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class OnBootHelper {

    public static boolean shouldRunOnBoot(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean("onBoot",true);
    }

}
