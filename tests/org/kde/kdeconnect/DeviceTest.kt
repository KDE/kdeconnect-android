/*
 * SPDX-FileCopyrightText: 2015 Vineet Garg <grg.vineet@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect

import android.app.NotificationManager
import android.content.Context
import android.preference.PreferenceManager
import android.util.Base64
import androidx.core.content.ContextCompat
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.kde.kdeconnect.Backends.LanBackend.LanLink
import org.kde.kdeconnect.Backends.LanBackend.LanLinkProvider
import org.kde.kdeconnect.DeviceInfo.Companion.fromIdentityPacketAndCert
import org.kde.kdeconnect.DeviceInfo.Companion.isValidDeviceId
import org.kde.kdeconnect.DeviceInfo.Companion.isValidIdentityPacket
import org.kde.kdeconnect.DeviceInfo.Companion.loadFromSettings
import org.kde.kdeconnect.DeviceType.Companion.fromString
import org.kde.kdeconnect.Helpers.DeviceHelper
import org.kde.kdeconnect.Helpers.SecurityHelpers.RsaHelper
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper
import org.kde.kdeconnect.PairingHandler.PairingCallback
import org.mockito.ArgumentMatchers
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import java.security.cert.CertificateException

class DeviceTest {
    private lateinit var context: Context
    private lateinit var mockBase64: MockedStatic<Base64>
    private lateinit var preferenceManager: MockedStatic<PreferenceManager>
    private lateinit var contextCompat: MockedStatic<ContextCompat>

    // Creating a paired device before each test case
    @Before
    fun setUp() {
        // Save new test device in settings

        val deviceId = "testDevice"
        val name = "Test Device"
        val encodedCertificate = """
            MIIDVzCCAj+gAwIBAgIBCjANBgkqhkiG9w0BAQUFADBVMS8wLQYDVQQDDCZfZGExNzlhOTFfZjA2
            NF80NzhlX2JlOGNfMTkzNWQ3NTQ0ZDU0XzEMMAoGA1UECgwDS0RFMRQwEgYDVQQLDAtLZGUgY29u
            bmVjdDAeFw0xNTA2MDMxMzE0MzhaFw0yNTA2MDMxMzE0MzhaMFUxLzAtBgNVBAMMJl9kYTE3OWE5
            MV9mMDY0XzQ3OGVfYmU4Y18xOTM1ZDc1NDRkNTRfMQwwCgYDVQQKDANLREUxFDASBgNVBAsMC0tk
            ZSBjb25uZWN0MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzH9GxS1lctpwYdSGAoPH
            ws+MnVaL0PVDCuzrpxzXc+bChR87xofhQIesLPLZEcmUJ1MlEJ6jx4W+gVhvY2tUN7SoiKKbnq8s
            WjI5ovs5yML3C1zPbOSJAdK613FcdkK+UGd/9dQk54gIozinC58iyTAChVVpB3pAF38EPxwKkuo2
            qTzwk24d6PRxz1skkzwEphUQQzGboyHsAlJHN1MzM2/yFGB4l8iUua2d3ETyfy/xFEh/SwtGtXE5
            KLz4cpb0fxjeYQZVruBKxzE07kgDO3zOhmP3LJ/KSPHWYImd1DWmpY9iDvoXr6+V7FAnRloaEIyg
            7WwdlSCpo3TXVuIjLwIDAQABozIwMDAdBgNVHQ4EFgQUwmbHo8YbiR463GRKSLL3eIKyvDkwDwYD
            VR0TAQH/BAUwAwIBADANBgkqhkiG9w0BAQUFAAOCAQEAydijH3rbnvpBDB/30w2PCGMT7O0N/XYM
            wBtUidqa4NFumJrNrccx5Ehp4UP66BfP61HW8h2U/EekYfOsZyyWd4KnsDD6ycR8h/WvpK3BC2cn
            I299wbqCEZmk5ZFFaEIDHdLAdgMCuxJkAzy9mMrWEa05Soxi2/ZXdrU9nXo5dzuPGYlirVPDHl7r
            /urBxD6HVX3ObQJRJ7r/nAWyUVdX3/biJaDRsydftOpGU6Gi5c1JK4MWIz8Bsjh6mEjCsVatbPPl
            yygGiJbDZfAvN2XoaVEBii2GDDCWfaFwPVPYlNTvjkUkMP8YThlMsiJ8Q4693XoLOL94GpNlCfUg
            7n+KOQ==
            """.trimIndent()

        val context = Mockito.mock(Context::class.java)
        val mockBase64 = Mockito.mockStatic(Base64::class.java)

        mockBase64.`when`<Any> {
            Base64.encodeToString(ArgumentMatchers.any(ByteArray::class.java), ArgumentMatchers.anyInt())
        }.thenAnswer { invocation: InvocationOnMock ->
            java.util.Base64.getMimeEncoder().encodeToString(invocation.arguments[0] as ByteArray)
        }

        mockBase64.`when`<Any> {
            Base64.decode(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt())
        }.thenAnswer { invocation: InvocationOnMock ->
            java.util.Base64.getMimeDecoder().decode(invocation.arguments[0] as String)
        }

        // Store device information needed to create a Device object in a future
        val deviceSettings = MockSharedPreference()
        val editor = deviceSettings.edit()
        editor.putString("deviceName", name)
        editor.putString("deviceType", DeviceType.PHONE.toString())
        editor.putString("certificate", encodedCertificate)
        editor.apply()
        Mockito.`when`(context.getSharedPreferences(ArgumentMatchers.eq(deviceId), ArgumentMatchers.eq(Context.MODE_PRIVATE))).thenReturn(deviceSettings)

        // Store the device as trusted
        val trustedSettings = MockSharedPreference()
        trustedSettings.edit().putBoolean(deviceId, true).apply()
        Mockito.`when`(context.getSharedPreferences(ArgumentMatchers.eq("trusted_devices"), ArgumentMatchers.eq(Context.MODE_PRIVATE))).thenReturn(trustedSettings)

        // Store an untrusted device
        val untrustedSettings = MockSharedPreference()
        Mockito.`when`(context.getSharedPreferences(ArgumentMatchers.eq("unpairedTestDevice"), ArgumentMatchers.eq(Context.MODE_PRIVATE))).thenReturn(untrustedSettings)

        // Default shared prefs, including our own private key
        val preferenceManager = Mockito.mockStatic(PreferenceManager::class.java)
        val defaultSettings = MockSharedPreference()
        preferenceManager.`when`<Any> {
            PreferenceManager.getDefaultSharedPreferences(ArgumentMatchers.any(Context::class.java))
        }.thenReturn(defaultSettings)

        RsaHelper.initialiseRsaKeys(context)

        val contextCompat = Mockito.mockStatic(ContextCompat::class.java)
        contextCompat.`when`<Any> {
            ContextCompat.getSystemService(context!!, NotificationManager::class.java)
        }.thenReturn(Mockito.mock(NotificationManager::class.java))

        this.context = context
        this.mockBase64 = mockBase64
        this.preferenceManager = preferenceManager
        this.contextCompat = contextCompat
    }

    @After
    fun tearDown() {
        mockBase64.close()
        preferenceManager.close()
        contextCompat.close()
    }

    @Test
    @Throws(CertificateException::class)
    fun testDeviceInfoToIdentityPacket() {
        val deviceId = "testDevice"
        val settings = context.getSharedPreferences(deviceId, Context.MODE_PRIVATE)
        val deviceInfo = loadFromSettings(context, deviceId, settings)
        deviceInfo.protocolVersion = DeviceHelper.ProtocolVersion
        deviceInfo.incomingCapabilities = hashSetOf("kdeconnect.plugin1State", "kdeconnect.plugin2State")
        deviceInfo.outgoingCapabilities = hashSetOf("kdeconnect.plugin1State.request", "kdeconnect.plugin2State.request")

        val networkPacket = deviceInfo.toIdentityPacket()
        Assert.assertEquals(deviceInfo.id, networkPacket.getString("deviceId"))
        Assert.assertEquals(deviceInfo.name, networkPacket.getString("deviceName"))
        Assert.assertEquals(deviceInfo.protocolVersion.toLong(), networkPacket.getInt("protocolVersion").toLong())
        Assert.assertEquals(deviceInfo.type.toString(), networkPacket.getString("deviceType"))
        Assert.assertEquals(deviceInfo.incomingCapabilities, networkPacket.getStringSet("incomingCapabilities"))
        Assert.assertEquals(deviceInfo.outgoingCapabilities, networkPacket.getStringSet("outgoingCapabilities"))
    }

    @Test
    fun testIsValidDeviceId() {
        Assert.assertTrue(isValidDeviceId("27456e3c_fe5c_4208_96a7_c0caeec5e5a0"))
        Assert.assertFalse(isValidDeviceId("7456e3c_fe5c_4208_96a7_c0caeec5e5a0"))
        Assert.assertFalse(isValidDeviceId("127456e3cfe5c420896a7c0caeec5e5a0"))
        Assert.assertTrue(isValidDeviceId("27456e3cfe5c420896a7c0caeec5e5a0"))
        Assert.assertFalse(isValidDeviceId("7456e3cfe5c420896a7c0caeec5e5a0"))
        Assert.assertTrue(isValidDeviceId("_27456e3c_fe5c_4208_96a7_c0caeec5e5a0_"))
        Assert.assertFalse(isValidDeviceId("_7456e3c_fe5c_4208_96a7_c0caeec5e5a0_"))
        Assert.assertFalse(isValidDeviceId("_7456e3c_fe5c_4208_96a7_c0caeec_e5a0_"))
        Assert.assertFalse(isValidDeviceId("_7456z3c_fe5c_4208_96a7_c0caeec_e5a0_"))
        Assert.assertFalse(isValidDeviceId(""))
        Assert.assertFalse(isValidDeviceId("______"))
        Assert.assertFalse(isValidDeviceId("____"))
        Assert.assertFalse(isValidDeviceId("potato"))
        Assert.assertFalse(isValidDeviceId("12345"))
    }

    @Test
    fun testIsValidIdentityPacket() {
        val np = NetworkPacket(NetworkPacket.PACKET_TYPE_IDENTITY)
        Assert.assertFalse(isValidIdentityPacket(np))

        val validName = "MyDevice"
        val validId = "27456e3c_fe5c_4208_96a7_c0caeec5e5a0"
        np["deviceName"] = validName
        np["deviceId"] = validId
        Assert.assertTrue(isValidIdentityPacket(np))

        np["deviceName"] = "    "
        Assert.assertFalse(isValidIdentityPacket(np))
        np["deviceName"] = "<><><><><><><><><>" // Only invalid characters
        Assert.assertFalse(isValidIdentityPacket(np))

        np["deviceName"] = validName
        np["deviceId"] = "    "
        Assert.assertFalse(isValidIdentityPacket(np))
    }

    @Test
    fun testDeviceType() {
        Assert.assertEquals(DeviceType.PHONE, fromString(DeviceType.PHONE.toString()))
        Assert.assertEquals(DeviceType.TABLET, fromString(DeviceType.TABLET.toString()))
        Assert.assertEquals(DeviceType.DESKTOP, fromString(DeviceType.DESKTOP.toString()))
        Assert.assertEquals(DeviceType.LAPTOP, fromString(DeviceType.LAPTOP.toString()))
        Assert.assertEquals(DeviceType.TV, fromString(DeviceType.TV.toString()))
        Assert.assertEquals(DeviceType.DESKTOP, fromString("invalid"))
    }

    // Basic paired device testing
    @Test
    @Throws(CertificateException::class)
    fun testDevice() {
        val device = Device(context, "testDevice")

        Assert.assertEquals(device.deviceId, "testDevice")
        Assert.assertEquals(device.deviceType, DeviceType.PHONE)
        Assert.assertEquals(device.name, "Test Device")
        Assert.assertTrue(device.isPaired)
        Assert.assertNotNull(device.deviceInfo.certificate)
    }

    @Test
    @Throws(CertificateException::class)
    fun testPairingDone() {
        val fakeNetworkPacket = NetworkPacket(NetworkPacket.PACKET_TYPE_IDENTITY)
        val deviceId = "unpairedTestDevice"
        fakeNetworkPacket["deviceId"] = deviceId
        fakeNetworkPacket["deviceName"] = "Unpaired Test Device"
        fakeNetworkPacket["protocolVersion"] = DeviceHelper.ProtocolVersion
        fakeNetworkPacket["deviceType"] = DeviceType.PHONE.toString()
        val certificateString =
            """
            MIIDVzCCAj+gAwIBAgIBCjANBgkqhkiG9w0BAQUFADBVMS8wLQYDVQQDDCZfZGExNzlhOTFfZjA2
            NF80NzhlX2JlOGNfMTkzNWQ3NTQ0ZDU0XzEMMAoGA1UECgwDS0RFMRQwEgYDVQQLDAtLZGUgY29u
            bmVjdDAeFw0xNTA2MDMxMzE0MzhaFw0yNTA2MDMxMzE0MzhaMFUxLzAtBgNVBAMMJl9kYTE3OWE5
            MV9mMDY0XzQ3OGVfYmU4Y18xOTM1ZDc1NDRkNTRfMQwwCgYDVQQKDANLREUxFDASBgNVBAsMC0tk
            ZSBjb25uZWN0MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzH9GxS1lctpwYdSGAoPH
            ws+MnVaL0PVDCuzrpxzXc+bChR87xofhQIesLPLZEcmUJ1MlEJ6jx4W+gVhvY2tUN7SoiKKbnq8s
            WjI5ovs5yML3C1zPbOSJAdK613FcdkK+UGd/9dQk54gIozinC58iyTAChVVpB3pAF38EPxwKkuo2
            qTzwk24d6PRxz1skkzwEphUQQzGboyHsAlJHN1MzM2/yFGB4l8iUua2d3ETyfy/xFEh/SwtGtXE5
            KLz4cpb0fxjeYQZVruBKxzE07kgDO3zOhmP3LJ/KSPHWYImd1DWmpY9iDvoXr6+V7FAnRloaEIyg
            7WwdlSCpo3TXVuIjLwIDAQABozIwMDAdBgNVHQ4EFgQUwmbHo8YbiR463GRKSLL3eIKyvDkwDwYD
            VR0TAQH/BAUwAwIBADANBgkqhkiG9w0BAQUFAAOCAQEAydijH3rbnvpBDB/30w2PCGMT7O0N/XYM
            wBtUidqa4NFumJrNrccx5Ehp4UP66BfP61HW8h2U/EekYfOsZyyWd4KnsDD6ycR8h/WvpK3BC2cn
            I299wbqCEZmk5ZFFaEIDHdLAdgMCuxJkAzy9mMrWEa05Soxi2/ZXdrU9nXo5dzuPGYlirVPDHl7r
            /urBxD6HVX3ObQJRJ7r/nAWyUVdX3/biJaDRsydftOpGU6Gi5c1JK4MWIz8Bsjh6mEjCsVatbPPl
            yygGiJbDZfAvN2XoaVEBii2GDDCWfaFwPVPYlNTvjkUkMP8YThlMsiJ8Q4693XoLOL94GpNlCfUg
            7n+KOQ==
            """.trimIndent()
        val certificateBytes = Base64.decode(certificateString, 0)
        val certificate = SslHelper.parseCertificate(certificateBytes)
        val deviceInfo = fromIdentityPacketAndCert(fakeNetworkPacket, certificate)

        val linkProvider = Mockito.mock(LanLinkProvider::class.java)
        Mockito.`when`(linkProvider.name).thenReturn("LanLinkProvider")
        val link = Mockito.mock(LanLink::class.java)
        Mockito.`when`(link.linkProvider).thenReturn(linkProvider)
        Mockito.`when`(link.deviceId).thenReturn(deviceId)
        Mockito.`when`(link.deviceInfo).thenReturn(deviceInfo)
        val device = Device(context, link)

        Assert.assertNotNull(device)
        Assert.assertEquals(device.deviceId, deviceId)
        Assert.assertEquals(device.name, "Unpaired Test Device")
        Assert.assertEquals(device.deviceType, DeviceType.PHONE)
        Assert.assertNotNull(device.deviceInfo.certificate)

        device.pairingHandler.pairingDone()

        Assert.assertTrue(device.isPaired)

        val preferences = context.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE)
        Assert.assertTrue(preferences.getBoolean(device.deviceId, false))

        val settings = context.getSharedPreferences(device.deviceId, Context.MODE_PRIVATE)
        Assert.assertEquals(
            settings.getString("deviceName", "Unknown device"),
            "Unpaired Test Device"
        )
        Assert.assertEquals(settings.getString("deviceType", "tablet"), "phone")

        // Cleanup for unpaired test device
        preferences.edit().remove(device.deviceId).apply()
        settings.edit().clear().apply()
    }

    @Test
    @Throws(CertificateException::class)
    fun testUnpair() {
        val pairingCallback = Mockito.mock(PairingCallback::class.java)
        val device = Device(context, "testDevice")
        device.addPairingCallback(pairingCallback)

        device.unpair()

        Assert.assertFalse(device.isPaired)

        val preferences = context.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE)
        Assert.assertFalse(preferences.getBoolean(device.deviceId, false))

        Mockito.verify(pairingCallback, Mockito.times(1)).unpaired()
    }
}
