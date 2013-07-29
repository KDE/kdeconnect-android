package org.kde.connect;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import org.kde.connect.ComputerLinks.BaseComputerLink;
import org.kde.connect.LinkProviders.AvahiTcpLinkProvider;
import org.kde.connect.LinkProviders.BaseLinkProvider;
import org.kde.connect.PackageInterfaces.BasePackageInterface;
import org.kde.connect.PackageInterfaces.BatteryMonitorPackageInterface;
import org.kde.connect.PackageInterfaces.CallPackageInterface;
import org.kde.connect.PackageInterfaces.ClipboardPackageInterface;
import org.kde.connect.PackageInterfaces.MprisControlPackageInterface;
import org.kde.connect.PackageInterfaces.PingPackageInterface;

import java.util.ArrayList;
import java.util.HashMap;

public class BackgroundService extends Service {

    SharedPreferences settings;

    ArrayList<BaseLinkProvider> linkProviders = new ArrayList<BaseLinkProvider>();

    ArrayList<BasePackageInterface> packageInterfaces = new ArrayList<BasePackageInterface>();

    HashMap<String, Device> devices = new HashMap<String, Device>();

    private void registerPackageInterfaces() {
        if (settings.getBoolean("call_interface", true)) {
            packageInterfaces.add(new CallPackageInterface(getApplicationContext()));
        }

        if (settings.getBoolean("ping_interface", true)) {
            packageInterfaces.add(new PingPackageInterface(getApplicationContext()));
        }

        if (settings.getBoolean("clipboard_interface", true)) {
            packageInterfaces.add(new ClipboardPackageInterface(getApplicationContext()));
        }

        if (settings.getBoolean("battery_interface", true)) {
            packageInterfaces.add(new BatteryMonitorPackageInterface(getApplicationContext()));
        }

        if (settings.getBoolean("mpris_interface", true)) {
            packageInterfaces.add(new MprisControlPackageInterface(getApplicationContext()));
        }


    }

    public BasePackageInterface getPackageInterface(Class c) {
        for (BasePackageInterface pi : packageInterfaces) {
            if (c.isInstance(pi)) return pi;
        }
        return null;
    }

    public void registerLinkProviders() {
        if (settings.getBoolean("avahitcp_link", true)) {
            linkProviders.add(new AvahiTcpLinkProvider(this));
        }
    }

    public ArrayList<String> getVisibleDevices() {
        ArrayList<String> list = new ArrayList<String>();
        for(Device d : devices.values()) {
            list.add(d.getName());
        }
        return list;
    }

    //This will be called for each intent launch, even if the service is already started and is reused
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("BackgroundService","onStartCommand");
        for (InstanceCallback c : callbacks) {
            c.onServiceStart(this);
        }
        callbacks.clear();
        return Service.START_STICKY;
    }

    private void startDiscovery() {
        Log.i("StartDiscovery","Registering connection receivers");
        for (BaseLinkProvider a : linkProviders) {
            a.reachComputers(new BaseLinkProvider.ConnectionReceiver() {
                @Override
                public void onConnectionAccepted(String deviceId, String name, BaseComputerLink link) {
                    Log.i("BackgroundService", "Connection accepted!");

                    if (devices.containsKey(deviceId)) {
                        Log.i("BackgroundService", "known device");
                        devices.get(deviceId).addLink(link);
                    } else {
                        Log.i("BackgroundService", "unknown device");
                        Device device = new Device(deviceId, name, link);
                        devices.put(deviceId, device);
                        for (BasePackageInterface pe : packageInterfaces) {
                            pe.addDevice(device);
                            device.addPackageReceiver(pe);
                        }
                    }

                }

                @Override
                public void onConnectionLost(BaseComputerLink link) {
                    Device d = devices.get(link.getDeviceId());
                    if (d != null) {
                        d.removeLink(link);
                        //if (d.countLinkedDevices() == 0) devices.remove(link.getDeviceId);
                    }

                }
            });
        }
    }

    //This will called only once, even if we launch the service intent several times
    @Override
    public void onCreate() {
        super.onCreate();

        Log.i("BackgroundService","Service not started yet, initializing...");

        settings = getSharedPreferences("KdeConnect", 0);

        registerPackageInterfaces();
        registerLinkProviders();
        startDiscovery();

    }

    public void restart() {
        devices.clear();
        startDiscovery();
    }

    @Override
    public void onDestroy() {
        Log.i("BackgroundService", "Destroying");
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

    public static void Start(Context c) {
        RunCommand(c, null);
    }

    public static void RunCommand(Context c, final InstanceCallback callback) {
        if (callback != null) callbacks.add(callback);
        Intent serviceIntent = new Intent(c, BackgroundService.class);
        c.startService(serviceIntent);
    }


}
