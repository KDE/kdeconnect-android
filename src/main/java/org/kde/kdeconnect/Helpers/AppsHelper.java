package org.kde.kdeconnect.Helpers;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.util.Log;

public class AppsHelper {

    public static String appNameLookup(Context context, String packageName) {

        try {

            PackageManager pm = context.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo( packageName, 0);

            return pm.getApplicationLabel(ai).toString();

        } catch (final PackageManager.NameNotFoundException e) {

            e.printStackTrace();
            Log.e("AppsHelper","Could not resolve name "+packageName);

            return null;

        }

    }

    public static Drawable appIconLookup(Context context, String packageName) {

        try {

            PackageManager pm = context.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo( packageName, 0);
            return pm.getApplicationIcon(ai);

        } catch (final PackageManager.NameNotFoundException e) {

            e.printStackTrace();
            Log.e("AppsHelper","Could not find icon for "+packageName);

            return null;

        }

    }



}