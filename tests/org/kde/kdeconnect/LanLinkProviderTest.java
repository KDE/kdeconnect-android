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

import android.util.Log;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kde.kdeconnect.Backends.LanBackend.LanLink;
import org.kde.kdeconnect.Backends.LanBackend.LanLinkProvider;
import org.kde.kdeconnect.Helpers.DeviceHelper;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.HashMap;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;

@RunWith(PowerMockRunner.class)
@PrepareForTest({DeviceHelper.class, Log.class})
public class LanLinkProviderTest {

    @Before
    public void setUp() {
        PowerMockito.mockStatic(DeviceHelper.class);
        PowerMockito.when(DeviceHelper.getDeviceId(any())).thenReturn("123");

        PowerMockito.mockStatic(Log.class);
    }

    @Test
    public void testIdentityPacketReceived() throws Exception {

        LanLinkProvider linkProvider = new LanLinkProvider(null);

        NetworkPacket networkPacket = Mockito.mock(NetworkPacket.class);
        Mockito.when(networkPacket.getType()).thenReturn("kdeconnect.identity");
        Mockito.when(networkPacket.getString("deviceId")).thenReturn("testDevice");
        Mockito.when(networkPacket.getString("deviceName")).thenReturn("Test Device");
        Mockito.when(networkPacket.getInt("protocolVersion")).thenReturn(5);
        Mockito.when(networkPacket.getString("deviceType")).thenReturn("phone");

        String serialized = "{\"type\":\"kdeconnect.identity\",\"id\":12345,\"body\":{\"deviceName\":\"Test Device\",\"deviceType\":\"phone\",\"deviceId\":\"testDevice\",\"protocolVersion\":5}}";
        Mockito.when(networkPacket.serialize()).thenReturn(serialized);

        Socket channel = Mockito.mock(Socket.class);
        Method method = LanLinkProvider.class.getDeclaredMethod("identityPacketReceived", NetworkPacket.class, Socket.class, LanLink.ConnectionStarted.class);
        method.setAccessible(true);
        method.invoke(linkProvider, networkPacket, channel, LanLink.ConnectionStarted.Locally);

        HashMap<String, LanLink> visibleComputers;
        Field field = LanLinkProvider.class.getDeclaredField("visibleComputers");
        field.setAccessible(true);
        visibleComputers = (HashMap<String, LanLink>) field.get(linkProvider);
        assertNotNull(visibleComputers.get("testDevice"));

    }
}
