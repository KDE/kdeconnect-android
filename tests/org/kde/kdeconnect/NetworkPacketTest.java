/*
 * SPDX-FileCopyrightText: 2015 Vineet Garg <grg.vineet@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kde.kdeconnect.Helpers.DeviceHelper;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;

@RunWith(PowerMockRunner.class)
@PrepareForTest({DeviceHelper.class, Log.class})
public class NetworkPacketTest {

    @Before
    public void setUp() {
        PowerMockito.mockStatic(DeviceHelper.class);
        PowerMockito.when(DeviceHelper.getDeviceId(any())).thenReturn("123");
        PowerMockito.when(DeviceHelper.getDeviceType(any())).thenReturn(Device.DeviceType.Phone);

        PowerMockito.mockStatic(Log.class);
    }

    @Test
    public void testNetworkPacket() throws JSONException {
        NetworkPacket np = new NetworkPacket("com.test");

        np.set("hello", "hola");
        assertEquals(np.getString("hello", "bye"), "hola");

        np.set("hello", "");
        assertEquals(np.getString("hello", "bye"), "");

        assertEquals(np.getString("hi", "bye"), "bye");

        np.set("foo", "bar");
        String serialized = np.serialize();
        NetworkPacket np2 = NetworkPacket.unserialize(serialized);

        assertEquals(np.getLong("id"), np2.getLong("id"));
        assertEquals(np.getString("type"), np2.getString("type"));
        assertEquals(np.getJSONArray("body"), np2.getJSONArray("body"));

        String json = "{\"id\":123,\"type\":\"test\",\"body\":{\"testing\":true}}";
        np2 = NetworkPacket.unserialize(json);
        assertEquals(np2.getId(), 123);
        assertTrue(np2.getBoolean("testing"));
        assertFalse(np2.getBoolean("not_testing"));
        assertTrue(np2.getBoolean("not_testing", true));

    }

    @Test
    public void testIdentity() {

        Context context = Mockito.mock(Context.class);
        MockSharedPreference settings = new MockSharedPreference();
        Mockito.when(context.getSharedPreferences(anyString(), anyInt())).thenReturn(settings);

        NetworkPacket np = NetworkPacket.createIdentityPacket(context);

        assertEquals(np.getInt("protocolVersion"), DeviceHelper.ProtocolVersion);

    }

}
