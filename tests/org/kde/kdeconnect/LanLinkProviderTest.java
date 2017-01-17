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

package org.kde.kdeconnect;

import android.test.AndroidTestCase;

import org.kde.kdeconnect.Backends.LanBackend.LanLink;
import org.kde.kdeconnect.Backends.LanBackend.LanLinkProvider;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.HashMap;

public class LanLinkProviderTest extends AndroidTestCase {

    private LanLinkProvider linkProvider;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().getPath());

        linkProvider = new LanLinkProvider(getContext());
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

    }

    public void testIdentityPackageReceived() throws Exception{

        NetworkPackage networkPackage = Mockito.mock(NetworkPackage.class);
        Mockito.when(networkPackage.getType()).thenReturn("kdeconnect.identity");
        Mockito.when(networkPackage.getString("deviceId")).thenReturn("testDevice");
        Mockito.when(networkPackage.getString("deviceName")).thenReturn("Test Device");
        Mockito.when(networkPackage.getInt("protocolVersion")).thenReturn(5);
        Mockito.when(networkPackage.getString("deviceType")).thenReturn("phone");

        String serialized = "{\"type\":\"kdeconnect.identity\",\"id\":12345,\"body\":{\"deviceName\":\"Test Device\",\"deviceType\":\"phone\",\"deviceId\":\"testDevice\",\"protocolVersion\":5}}";
        Mockito.when(networkPackage.serialize()).thenReturn(serialized);

        Socket channel = Mockito.mock(Socket.class);
        try {
            Method method = LanLinkProvider.class.getDeclaredMethod("identityPackageReceived", NetworkPackage.class, Socket.class, LanLink.ConnectionStarted.class);
            method.setAccessible(true);
            method.invoke(linkProvider, networkPackage, channel, LanLink.ConnectionStarted.Locally);
        }catch (Exception e){
            throw e;
        }

        HashMap<String, LanLink> visibleComputers;
        try {
            Field field = LanLinkProvider.class.getDeclaredField("visibleComputers");
            field.setAccessible(true);
            visibleComputers = (HashMap<String, LanLink>)field.get(linkProvider);
        }catch (Exception e){
            throw e;
        }
        assertNotNull(visibleComputers.get("testDevice"));

    }
}
