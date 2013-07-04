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

import java.util.ArrayList;

public class BackgroundService extends Service {

    SharedPreferences settings;

    ArrayList<BaseLinkProvider> locators = new ArrayList<BaseLinkProvider>();
    ArrayList<BaseComputerLink> computerLinks = new ArrayList<BaseComputerLink>();

    ArrayList<BasePackageEmitter> emitters = new ArrayList<BasePackageEmitter>();
    ArrayList<BasePackageReceiver> receivers = new ArrayList<BasePackageReceiver>();

    PingPackageEmitter pingEmitter;

    private void clearComputerLinks() {
        Log.i("BackgroundService","clearComputerLinks");
        for(BasePackageEmitter pe : emitters) pe.clearComputerLinks();
        computerLinks.clear();
    }

    private void removeComputerLink(BaseComputerLink cl) {
        Log.i("BackgroundService","removeComputerLink");
        for(BasePackageEmitter pe : emitters) pe.removeComputerLink(cl);
        computerLinks.remove(cl);
    }

    private void addComputerLink(BaseComputerLink cl) {
        Log.i("BackgroundService","addComputerLink");
        computerLinks.add(cl);
        for(BasePackageEmitter pe : emitters) pe.addComputerLink(cl);
        for(BasePackageReceiver pr : receivers) cl.addPackageReceiver(pr);
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

    //This will be called for each intent launch, even if the service is already started and is reused
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e("BackgroundService","Running callbacks waiting service to be ready");
        for (InstanceCallback c : callbacks) {
            c.onServiceStart(this);
        }
        callbacks.clear();
        return Service.START_STICKY;
    }

    public void reachComputers() {
        clearComputerLinks();
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

    //This will called only once, even if we launch the service intent several times
    @Override
    public void onCreate() {
        super.onCreate();

        Log.e("BackgroundService","Service not started yet, initializing...");

        settings = getSharedPreferences("KdeConnect", 0);

        pingEmitter = new PingPackageEmitter(getApplicationContext());

        registerEmitters();
        registerReceivers();
        registerAnnouncers();

    }

    @Override
    public void onDestroy() {
        Log.e("BackgroundService", "Destroying");
        super.onDestroy();
    }

    @Override
    public IBinder onBind (Intent intent) {
        return new Binder();
    }




    //To use the service from the gui

    public interface InstanceCallback {
        void onServiceStart(BackgroundService service);
    }

    private static ArrayList<InstanceCallback> callbacks = new ArrayList<InstanceCallback>();
/*
    public static void Start(Context c) {
        RunCommand(c, null);
    }
*/
    public static void RunCommand(Context c, final InstanceCallback callback) {
        if (callback != null) callbacks.add(callback);
        Intent serviceIntent = new Intent(c, BackgroundService.class);
        c.startService(serviceIntent);
    }


}
