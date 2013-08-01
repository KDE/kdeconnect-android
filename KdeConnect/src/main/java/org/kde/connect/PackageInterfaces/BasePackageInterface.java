package org.kde.connect.PackageInterfaces;

import android.content.BroadcastReceiver;
import android.content.Context;

import org.kde.connect.Device;
import org.kde.connect.NetworkPackage;

import java.util.ArrayList;

public abstract class BasePackageInterface {

    private ArrayList<Device> mDevices = new ArrayList<Device>();

    public void addDevice(Device d) {
        mDevices.add(d);
        onDeviceConnected(d);
    }

    public void removeDevice(Device d) {
        mDevices.remove(d);
    }

    public void clearComputerLinks() {
        mDevices.clear();
    }

    public int countLinkedDevices() {
        return mDevices.size();
    }


    protected boolean sendPackage(NetworkPackage np) {
        if (mDevices.isEmpty()) return false;
        for(Device d : mDevices) {
            d.sendPackage(np);
        }
        return true;
    }

    //To override
    public abstract boolean onCreate(Context context);
    public abstract void onDestroy();
    public abstract boolean onPackageReceived(Device d, NetworkPackage np);
    public abstract boolean onDeviceConnected(Device d);
}
