/*
 * SPDX-FileCopyrightText: 2024 TPJ Schikhof <kde@schikhof.eu>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.helpers

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.kde.kdeconnect.helpers.security.SslHelper
import org.kde.kdeconnect.MockSharedPreference
import java.security.cert.X509Certificate
import java.util.Base64

class SSLHelperTest {
    private val context: Context = mockk()
    private lateinit var sharedPreferences: MockSharedPreference
    private val certificateBase64 = """
        MIIBkzCCATmgAwIBAgIBATAKBggqhkjOPQQDBDBTMS0wKwYDVQQDDCRlZTA2MWE3NV9lNDAzXzRl
        Y2NfOTI2MV81ZmZlMjcyMmY2OTgxFDASBgNVBAsMC0tERSBDb25uZWN0MQwwCgYDVQQKDANLREUw
        HhcNMjMwOTE1MjIwMDAwWhcNMzQwOTE1MjIwMDAwWjBTMS0wKwYDVQQDDCRlZTA2MWE3NV9lNDAz
        XzRlY2NfOTI2MV81ZmZlMjcyMmY2OTgxFDASBgNVBAsMC0tERSBDb25uZWN0MQwwCgYDVQQKDANL
        REUwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAASqOIKTm5j6x8DKgYSkItLmjCgIXP0gkOW6bmVv
        loDGsYnvqYLMFGe7YW8g8lT/qPBTEfDOM4UpQ8X6jidE+XrnMAoGCCqGSM49BAMEA0gAMEUCIEpk
        6VNpbt3tfbWDf0TmoJftRq3wAs3Dke7d5vMZlivyAiEA/ZXtSRqPjs/2RN9SynKhSUA9/z0PNq6L
        YoAaC6TdomM=
        """.trimIndent().replace("\n", "\r\n") // the mime encoder adds \r\n line endings
    private val certificateHash = "fc:1f:b3:d3:d3:3b:23:42:e4:5c:74:b1:a6:13:dc:df:e5:e1:f0:29:d6:68:24:9f:50:49:52:a9:a8:04:1e:31:"
    private val deviceId = "testDevice"
    private val certificateKey = "certificate"

    @Before
    fun setup() {
        sharedPreferences = MockSharedPreference()
        every { context.getSharedPreferences(deviceId, Context.MODE_PRIVATE) } returns sharedPreferences

        // implement android.util.Base64 using java.util.Base64
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any<ByteArray>(), any()) } answers {
            Base64.getMimeEncoder().encodeToString(firstArg())
        }
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            Base64.getMimeDecoder().decode(firstArg<String>())
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testNoCertificateStored() {
        val isStored = TrustedDevices.isCertificateStored(context, deviceId)
        Assert.assertFalse(isStored)
    }

    @Test
    fun testCertificateStored() {
        sharedPreferences.edit().putString(certificateKey, certificateBase64).apply()
        Assert.assertTrue(TrustedDevices.isCertificateStored(context, deviceId))
        sharedPreferences.edit().remove(certificateKey).apply()
        Assert.assertFalse(TrustedDevices.isCertificateStored(context, deviceId))
    }

    @Test
    fun getAnyCertificate() {
        Assert.assertThrows(Exception::class.java) { TrustedDevices.getDeviceCertificate(context, deviceId) }
        sharedPreferences.edit().putString(certificateKey, certificateBase64).apply()
        Assert.assertNotNull(TrustedDevices.getDeviceCertificate(context, deviceId))
    }

    @Test
    fun getExpectedCertificate() {
        sharedPreferences.edit().putString(certificateKey, certificateBase64).apply()
        val cert = TrustedDevices.getDeviceCertificate(context, deviceId)
        Assert.assertEquals(certificateBase64, Base64.getMimeEncoder().encodeToString(cert.encoded))
    }

    @Test
    fun getCertificateHash() {
        sharedPreferences.edit().putString(certificateKey, certificateBase64).apply()
        val cert = TrustedDevices.getDeviceCertificate(context, deviceId)
        val hash = SslHelper.getCertificateHash(cert)
        Assert.assertEquals(certificateHash, hash)
    }

    @Test
    fun parseCertificate() {
        val bytes = Base64.getMimeDecoder().decode(certificateBase64)
        val cert = SslHelper.parseCertificate(bytes)
        val hash = SslHelper.getCertificateHash(cert)
        Assert.assertEquals(certificateHash, hash)
    }

    @Test
    fun getCommonName() {
        sharedPreferences.edit().putString(certificateKey, certificateBase64).apply()
        val cert = TrustedDevices.getDeviceCertificate(context, deviceId)
        val commonName = SslHelper.getCommonNameFromCertificate(cert as X509Certificate)
        Assert.assertEquals("ee061a75_e403_4ecc_9261_5ffe2722f698", commonName)
    }

}
