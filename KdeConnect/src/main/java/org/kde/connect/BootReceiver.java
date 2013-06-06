package org.kde.connect;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class BootReceiver  extends BroadcastReceiver
{
    public void onReceive(Context context, Intent intent)
    {

        if (intent.getAction() != Intent.ACTION_BOOT_COMPLETED) {
            Log.w("KdeConnect", "Received unexpected intent: " + intent);
        } else {
            Log.i("KdeConnect", "BootReceiver");
            context.startService(new Intent(context,BackgroundService.class));
        }

    }
}
