package org.kde.connect.PackageInterfaces;

import org.kde.connect.Device;
import org.kde.connect.NetworkPackage;

import java.util.ArrayList;

public abstract class BasePackageInterface {

    private ArrayList<Device> mDevices = new ArrayList<Device>();

    public void addDevice(Device d) {
        mDevices.add(d);
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
    public abstract void onPackageReceived(Device d, NetworkPackage np);
}
