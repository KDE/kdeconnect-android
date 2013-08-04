package org.kde.connect;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import org.kde.connect.ComputerLinks.BaseComputerLink;
import org.kde.connect.LinkProviders.AvahiTcpLinkProvider;
import org.kde.connect.LinkProviders.BaseLinkProvider;
import org.kde.connect.PackageInterfaces.BasePackageInterface;
import org.kde.connect.PackageInterfaces.BatteryMonitorPackageInterface;
import org.kde.connect.PackageInterfaces.CallNotificationPackageInterface;
import org.kde.connect.PackageInterfaces.ClipboardPackageInterface;
import org.kde.connect.PackageInterfaces.MprisControlPackageInterface;
import org.kde.connect.PackageInterfaces.PingPackageInterface;
import org.kde.connect.PackageInterfaces.SmsNotificationPackageInterface;

import java.util.ArrayList;
import java.util.HashMap;

public class BackgroundService extends Service {

    ArrayList<BaseLinkProvider> linkProviders = new ArrayList<BaseLinkProvider>();

    ArrayList<BasePackageInterface> packageInterfaces = new ArrayList<BasePackageInterface>();

    HashMap<String, Device> devices = new HashMap<String, Device>();

    public void registerLinkProviders() {

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

        if (settings.getBoolean("avahitcp_link", true)) {
            linkProviders.add(new AvahiTcpLinkProvider(this));
        }
    }

    public void registerPackageInterfacesFromSettings() {

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

        Log.e("registerPackageInterfacesFromSettings","registerPackageInterfacesFromSettings");

        if (settings.getBoolean("call_interface", true)) {
            addPackageInterface(CallNotificationPackageInterface.class);
        } else {
            removePackageInterface(CallNotificationPackageInterface.class);
        }

        if (settings.getBoolean("sms_interface", true)) {
            addPackageInterface(SmsNotificationPackageInterface.class);
        } else {
            removePackageInterface(SmsNotificationPackageInterface.class);
        }

        if (settings.getBoolean("battery_interface", true)) {
            addPackageInterface(BatteryMonitorPackageInterface.class);
        } else {
            removePackageInterface(BatteryMonitorPackageInterface.class);
        }

        if (settings.getBoolean("clipboard_interface", true)) {
            addPackageInterface(ClipboardPackageInterface.class);
        } else {
            removePackageInterface(ClipboardPackageInterface.class);
        }

        if (settings.getBoolean("mpris_interface", true)) {
            addPackageInterface(MprisControlPackageInterface.class);
        } else {
            removePackageInterface(MprisControlPackageInterface.class);
        }

        if (settings.getBoolean("ping_interface", true)) {
            addPackageInterface(PingPackageInterface.class);
        } else {
            removePackageInterface(PingPackageInterface.class);
        }

    }

    public BasePackageInterface getPackageInterface(Class c) {
        for (BasePackageInterface pi : packageInterfaces) {
            if (c.isInstance(pi)) return pi;
        }
        return null;
    }

    public BasePackageInterface addPackageInterface(Class c) {
        BasePackageInterface pi = getPackageInterface(c);
        if (pi != null) {
            Log.e("addPackageInterface","package interface already existent");
            return pi;
        }
        try {
            pi = (BasePackageInterface)c.newInstance();
        } catch(Exception e) {
            e.printStackTrace();
            Log.e("addPackageInterface","Error instantiating packageinterface");
            return null;
        }
        packageInterfaces.add(pi);
        pi.onCreate(getApplicationContext());
        for (Device dev : devices.values()) {
            dev.addPackageReceiver(pi);
            pi.addDevice(dev);
        }
        return pi;
    }

    public boolean removePackageInterface(Class c) {
        for (BasePackageInterface pi : packageInterfaces) {
            if (c.isInstance(pi)) {
                packageInterfaces.remove(pi);
                for (Device dev : devices.values()) {
                    dev.removePackageReceiver(pi);
                    pi.removeDevice(dev);
                }
                pi.onDestroy();
                return true;
            }
        }
        Log.e("removePackageInterface","Unexistent preference");
        return false;
    }

    public Device getDevice(String id) {
        return devices.get(id);
    }

    private BaseLinkProvider.ConnectionReceiver deviceListener = new BaseLinkProvider.ConnectionReceiver() {
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
                    device.addPackageReceiver(pe);
                    pe.addDevice(device);
                }
            }

        }

        @Override
        public void onConnectionLost(BaseComputerLink link) {
            Log.e("BackgroundService","onConnectionLost");
            Device d = devices.get(link.getDeviceId());
            if (d != null) {
                d.removeLink(link);
                if (d.countLinkedDevices() == 0) devices.remove(link.getDeviceId());
            }

        }
    };

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

    public void startDiscovery() {
        Log.i("BackgroundService","StartDiscovery");
        for (BaseLinkProvider a : linkProviders) {
            a.onStart();
        }
    }

    public void stopDiscovery() {
        Log.i("BackgroundService","StopDiscovery");
        for (BaseLinkProvider a : linkProviders) {
            a.onStop();
        }
    }

    public void addConnectionListener(BaseLinkProvider.ConnectionReceiver cr) {
        Log.i("BackgroundService","Registering connection listener");
        for (BaseLinkProvider a : linkProviders) {
            a.addConnectionReceiver(cr);
        }
    }

    //This will called only once, even if we launch the service intent several times
    @Override
    public void onCreate() {
        super.onCreate();

        Log.i("BackgroundService","Service not started yet, initializing...");

        registerPackageInterfacesFromSettings();
        registerLinkProviders();

        //Link Providers need to be already registered
        addConnectionListener(deviceListener);
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
