package org.kde.connect.PackageInterfaces;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;

import org.kde.connect.Device;
import org.kde.connect.NetworkPackage;

public class BatteryMonitorPackageInterface extends BasePackageInterface {

    NetworkPackage lastPackage = null;

    public BatteryMonitorPackageInterface(final Context context) {
        final IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                Log.e("BatteryMonitorPackageInterface", "Battery event");

                boolean isCharging = (0 != intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0));

                int currentCharge = 100;
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                if (status != BatteryManager.BATTERY_STATUS_FULL) {
                    Intent batteryStatus = context.registerReceiver(null, ifilter);
                    int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    currentCharge = level*100 / scale;
                }

                //Only notify if change is meaningful enough
                if (lastPackage == null || (
                    isCharging != lastPackage.getBoolean("isCharging")
                    || currentCharge != lastPackage.getInt("currentCharge")
                    )
                ) {
                    NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_BATTERY);
                    np.set("isCharging", isCharging);
                    np.set("currentCharge", currentCharge);
                    sendPackage(np);
                    lastPackage = np;
                }
            }
        }, ifilter);
    }

    @Override
    public boolean onPackageReceived(Device d, NetworkPackage np) {
        //Do nothing
        return false;
    }

    @Override
    public boolean onDeviceConnected(Device d) {
        if (lastPackage != null) sendPackage(lastPackage);
        return true;
    }
}
