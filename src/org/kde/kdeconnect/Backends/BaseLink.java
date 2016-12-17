/*
 * Copyright 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License or (at your option) version 3 or any later version
 * accepted by the membership of KDE e.V. (or its successor approved
 * by the membership of KDE e.V.), which shall act as a proxy
 * defined in Section 14 of version 3 of the license.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
*/

package org.kde.kdeconnect.Backends;

import android.content.Context;

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.NetworkPackage;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;


public abstract class BaseLink {

    protected final Context context;

    public interface PackageReceiver {
        void onPackageReceived(NetworkPackage np);
    }

    private final BaseLinkProvider linkProvider;
    private final String deviceId;
    private final ArrayList<PackageReceiver> receivers = new ArrayList<>();
    protected PrivateKey privateKey;

    protected BaseLink(Context context, String deviceId, BaseLinkProvider linkProvider) {
        this.context = context;        
        this.linkProvider = linkProvider;
        this.deviceId = deviceId;
    }

    /* To be implemented by each link for pairing handlers */
    public abstract String getName();
    public abstract BasePairingHandler getPairingHandler(Device device, BasePairingHandler.PairingHandlerCallback callback);

    public String getDeviceId() {
        return deviceId;
    }

    public void setPrivateKey(PrivateKey key) {
        privateKey = key;
    }

    public BaseLinkProvider getLinkProvider() {
        return linkProvider;
    }

    //The daemon will periodically destroy unpaired links if this returns false
    public boolean linkShouldBeKeptAlive() {
        return false;
    }

    public void addPackageReceiver(PackageReceiver pr) {
        receivers.add(pr);
    }
    public void removePackageReceiver(PackageReceiver pr) {
        receivers.remove(pr);
    }

    //Should be called from a background thread listening to packages
    protected void packageReceived(NetworkPackage np) {
        for(PackageReceiver pr : receivers) {
            pr.onPackageReceived(np);
        }
    }

    public void disconnect() {
        linkProvider.connectionLost(this);
    }

    //TO OVERRIDE, should be sync
    public abstract void sendPackage(NetworkPackage np,Device.SendPackageStatusCallback callback);
    @Deprecated
    public abstract void sendPackageEncrypted(NetworkPackage np,Device.SendPackageStatusCallback callback, PublicKey key);
}
