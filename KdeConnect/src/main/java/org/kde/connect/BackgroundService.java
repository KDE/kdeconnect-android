package org.kde.connect;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import org.kde.connect.PackageEmitters.BasePackageEmitter;
import org.kde.connect.PackageEmitters.CallPackageEmitter;
import org.kde.connect.PackageEmitters.PingPackageEmitter;
import org.kde.connect.PackageReceivers.BasePackageReceiver;
import org.kde.connect.PackageReceivers.PingPackageReceiver;
import org.kde.connect.Types.NetworkPackage;

import java.util.ArrayList;

public class BackgroundService extends Service {

    SharedPreferences settings;
    ArrayList<Announcer> announcers = new ArrayList<Announcer>();
    ArrayList<ComputerLink> computerLinks = new ArrayList<ComputerLink>();

    ArrayList<BasePackageEmitter> emitters = new ArrayList<BasePackageEmitter>();
    ArrayList<BasePackageReceiver> receivers = new ArrayList<BasePackageReceiver>();

    PingPackageEmitter pingEmitter;

    private void addComputerLink(ComputerLink cl) {

        computerLinks.add(cl);


        NetworkPackage p = new NetworkPackage(System.currentTimeMillis());
        p.setType(NetworkPackage.Type.PING);
        cl.sendPackage(p);

    }

    private void registerEmitters() {
        if (settings.getBoolean("emit_call", true)) {
            emitters.add(new CallPackageEmitter(getApplicationContext()));
        }

        pingEmitter = new PingPackageEmitter(getApplicationContext());
        if (settings.getBoolean("emit_ping", true)) {
            emitters.add(pingEmitter);
        }
    }

    private void registerReceivers() {
        if (settings.getBoolean("receive_ping", true)) {
            receivers.add(new PingPackageReceiver(getApplicationContext()));
        }
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

    public void sendPing() {
        pingEmitter.sendPing();
    }

    @Override
    public void onCreate() {
        Log.e("BackgroundService","Creating");

        registerEmitters();
        registerReceivers();

        settings = getSharedPreferences("KdeConnect", 0);

        announcers.add(new AvahiAnnouncer(this));

        super.onCreate();
    }

    @Override
    public void onDestroy() {
        Log.e("BackgroundService","Destroying");
        super.onDestroy();
    }


    public class LocalBinder extends Binder {
        public BackgroundService getInstance() {
            return BackgroundService.this;
        }
    }
    IBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }


}
