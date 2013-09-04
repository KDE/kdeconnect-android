package org.kde.connect.Plugins.BatteryPlugin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.os.BatteryManager;
import android.util.Log;
import android.widget.Button;

import org.kde.connect.NetworkPackage;
import org.kde.connect.Plugins.Plugin;
import org.kde.kdeconnect_tp.R;

public class BatteryPlugin extends Plugin {

    private NetworkPackage lastPackage = null;

    private IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);

    /*static {
        PluginFactory.registerPlugin(BatteryPlugin.class);
    }*/

    @Override
    public String getPluginName() {
        return "plugin_battery";
    }

    @Override
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_battery);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_battery_desc);
    }

    @Override
    public Drawable getIcon() {
        return context.getResources().getDrawable(R.drawable.icon);
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }


    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            Log.e("BatteryPlugin", "Battery event");

            boolean isCharging = (0 != intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0));

            int currentCharge = 100;
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            if (status != BatteryManager.BATTERY_STATUS_FULL) {
                Intent batteryStatus = context.registerReceiver(null, filter);
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                currentCharge = level*100 / scale;
            }

            //Only notify if change is meaningful enough
            if (lastPackage == null
                || (
                    isCharging != lastPackage.getBoolean("isCharging")
                    || currentCharge != lastPackage.getInt("currentCharge")
                )
            ) {
                NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_BATTERY);
                np.set("isCharging", isCharging);
                np.set("currentCharge", currentCharge);
                device.sendPackage(np);
                lastPackage = np;
            }

        }
    };

    @Override
    public boolean onCreate() {
        context.registerReceiver(receiver, filter);
        return true;
    }

    @Override
    public void onDestroy() {
        context.unregisterReceiver(receiver);
    }

    @Override
    public boolean onPackageReceived(NetworkPackage np) {
        if (!np.getType().equals(NetworkPackage.PACKAGE_TYPE_BATTERY)) return false;

        if (np.getBoolean("request")) {
            if (lastPackage != null) {
                device.sendPackage(lastPackage);
            }
        }

        return true;
    }

    @Override
    public AlertDialog getErrorDialog(Context baseContext) {
        return null;
    }

    @Override
    public Button getInterfaceButton(Activity activity) {
        return null;
    }
}
