package org.kde.connect;

import android.os.AsyncTask;
import android.util.Log;

import org.kde.connect.ComputerLinks.BaseComputerLink;
import org.kde.connect.PackageInterfaces.BasePackageInterface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class Device {

    private ArrayList<BaseComputerLink> links = new ArrayList<BaseComputerLink>();
    private ArrayList<BasePackageInterface> receivers = new ArrayList<BasePackageInterface>();

    private String deviceId;
    private String name;


    Device(String deviceId, String name, BaseComputerLink dl) {
        this.deviceId = deviceId;
        this.name = name;
        addLink(dl);
    }

    public String getName() {
        return name;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void addLink(BaseComputerLink link) {
        Log.e("Device","addLink "+link.getLinkProvider().getName()+" -> "+getName());
        links.add(link);
        Collections.sort(links, new Comparator<BaseComputerLink>() {
            @Override
            public int compare(BaseComputerLink o, BaseComputerLink o2) {
                return o2.getLinkProvider().getPriority() - o.getLinkProvider().getPriority();
            }
        });

        link.addPackageReceiver(new BaseComputerLink.PackageReceiver() {
            @Override
            public void onPackageReceived(NetworkPackage np) {
                for (BasePackageInterface receiver : receivers) {
                    receiver.onPackageReceived(Device.this, np);
                }
            }
        });
    }



    public void removeLink(BaseComputerLink link) {
        links.remove(link);
    }



    //Send and receive interfaces

    public void addPackageReceiver(BasePackageInterface receiver) {
        receivers.add(receiver);
    }

    public boolean sendPackage(final NetworkPackage np) {
        Log.e("Device", "sendPackage");

        new AsyncTask<Void,Void,Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                Log.e("sendPackage","Do in background");
                for(BaseComputerLink link : links) {
                    Log.e("sendPackage","Trying "+link.getLinkProvider().getName());
                    if (link.sendPackage(np)) {
                        Log.e("sent using", link.getLinkProvider().getName());
                        return null;
                    }
                }
                Log.e("sendPackage","Error: Package could not be sent");
                return null;
            }
        }.execute();

        return true; //FIXME
    }

}
