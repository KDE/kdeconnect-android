/*
 * SPDX-FileCopyrightText: 2015 Vineet Garg <grg.vineet@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kde.kdeconnect.Backends.BasePairingHandler;
import org.kde.kdeconnect.Backends.LanBackend.LanLink;
import org.kde.kdeconnect.Backends.LanBackend.LanLinkProvider;
import org.kde.kdeconnect.Backends.LanBackend.LanPairingHandler;
import org.kde.kdeconnect.Helpers.DeviceHelper;
import org.kde.kdeconnect.Helpers.SecurityHelpers.RsaHelper;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.lang.reflect.Method;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Matchers.eq;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Base64.class, Log.class, PreferenceManager.class, ContextCompat.class})
public class DeviceTest {

    private Context context;

    // Creating a paired device before each test case
    @Before
    public void setUp() {
        // Save new test device in settings

        String deviceId = "testDevice";
        String name = "Test Device";

        KeyPair keyPair;
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            keyPair = keyGen.genKeyPair();
        } catch (Exception e) {
            Log.e("KDE/initializeRsaKeys", "Exception", e);
            return;
        }

        this.context = Mockito.mock(Context.class);

        PowerMockito.mockStatic(Base64.class);
        PowerMockito.when(Base64.encodeToString(any(), anyInt())).thenAnswer(invocation -> java.util.Base64.getMimeEncoder().encodeToString((byte[]) invocation.getArguments()[0]));
        PowerMockito.when(Base64.decode(anyString(), anyInt())).thenAnswer(invocation -> java.util.Base64.getMimeDecoder().decode((String) invocation.getArguments()[0]));

        PowerMockito.mockStatic(Log.class);

        //Store device information needed to create a Device object in a future
        MockSharedPreference deviceSettings = new MockSharedPreference();
        SharedPreferences.Editor editor = deviceSettings.edit();
        editor.putString("deviceName", name);
        editor.putString("deviceType", Device.DeviceType.Phone.toString());
        editor.putString("publicKey", Base64.encodeToString(keyPair.getPublic().getEncoded(), 0).trim() + "\n");
        editor.apply();
        Mockito.when(context.getSharedPreferences(eq(deviceId), eq(Context.MODE_PRIVATE))).thenReturn(deviceSettings);

        //Store the device as trusted
        MockSharedPreference trustedSettings = new MockSharedPreference();
        trustedSettings.edit().putBoolean(deviceId, true).apply();
        Mockito.when(context.getSharedPreferences(eq("trusted_devices"), eq(Context.MODE_PRIVATE))).thenReturn(trustedSettings);

        //Store an untrusted device
        MockSharedPreference untrustedSettings = new MockSharedPreference();
        Mockito.when(context.getSharedPreferences(eq("unpairedTestDevice"), eq(Context.MODE_PRIVATE))).thenReturn(untrustedSettings);

        //Default shared prefs, including our own private key
        PowerMockito.mockStatic(PreferenceManager.class);
        MockSharedPreference defaultSettings = new MockSharedPreference();
        PowerMockito.when(PreferenceManager.getDefaultSharedPreferences(any())).thenReturn(defaultSettings);
        RsaHelper.initialiseRsaKeys(context);

        PowerMockito.mockStatic(ContextCompat.class);
        PowerMockito.when(ContextCompat.getSystemService(context, NotificationManager.class)).thenReturn(Mockito.mock(NotificationManager.class));
    }

    @Test
    public void testDeviceType() {
        assertEquals(Device.DeviceType.Phone, Device.DeviceType.FromString(Device.DeviceType.Phone.toString()));
        assertEquals(Device.DeviceType.Tablet, Device.DeviceType.FromString(Device.DeviceType.Tablet.toString()));
        assertEquals(Device.DeviceType.Computer, Device.DeviceType.FromString(Device.DeviceType.Computer.toString()));
        assertEquals(Device.DeviceType.Tv, Device.DeviceType.FromString(Device.DeviceType.Tv.toString()));
        assertEquals(Device.DeviceType.Computer, Device.DeviceType.FromString(""));
        assertEquals(Device.DeviceType.Computer, Device.DeviceType.FromString(null));
    }

    // Basic paired device testing
    @Test
    public void testDevice() {
        Device device = new Device(context, "testDevice");

        assertEquals(device.getDeviceId(), "testDevice");
        assertEquals(device.getDeviceType(), Device.DeviceType.Phone);
        assertEquals(device.getName(), "Test Device");
        assertTrue(device.isPaired());
    }

    // Testing pairing done
    // Created an unpaired device inside this test
    @Test
    public void testPairingDone() {
        NetworkPacket fakeNetworkPacket = new NetworkPacket(NetworkPacket.PACKET_TYPE_IDENTITY);
        fakeNetworkPacket.set("deviceId", "unpairedTestDevice");
        fakeNetworkPacket.set("deviceName", "Unpaired Test Device");
        fakeNetworkPacket.set("protocolVersion", DeviceHelper.ProtocolVersion);
        fakeNetworkPacket.set("deviceType", Device.DeviceType.Phone.toString());

        LanLinkProvider linkProvider = Mockito.mock(LanLinkProvider.class);
        Mockito.when(linkProvider.getName()).thenReturn("LanLinkProvider");
        LanLink link = Mockito.mock(LanLink.class);
        Mockito.when(link.getLinkProvider()).thenReturn(linkProvider);
        Mockito.when(link.getPairingHandler(any(Device.class), any(BasePairingHandler.PairingHandlerCallback.class))).thenReturn(Mockito.mock(LanPairingHandler.class));
        Device device = new Device(context, fakeNetworkPacket, link);

        Device.PairingCallback pairingCallback = Mockito.mock(Device.PairingCallback.class);
        device.addPairingCallback(pairingCallback);


        ArgumentCaptor<BasePairingHandler.PairingHandlerCallback> pairingHandlerCallback = ArgumentCaptor.forClass(BasePairingHandler.PairingHandlerCallback.class);
        Mockito.verify(link, Mockito.times(1)).getPairingHandler(eq(device), pairingHandlerCallback.capture());

        assertNotNull(device);
        assertEquals(device.getDeviceId(), "unpairedTestDevice");
        assertEquals(device.getName(), "Unpaired Test Device");
        assertEquals(device.getDeviceType(), Device.DeviceType.Phone);
        assertNull(device.certificate);

        pairingHandlerCallback.getValue().pairingDone();

        assertTrue(device.isPaired());
        Mockito.verify(pairingCallback, Mockito.times(1)).pairingSuccessful();

        SharedPreferences preferences = context.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE);
        assertTrue(preferences.getBoolean(device.getDeviceId(), false));

        SharedPreferences settings = context.getSharedPreferences(device.getDeviceId(), Context.MODE_PRIVATE);
        assertEquals(settings.getString("deviceName", "Unknown device"), "Unpaired Test Device");
        assertEquals(settings.getString("deviceType", "tablet"), "phone");

        // Cleanup for unpaired test device
        preferences.edit().remove(device.getDeviceId()).apply();
        settings.edit().clear().apply();
    }

    @Test
    public void testPairingDoneWithCertificate() {

        NetworkPacket fakeNetworkPacket = new NetworkPacket(NetworkPacket.PACKET_TYPE_IDENTITY);
        fakeNetworkPacket.set("deviceId", "unpairedTestDevice");
        fakeNetworkPacket.set("deviceName", "Unpaired Test Device");
        fakeNetworkPacket.set("protocolVersion", DeviceHelper.ProtocolVersion);
        fakeNetworkPacket.set("deviceType", Device.DeviceType.Phone.toString());
        fakeNetworkPacket.set("certificate",
            "MIIDVzCCAj+gAwIBAgIBCjANBgkqhkiG9w0BAQUFADBVMS8wLQYDVQQDDCZfZGExNzlhOTFfZjA2\n" +
            "NF80NzhlX2JlOGNfMTkzNWQ3NTQ0ZDU0XzEMMAoGA1UECgwDS0RFMRQwEgYDVQQLDAtLZGUgY29u\n" +
            "bmVjdDAeFw0xNTA2MDMxMzE0MzhaFw0yNTA2MDMxMzE0MzhaMFUxLzAtBgNVBAMMJl9kYTE3OWE5\n" +
            "MV9mMDY0XzQ3OGVfYmU4Y18xOTM1ZDc1NDRkNTRfMQwwCgYDVQQKDANLREUxFDASBgNVBAsMC0tk\n" +
            "ZSBjb25uZWN0MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzH9GxS1lctpwYdSGAoPH\n" +
            "ws+MnVaL0PVDCuzrpxzXc+bChR87xofhQIesLPLZEcmUJ1MlEJ6jx4W+gVhvY2tUN7SoiKKbnq8s\n" +
            "WjI5ovs5yML3C1zPbOSJAdK613FcdkK+UGd/9dQk54gIozinC58iyTAChVVpB3pAF38EPxwKkuo2\n" +
            "qTzwk24d6PRxz1skkzwEphUQQzGboyHsAlJHN1MzM2/yFGB4l8iUua2d3ETyfy/xFEh/SwtGtXE5\n" +
            "KLz4cpb0fxjeYQZVruBKxzE07kgDO3zOhmP3LJ/KSPHWYImd1DWmpY9iDvoXr6+V7FAnRloaEIyg\n" +
            "7WwdlSCpo3TXVuIjLwIDAQABozIwMDAdBgNVHQ4EFgQUwmbHo8YbiR463GRKSLL3eIKyvDkwDwYD\n" +
            "VR0TAQH/BAUwAwIBADANBgkqhkiG9w0BAQUFAAOCAQEAydijH3rbnvpBDB/30w2PCGMT7O0N/XYM\n" +
            "wBtUidqa4NFumJrNrccx5Ehp4UP66BfP61HW8h2U/EekYfOsZyyWd4KnsDD6ycR8h/WvpK3BC2cn\n" +
            "I299wbqCEZmk5ZFFaEIDHdLAdgMCuxJkAzy9mMrWEa05Soxi2/ZXdrU9nXo5dzuPGYlirVPDHl7r\n" +
            "/urBxD6HVX3ObQJRJ7r/nAWyUVdX3/biJaDRsydftOpGU6Gi5c1JK4MWIz8Bsjh6mEjCsVatbPPl\n" +
            "yygGiJbDZfAvN2XoaVEBii2GDDCWfaFwPVPYlNTvjkUkMP8YThlMsiJ8Q4693XoLOL94GpNlCfUg\n" +
            "7n+KOQ==");

        LanLinkProvider linkProvider = Mockito.mock(LanLinkProvider.class);
        Mockito.when(linkProvider.getName()).thenReturn("LanLinkProvider");
        LanLink link = Mockito.mock(LanLink.class);
        Mockito.when(link.getPairingHandler(any(Device.class), any(BasePairingHandler.PairingHandlerCallback.class))).thenReturn(Mockito.mock(LanPairingHandler.class));
        Mockito.when(link.getLinkProvider()).thenReturn(linkProvider);
        Device device = new Device(context, fakeNetworkPacket, link);

        assertNotNull(device);
        assertEquals(device.getDeviceId(), "unpairedTestDevice");
        assertEquals(device.getName(), "Unpaired Test Device");
        assertEquals(device.getDeviceType(), Device.DeviceType.Phone);
        assertNotNull(device.certificate);

        Method method;
        try {
            method = Device.class.getDeclaredMethod("pairingDone");
            method.setAccessible(true);
            method.invoke(device);
        } catch (Exception e) {
            Log.e("KDEConnect", "Exception", e);
        }

        assertTrue(device.isPaired());

        SharedPreferences preferences = context.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE);
        assertTrue(preferences.getBoolean(device.getDeviceId(), false));

        SharedPreferences settings = context.getSharedPreferences(device.getDeviceId(), Context.MODE_PRIVATE);
        assertEquals(settings.getString("deviceName", "Unknown device"), "Unpaired Test Device");
        assertEquals(settings.getString("deviceType", "tablet"), "phone");

        // Cleanup for unpaired test device
        preferences.edit().remove(device.getDeviceId()).apply();
        settings.edit().clear().apply();
    }

    @Test
    public void testUnpair() {
        Device.PairingCallback pairingCallback = Mockito.mock(Device.PairingCallback.class);
        Device device = new Device(context, "testDevice");
        device.addPairingCallback(pairingCallback);

        device.unpair();

        assertFalse(device.isPaired());

        SharedPreferences preferences = context.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE);
        assertFalse(preferences.getBoolean(device.getDeviceId(), false));

        Mockito.verify(pairingCallback, Mockito.times(1)).unpaired();
    }
}
