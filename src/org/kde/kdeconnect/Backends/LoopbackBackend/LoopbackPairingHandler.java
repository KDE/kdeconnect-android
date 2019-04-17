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

package org.kde.kdeconnect.Backends.LoopbackBackend;

import android.util.Log;

import org.kde.kdeconnect.Backends.BasePairingHandler;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.NetworkPacket;

public class LoopbackPairingHandler extends BasePairingHandler {

    public LoopbackPairingHandler(Device device, PairingHandlerCallback callback) {
        super(device, callback);
    }

    @Override
    public void packageReceived(NetworkPacket np) {

    }

    @Override
    public void requestPairing() {
        Log.i("LoopbackPairing", "requestPairing");
        mCallback.pairingDone();
    }

    @Override
    public void acceptPairing() {
        Log.i("LoopbackPairing", "acceptPairing");
        mCallback.pairingDone();
    }

    @Override
    public void rejectPairing() {
        Log.i("LoopbackPairing", "rejectPairing");
        mCallback.unpaired();
    }

    @Override
    public void unpair() {
        Log.i("LoopbackPairing", "unpair");
        mCallback.unpaired();
    }

}
