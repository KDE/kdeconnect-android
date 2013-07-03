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
import org.kde.connect.LinkProviders.BaseLinkProvider;
import org.kde.connect.LinkProviders.AvahiLinkProvider;
import org.kde.connect.PackageEmitters.BasePackageEmitter;
import org.kde.connect.PackageEmitters.CallPackageEmitter;
import org.kde.connect.PackageEmitters.PingPackageEmitter;
import org.kde.connect.PackageReceivers.BasePackageReceiver;
import org.kde.connect.PackageReceivers.PingPackageReceiver;
import org.kde.connect.Types.NetworkPackage;

import java.util.ArrayList;

public class BackgroundService extends Service {

    SharedPreferences settings;

    ArrayList<BaseLinkProvider> locators = new ArrayList<BaseLinkProvider>();
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

        //NetworkPackage p = new NetworkPackage(System.currentTimeMillis());
        //p.setType(NetworkPackage.Type.PING);
        //cl.sendPackage(p);

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
            locators.add(new AvahiLinkProvider(this));
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e("BackgroundService","Starting");
        instance=this;
        attendCallbacks();
        return Service.START_STICKY;
    }

    public void reachComputers() {
        for (BaseLinkProvider a : locators) {
            a.reachComputers(new BaseLinkProvider.ConnectionReceiver() {
                @Override
                public void onConnectionAccepted(BaseComputerLink link) {
                    Log.e("BackgroundService", "Connection accepted!");
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
        super.onCreate();

        Log.e("BackgroundService","Creating");

        settings = getSharedPreferences("KdeConnect", 0);

        pingEmitter = new PingPackageEmitter(getApplicationContext());

        registerEmitters();
        registerReceivers();
        registerAnnouncers();

        instance = this;
        attendCallbacks();
    }

    @Override
    public void onDestroy() {
        Log.e("BackgroundService","Destroying");
        super.onDestroy();
        instance = null;
    }






    //All kind of black magic to make the service a singleton

    public interface InstanceCallback {
        void onServiceStart(BackgroundService service);
    }

    private static BackgroundService instance = null;
    private static ArrayList<InstanceCallback> callbacks = new ArrayList<InstanceCallback>();

    private static void attendCallbacks() {
        for (InstanceCallback c : callbacks) {
            c.onServiceStart(instance);
        }
        callbacks.clear();
    }

    public static void Start(Context c) {
        RunCommand(c, null);
    }

    public static void RunCommand(Context c, final InstanceCallback callback) {

        if (callback != null) callbacks.add(callback);

        if (instance == null) {
            Intent serviceIntent = new Intent(c, BackgroundService.class);
            c.startService(serviceIntent);
            try {
                c.bindService(serviceIntent, new ServiceConnection() {
                    public void onServiceDisconnected(ComponentName name) {
                        instance = null;
                    }
                    public void onServiceConnected(ComponentName name, IBinder binder) {
                        instance = ((LocalBinder) binder).getInstance();
                        attendCallbacks();
                    }
                }, Service.BIND_AUTO_CREATE);
            } catch(Exception e) {

            }
        } else {
            attendCallbacks();
        }
    }

    private class LocalBinder extends Binder {
        public BackgroundService getInstance() {
            return BackgroundService.this;
        }
    }

    private IBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
