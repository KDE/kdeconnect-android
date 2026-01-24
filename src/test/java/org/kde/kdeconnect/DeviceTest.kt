/*
 * SPDX-FileCopyrightText: 2015 Vineet Garg <grg.vineet@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect

import android.app.NotificationManager
import android.content.Context
import android.preference.PreferenceManager
import androidx.core.content.ContextCompat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.kde.kdeconnect.backends.lan.LanLink
import org.kde.kdeconnect.backends.lan.LanLinkProvider
import org.kde.kdeconnect.DeviceInfo.Companion.fromIdentityPacketAndCert
import org.kde.kdeconnect.DeviceInfo.Companion.isValidDeviceId
import org.kde.kdeconnect.DeviceInfo.Companion.isValidIdentityPacket
import org.kde.kdeconnect.DeviceInfo.Companion.loadFromSettings
import org.kde.kdeconnect.DeviceType.Companion.fromString
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.helpers.security.RsaHelper
import org.kde.kdeconnect.helpers.security.SslHelper
import org.kde.kdeconnect.helpers.TrustedDevices
import org.kde.kdeconnect.PairingHandler.PairingCallback
import java.security.cert.CertificateException

class DeviceTest {
    private val context: Context = mockk()

    // Creating a paired device before each test case
    @Before
    fun setUp() {
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

        // implement android.util.Base64 using java.util.Base64
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any<ByteArray>(), any()) } answers {
            java.util.Base64.getMimeEncoder().encodeToString(firstArg())
        }
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getMimeDecoder().decode(firstArg<String>())
        }

        // Store device information needed to create a Device object in a future
        val deviceSettings = MockSharedPreference()
        val editor = deviceSettings.edit()
        editor.putString("deviceName", name)
        editor.putString("deviceType", DeviceType.PHONE.toString())
        editor.putString("certificate", encodedCertificate)
        editor.apply()
        every { context.getSharedPreferences(deviceId, Context.MODE_PRIVATE) } returns deviceSettings

        // Store the device as trusted
        val trustedSettings = MockSharedPreference()
        trustedSettings.edit().putBoolean(deviceId, true).apply()
        every { context.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE) } returns trustedSettings

        // Store an untrusted device
        val untrustedSettings = MockSharedPreference()
        every { context.getSharedPreferences("unpairedTestDevice", Context.MODE_PRIVATE) } returns untrustedSettings

        mockkStatic(PreferenceManager::class)
        val defaultSettings = MockSharedPreference()
        every { PreferenceManager.getDefaultSharedPreferences(any()) } returns defaultSettings

        RsaHelper.initialiseRsaKeys(context)

        mockkStatic(ContextCompat::class)
        every { ContextCompat.getSystemService(context, NotificationManager::class.java) } returns mockk(relaxed = true)

        mockkStatic(android.util.Log::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    @Throws(CertificateException::class)
    fun testDeviceInfoToIdentityPacket() {
        val deviceId = "testDevice"
        val deviceInfo = loadFromSettings(context, deviceId)
        deviceInfo.protocolVersion = DeviceHelper.PROTOCOL_VERSION
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
        Assert.assertTrue(isValidDeviceId("27456E3C_fE5C_4208_96A7_c0CAEEC5E5A0"))
        Assert.assertTrue(isValidDeviceId("27456e3c_fe5c_4208_96a7_c0caeec5e5a0"))
        Assert.assertTrue(isValidDeviceId("27456e3cfe5c420896a7c0caeec5e5a0"))
        Assert.assertFalse(isValidDeviceId("7456e3cfe5c420896a7c0caeec5e5a0"))
        Assert.assertTrue(isValidDeviceId("_27456e3c_fe5c_4208_96a7_c0caeec5e5a0_"))
        Assert.assertTrue(isValidDeviceId("z7456e3c_fe5c_4208_96a7_c0caeec5e5a0"))
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
        fakeNetworkPacket["protocolVersion"] = DeviceHelper.PROTOCOL_VERSION
        fakeNetworkPacket["deviceType"] = DeviceType.PHONE.toString()
        val certificateString = """
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
        val certificateBytes = android.util.Base64.decode(certificateString, 0)
        val certificate = SslHelper.parseCertificate(certificateBytes)
        val deviceInfo = fromIdentityPacketAndCert(fakeNetworkPacket, certificate)

        val linkProvider = mockk<LanLinkProvider>()
        every { linkProvider.name } returns "LanLinkProvider"
        val link = mockk<LanLink>()
        every { link.linkProvider } returns linkProvider
        every { link.deviceId } returns deviceId
        every { link.deviceInfo } returns deviceInfo
        every { link.addPacketReceiver(any()) } returns Unit
        val device = Device(context, link)

        Assert.assertNotNull(device)
        Assert.assertEquals(device.deviceId, deviceId)
        Assert.assertEquals(device.name, "Unpaired Test Device")
        Assert.assertEquals(device.deviceType, DeviceType.PHONE)
        Assert.assertNotNull(device.deviceInfo.certificate)

        device.pairingHandler.pairingDone()

        Assert.assertTrue(device.isPaired)

        Assert.assertTrue(TrustedDevices.isTrustedDevice(context, device.deviceId))

        val settings = TrustedDevices.getDeviceSettings(context, device.deviceId)
        Assert.assertEquals(
            settings.getString("deviceName", "Unknown device"),
            "Unpaired Test Device"
        )
        Assert.assertEquals(settings.getString("deviceType", "tablet"), "phone")

        TrustedDevices.removeTrustedDevice(context, device.deviceId)
    }

    @Test
    @Throws(CertificateException::class)
    fun testUnpair() {
        val pairingCallback = mockk<PairingCallback>(relaxed = true)
        val device = Device(context, "testDevice")
        device.addPairingCallback(pairingCallback)

        device.unpair()

        Assert.assertFalse(device.isPaired)

        Assert.assertFalse(TrustedDevices.isTrustedDevice(context, device.deviceId))

        verify(exactly = 1) { pairingCallback.unpaired(device) }
    }
}
