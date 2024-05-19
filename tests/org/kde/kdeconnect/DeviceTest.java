/*
 * SPDX-FileCopyrightText: 2015 Vineet Garg <grg.vineet@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Base64;

import androidx.core.content.ContextCompat;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.kde.kdeconnect.Backends.LanBackend.LanLink;
import org.kde.kdeconnect.Backends.LanBackend.LanLinkProvider;
import org.kde.kdeconnect.Helpers.DeviceHelper;
import org.kde.kdeconnect.Helpers.SecurityHelpers.RsaHelper;
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.HashSet;

public class DeviceTest {

    private Context context;

    MockedStatic<Base64> mockBase64;
    MockedStatic<PreferenceManager> preferenceManager;
    MockedStatic<ContextCompat> contextCompat;

    @After
    public void tearDown() {
        mockBase64.close();
        preferenceManager.close();
        contextCompat.close();
    }

    // Creating a paired device before each test case
    @Before
    public void setUp() {
        // Save new test device in settings

        String deviceId = "testDevice";
        String name = "Test Device";
        String encodedCertificate = "MIIDVzCCAj+gAwIBAgIBCjANBgkqhkiG9w0BAQUFADBVMS8wLQYDVQQDDCZfZGExNzlhOTFfZjA2\n" +
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
            "7n+KOQ==";

        this.context = Mockito.mock(Context.class);

        mockBase64 = Mockito.mockStatic(Base64.class);
        mockBase64.when(() -> Base64.encodeToString(any(byte[].class), anyInt())).thenAnswer(invocation -> java.util.Base64.getMimeEncoder().encodeToString((byte[]) invocation.getArguments()[0]));
        mockBase64.when(() -> Base64.decode(anyString(), anyInt())).thenAnswer(invocation -> java.util.Base64.getMimeDecoder().decode((String) invocation.getArguments()[0]));

        //Store device information needed to create a Device object in a future
        MockSharedPreference deviceSettings = new MockSharedPreference();
        SharedPreferences.Editor editor = deviceSettings.edit();
        editor.putString("deviceName", name);
        editor.putString("deviceType", DeviceType.PHONE.toString());
        editor.putString("certificate", encodedCertificate);
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
        preferenceManager = Mockito.mockStatic(PreferenceManager.class);
        MockSharedPreference defaultSettings = new MockSharedPreference();
        preferenceManager.when(() -> PreferenceManager.getDefaultSharedPreferences(any(Context.class))).thenReturn(defaultSettings);

        RsaHelper.initialiseRsaKeys(context);

        contextCompat = Mockito.mockStatic(ContextCompat.class);
        contextCompat.when(() -> ContextCompat.getSystemService(context, NotificationManager.class)).thenReturn(Mockito.mock(NotificationManager.class));
    }

    @Test
    public void testDeviceInfoToIdentityPacket() throws CertificateException {
        String deviceId = "testDevice";
        SharedPreferences settings = context.getSharedPreferences(deviceId, Context.MODE_PRIVATE);
        DeviceInfo di = DeviceInfo.loadFromSettings(context, deviceId,  settings);
        di.protocolVersion = DeviceHelper.ProtocolVersion;
        di.incomingCapabilities = new HashSet<>(Arrays.asList("kdeconnect.plugin1State", "kdeconnect.plugin2State"));
        di.outgoingCapabilities = new HashSet<>(Arrays.asList("kdeconnect.plugin1State.request", "kdeconnect.plugin2State.request"));

        NetworkPacket np = di.toIdentityPacket();

        assertEquals(di.id, np.getString("deviceId"));
        assertEquals(di.name, np.getString("deviceName"));
        assertEquals(di.protocolVersion, np.getInt("protocolVersion"));
        assertEquals(di.type.toString(), np.getString("deviceType"));
        assertEquals(di.incomingCapabilities, np.getStringSet("incomingCapabilities"));
        assertEquals(di.outgoingCapabilities, np.getStringSet("outgoingCapabilities"));
    }

    @Test
    public void testIsValidIdentityPacket() {
        NetworkPacket np = new NetworkPacket(NetworkPacket.PACKET_TYPE_IDENTITY);
        assertFalse(DeviceInfo.Companion.isValidIdentityPacket(np));

        String validName = "MyDevice";
        String validId = "123";
        np.set("deviceName", validName);
        np.set("deviceId", validId);
        assertTrue(DeviceInfo.Companion.isValidIdentityPacket(np));

        np.set("deviceName", "    ");
        assertFalse(DeviceInfo.Companion.isValidIdentityPacket(np));
        np.set("deviceName", "<><><><><><><><><>"); // Only invalid characters
        assertFalse(DeviceInfo.Companion.isValidIdentityPacket(np));

        np.set("deviceName", validName);
        np.set("deviceId", "    ");
        assertFalse(DeviceInfo.Companion.isValidIdentityPacket(np));
    }

    @Test
    public void testDeviceType() {
        assertEquals(DeviceType.PHONE, DeviceType.fromString(DeviceType.PHONE.toString()));
        assertEquals(DeviceType.TABLET, DeviceType.fromString(DeviceType.TABLET.toString()));
        assertEquals(DeviceType.DESKTOP, DeviceType.fromString(DeviceType.DESKTOP.toString()));
        assertEquals(DeviceType.LAPTOP, DeviceType.fromString(DeviceType.LAPTOP.toString()));
        assertEquals(DeviceType.TV, DeviceType.fromString(DeviceType.TV.toString()));
        assertEquals(DeviceType.DESKTOP, DeviceType.fromString("invalid"));
    }

    // Basic paired device testing
    @Test
    public void testDevice() throws CertificateException {
        Device device = new Device(context, "testDevice");

        assertEquals(device.getDeviceId(), "testDevice");
        assertEquals(device.getDeviceType(), DeviceType.PHONE);
        assertEquals(device.getName(), "Test Device");
        assertTrue(device.isPaired());
        assertNotNull(device.getDeviceInfo().certificate);
    }

    @Test
    public void testPairingDone() throws CertificateException {

        NetworkPacket fakeNetworkPacket = new NetworkPacket(NetworkPacket.PACKET_TYPE_IDENTITY);
        String deviceId = "unpairedTestDevice";
        fakeNetworkPacket.set("deviceId", deviceId);
        fakeNetworkPacket.set("deviceName", "Unpaired Test Device");
        fakeNetworkPacket.set("protocolVersion", DeviceHelper.ProtocolVersion);
        fakeNetworkPacket.set("deviceType", DeviceType.PHONE.toString());
        String certificateString =
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
            "7n+KOQ==";
        byte[] certificateBytes = Base64.decode(certificateString, 0);
        Certificate certificate = SslHelper.parseCertificate(certificateBytes);
        DeviceInfo deviceInfo = DeviceInfo.fromIdentityPacketAndCert(fakeNetworkPacket, certificate);

        LanLinkProvider linkProvider = Mockito.mock(LanLinkProvider.class);
        Mockito.when(linkProvider.getName()).thenReturn("LanLinkProvider");
        LanLink link = Mockito.mock(LanLink.class);
        Mockito.when(link.getLinkProvider()).thenReturn(linkProvider);
        Mockito.when(link.getDeviceId()).thenReturn(deviceId);
        Mockito.when(link.getDeviceInfo()).thenReturn(deviceInfo);
        Device device = new Device(context, link);

        assertNotNull(device);
        assertEquals(device.getDeviceId(), deviceId);
        assertEquals(device.getName(), "Unpaired Test Device");
        assertEquals(device.getDeviceType(), DeviceType.PHONE);
        assertNotNull(device.getDeviceInfo().certificate);

        device.getPairingHandler().pairingDone();

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
    public void testUnpair() throws CertificateException {
        PairingHandler.PairingCallback pairingCallback = Mockito.mock(PairingHandler.PairingCallback.class);
        Device device = new Device(context, "testDevice");
        device.addPairingCallback(pairingCallback);

        device.unpair();

        assertFalse(device.isPaired());

        SharedPreferences preferences = context.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE);
        assertFalse(preferences.getBoolean(device.getDeviceId(), false));

        Mockito.verify(pairingCallback, Mockito.times(1)).unpaired();
    }
}
