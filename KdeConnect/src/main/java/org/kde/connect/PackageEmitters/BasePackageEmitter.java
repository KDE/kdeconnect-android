package org.kde.connect.PackageEmitters;

import org.kde.connect.ComputerLink;
import org.kde.connect.Types.NetworkPackage;

import java.util.ArrayList;

public class BasePackageEmitter {

    private ArrayList<ComputerLink> computerLinks = new ArrayList<ComputerLink>();

    public void addComputerLink(ComputerLink cl) {
        computerLinks.add(cl);
    }

    protected int countLinkedComputers() {
        return computerLinks.size();
    }

    protected void sendPackage(NetworkPackage np) {
        for(ComputerLink cl : computerLinks) {
            cl.sendPackage(np);
        }
    }
}
