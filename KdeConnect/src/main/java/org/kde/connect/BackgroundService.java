package org.kde.connect;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;

import org.kde.connect.PackageEmitters.BaseReceiver;
import org.kde.connect.PackageEmitters.PhoneCallReceiver;
import org.kde.connect.Types.NetworkPackage;

import java.util.ArrayList;

public class BackgroundService extends Service {

    SharedPreferences settings;
    ArrayList<Announcer> announcers = new ArrayList<Announcer>();
    ArrayList<ComputerLink> computerLinks = new ArrayList<ComputerLink>();
    ArrayList<BaseReceiver> receivers = new ArrayList<BaseReceiver>();

    private void addComputerLink(ComputerLink cl) {

        computerLinks.add(cl);

        if (settings.getBoolean("listenCalls", true)) {
            receivers.add(new PhoneCallReceiver(getApplicationContext(), cl));
        }

        NetworkPackage p = new NetworkPackage(System.currentTimeMillis());
        p.setType(NetworkPackage.Type.PING);
        cl.sendPackage(p);

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e("BackgroundService","Starting");

        for (Announcer a : announcers) {
            a.startAnnouncing(new Announcer.ConnexionReceiver() {
                @Override
                public void onPair(ComputerLink cl) {
                    addComputerLink(cl);
                }
            });
        }

        return Service.START_STICKY;
    }

    @Override
    public void onCreate() {
        Log.e("BackgroundService","Creating");

        settings = getSharedPreferences("KdeConnect", 0);

        announcers.add(new AvahiAnnouncer(this));

        super.onCreate();
    }

    @Override
    public void onDestroy() {
        Log.e("BackgroundService","Destroying");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.e("BackgroundService", "Binding");
        return null;
    }

}
