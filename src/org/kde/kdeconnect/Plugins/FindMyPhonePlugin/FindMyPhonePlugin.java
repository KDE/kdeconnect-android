package org.kde.kdeconnect.Plugins.FindMyPhonePlugin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.Button;

import org.kde.kdeconnect.NetworkPackage;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect_tp.R;


/**
 * Created by vineet on 1/11/14.
 * and David Edmundson 2015
 */
public class FindMyPhonePlugin extends Plugin {

    public final static String PACKAGE_TYPE_FINDMYPHONE = "kdeconnect.findmyphone";
    public final static String PACKAGE_TYPE_FINDMYPHONE_REQUEST = "kdeconnect.findmyphone.request";

    @Override
    public String getDisplayName() {
        return context.getString(R.string.findmyphone_title);
    }

    @Override
    public String getDescription() {
        return context.getString(R.string.findmyphone_description);
    }

    @Override
    public boolean onPackageReceived(NetworkPackage np) {
        if (np.getType().equals(PACKAGE_TYPE_FINDMYPHONE_REQUEST)) {
            //Log.e("PingPackageReceiver", "was a find my phone!");

            Intent intent = new Intent(context,FindMyPhoneActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;

        }
        return false;
    }

    @Override
    public String[] getSupportedPackageTypes() {
        return new String[]{PACKAGE_TYPE_FINDMYPHONE_REQUEST};
    }

    @Override
    public String[] getOutgoingPackageTypes() {
        return new String[0];
    }
}
