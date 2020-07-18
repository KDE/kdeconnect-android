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

import android.util.Log;

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.NetworkPacket;

import java.net.InetAddress;


public class DeviceOffer {

    public String id;
    public String name;
    public Device.DeviceType type;
    public int protocolVersion;

    public InetAddress host;
    public int port;

    public static DeviceOffer FromLegacyIdentityPacket(NetworkPacket identityPacket) {
        DeviceOffer ret = new DeviceOffer();
        ret.id = identityPacket.getString("deviceId");
        ret.protocolVersion = identityPacket.getInt("protocolVersion");
        ret.name = identityPacket.getString("deviceName");
        ret.type = Device.DeviceType.FromString(identityPacket.getString("deviceType", "desktop"));
        return ret;
    }

    public BaseLinkProvider provider = null;
    public void connect() {
        if (provider == null) {
            Log.e("AAA", "ERROR: Can't connect, provider unknown");
            return;
        }
        provider.connect(this);
    }

}
