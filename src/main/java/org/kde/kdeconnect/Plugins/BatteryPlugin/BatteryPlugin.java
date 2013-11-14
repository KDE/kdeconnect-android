package org.kde.kdeconnect.Plugins.BatteryPlugin;

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

import org.kde.kdeconnect.NetworkPackage;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect_tp.R;

public class BatteryPlugin extends Plugin {

    // keep these fields in sync with kdeconnect-kded:BatteryPlugin.h:ThresholdBatteryEvent
    private static final int THRESHOLD_EVENT_NONE= 0;
    private static final int THRESHOLD_EVENT_BATTERY_LOW = 1;

    NetworkPackage lastInfo = null;

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
        public void onReceive(Context context, Intent batteryIntent) {

            Intent batteryChargeIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int level = batteryChargeIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryChargeIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 1);
            int currentCharge = level*100 / scale;
            boolean isCharging = (0 != batteryChargeIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0));
            boolean lowBattery = Intent.ACTION_BATTERY_LOW.equals(batteryIntent.getAction());
            int thresholdEvent = lowBattery? THRESHOLD_EVENT_BATTERY_LOW : THRESHOLD_EVENT_NONE;

            if (lastInfo != null
                && isCharging != lastInfo.getBoolean("isCharging")
                && currentCharge != lastInfo.getInt("currentCharge")
                && thresholdEvent != lastInfo.getInt("thresholdEvent")
            ) {

                //Do not send again if nothing has changed
                return;

            } else {

                NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_BATTERY);
                np.set("currentCharge", currentCharge);
                np.set("isCharging", isCharging);
                np.set("thresholdEvent", thresholdEvent);
                device.sendPackage(np);
                lastInfo = np;

            }

        }
    };

    @Override
    public boolean onCreate() {
        context.registerReceiver(receiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        context.registerReceiver(receiver, new IntentFilter(Intent.ACTION_BATTERY_LOW));
        return true;
    }

    @Override
    public void onDestroy() {
        //It's okay to call this only once, even though we registered it for two filters
        context.unregisterReceiver(receiver);
    }

    @Override
    public boolean onPackageReceived(NetworkPackage np) {
        if (!np.getType().equals(NetworkPackage.PACKAGE_TYPE_BATTERY)) return false;

        if (np.getBoolean("request")) {
            if (lastInfo != null) {
                device.sendPackage(lastInfo);
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
