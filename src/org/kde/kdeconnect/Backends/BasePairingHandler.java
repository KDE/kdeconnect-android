/*
 * Copyright 2015 Vineet Garg <grg.vineet@gmail.com>
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

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.NetworkPackage;

public interface BasePairingHandler {

    NetworkPackage createPairPackage(Device device);
    void packageReceived(Device device, NetworkPackage np) throws Exception;
    void requestPairing(Device device, Device.SendPackageStatusCallback callback);
    void acceptPairing(Device device, Device.SendPackageStatusCallback callback);
    void rejectPairing(Device device);
    void pairingDone(Device device);
    void unpair(Device device);

}
