package org.kde.connect.PackageEmitters;

import org.kde.connect.ComputerLinks.BaseComputerLink;
import org.kde.connect.NetworkPackage;

import java.util.ArrayList;

public class BasePackageEmitter {

    private ArrayList<BaseComputerLink> mBaseComputerLinks = new ArrayList<BaseComputerLink>();

    public void addComputerLink(BaseComputerLink cl) {
        mBaseComputerLinks.add(cl);
    }
    public void removeComputerLink(BaseComputerLink cl) {
        mBaseComputerLinks.remove(cl);
    }
    public void clearComputerLinks() {
        mBaseComputerLinks.clear();
    }

    protected int countLinkedComputers() {
        return mBaseComputerLinks.size();
    }

    protected void sendPackage(NetworkPackage np) {
        for(BaseComputerLink cl : mBaseComputerLinks) {
            cl.sendPackage(np);
        }
    }
}
