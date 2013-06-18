package org.kde.connect;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import org.kde.connect.ComputerLinks.BaseComputerLink;
import org.kde.connect.Extensions.SingletonService;
import org.kde.connect.Locators.AvahiLocator;
import org.kde.connect.Locators.BaseLocator;
import org.kde.connect.PackageEmitters.BasePackageEmitter;
import org.kde.connect.PackageEmitters.CallPackageEmitter;
import org.kde.connect.PackageEmitters.PingPackageEmitter;
import org.kde.connect.PackageReceivers.BasePackageReceiver;
import org.kde.connect.PackageReceivers.PingPackageReceiver;
import org.kde.connect.Types.NetworkPackage;

import java.util.ArrayList;

public class BackgroundService extends SingletonService {

    SharedPreferences settings;

    ArrayList<BaseLocator> locators = new ArrayList<BaseLocator>();
    ArrayList<BaseComputerLink> computerLinks = new ArrayList<BaseComputerLink>();

    ArrayList<BasePackageEmitter> emitters = new ArrayList<BasePackageEmitter>();
    ArrayList<BasePackageReceiver> receivers = new ArrayList<BasePackageReceiver>();

    PingPackageEmitter pingEmitter;



    private void addComputerLink(BaseComputerLink cl) {

        Log.i("BackgroundService","addComputerLink");

        computerLinks.add(cl);


        for(BasePackageEmitter pe : emitters) pe.addComputerLink(cl);
        for(BasePackageReceiver pr : receivers) cl.addPackageReceiver(pr);

        Log.i("BackgroundService","sending ping after connection");

        NetworkPackage p = new NetworkPackage(System.currentTimeMillis());
        p.setType(NetworkPackage.Type.PING);
        cl.sendPackage(p);

    }

    private void registerEmitters() {
        if (settings.getBoolean("emit_call", true)) {
            emitters.add(new CallPackageEmitter(getApplicationContext()));
        }

        if (settings.getBoolean("emit_ping", true)) {
            emitters.add(pingEmitter);
        }
    }

    private void registerReceivers() {
        if (settings.getBoolean("receive_ping", true)) {
            receivers.add(new PingPackageReceiver(getApplicationContext()));
        }
    }

    public void registerAnnouncers() {
        if (settings.getBoolean("announce_avahi", true)) {
            locators.add(new AvahiLocator(this));
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e("BackgroundService","Starting");

        return Service.START_STICKY;
    }

    public void reachComputers() {
        for (BaseLocator a : locators) {
            a.reachComputers(new BaseLocator.ConnectionReceiver() {
                @Override
                public void onConnectionAccepted(BaseComputerLink link) {
                    Log.e("BackgroundService","Connection accepted!");
                    //TODO: Check if there are other links available, and keep the best one
                    addComputerLink(link);
                }
            });
        }
    }

    public void sendPing() {
        pingEmitter.sendPing();
    }

    @Override
    public void onCreate() {
        Log.e("BackgroundService","Creating");

        settings = getSharedPreferences("KdeConnect", 0);

        pingEmitter = new PingPackageEmitter(getApplicationContext());

        registerEmitters();
        registerReceivers();
        registerAnnouncers();

        super.onCreate();
    }

    @Override
    public void onDestroy() {
        Log.e("BackgroundService","Destroying");
        super.onDestroy();
    }









    //Singleton service auxiliars


    private class LocalBinder extends Binder {
        public BackgroundService getInstance() {
            return BackgroundService.this;
        }
    }

    public interface ServiceStartCallback {
        void onServiceStart(BackgroundService service);
    }

    private IBinder mBinder = new LocalBinder();
    private static BackgroundService instance = null;

    public static BackgroundService GetInstance() {
        return instance;
    }

    public static void Start(Context c) {
        Start(c,null);
    }

    public static void Start(Context c, ServiceStartCallback callback) {

        if (instance != null) {
            Log.e("SingletonService","Already started");
        }

        Intent serviceIntent = new Intent(c, BackgroundService.class);
        c.startService(serviceIntent);
        c.bindService(serviceIntent, new ServiceConnection() {

            public void onServiceDisconnected(ComponentName name) {
                instance = null;
            }

            public void onServiceConnected(ComponentName name, IBinder binder) {
                instance = ((LocalBinder) binder).getInstance();
                if (callback != null) callback.onServiceStart(instance);
            }

        }, Service.BIND_AUTO_CREATE);

    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

}
