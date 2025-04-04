/*
 * SPDX-FileCopyrightText: 2024 TPJ Schikhof <kde@schikhof.eu>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.Helpers

import android.content.Context
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper
import org.kde.kdeconnect.MockSharedPreference
import org.mockito.ArgumentMatchers
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import java.security.cert.X509Certificate
import java.util.Base64

class SSLHelperTest {
    private lateinit var context: Context
    private lateinit var sharedPreferences: MockSharedPreference
    private val certificateBase64 = "MIIBkzCCATmgAwIBAgIBATAKBggqhkjOPQQDBDBTMS0wKwYDVQQDDCRlZTA2MWE3NV9lNDAzXzRlY2NfOTI2MV81ZmZlMjcyMmY2OTgxFDASBgNVBAsMC0tERSBDb25uZWN0MQwwCgYDVQQKDANLREUwHhcNMjMwOTE1MjIwMDAwWhcNMzQwOTE1MjIwMDAwWjBTMS0wKwYDVQQDDCRlZTA2MWE3NV9lNDAzXzRlY2NfOTI2MV81ZmZlMjcyMmY2OTgxFDASBgNVBAsMC0tERSBDb25uZWN0MQwwCgYDVQQKDANLREUwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAASqOIKTm5j6x8DKgYSkItLmjCgIXP0gkOW6bmVvloDGsYnvqYLMFGe7YW8g8lT/qPBTEfDOM4UpQ8X6jidE+XrnMAoGCCqGSM49BAMEA0gAMEUCIEpk6VNpbt3tfbWDf0TmoJftRq3wAs3Dke7d5vMZlivyAiEA/ZXtSRqPjs/2RN9SynKhSUA9/z0PNq6LYoAaC6TdomM="
    private val certificateHash = "fc:1f:b3:d3:d3:3b:23:42:e4:5c:74:b1:a6:13:dc:df:e5:e1:f0:29:d6:68:24:9f:50:49:52:a9:a8:04:1e:31:"
    private val deviceId = "testDevice"
    private val certificateKey = "certificate"
    private lateinit var mockBase64: MockedStatic<android.util.Base64>

    @Before
    fun setup() {
        context = Mockito.mock(Context::class.java)
        sharedPreferences = MockSharedPreference()
        Mockito.`when`(context.getSharedPreferences(deviceId, Context.MODE_PRIVATE)).thenReturn(sharedPreferences)

        val mockBase64 = Mockito.mockStatic(android.util.Base64::class.java)

        mockBase64.`when`<Any> {
            android.util.Base64.encodeToString(ArgumentMatchers.any(ByteArray::class.java), ArgumentMatchers.anyInt())
        }.thenAnswer { invocation: InvocationOnMock ->
            Base64.getMimeEncoder().encodeToString(invocation.arguments[0] as ByteArray)
        }

        mockBase64.`when`<Any> {
            android.util.Base64.decode(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt())
        }.thenAnswer { invocation: InvocationOnMock ->
            Base64.getMimeDecoder().decode(invocation.arguments[0] as String)
        }

        this.mockBase64 = mockBase64
    }

    @After
    fun tearDown() {
        mockBase64.close()
    }

    @Test
    fun testNoCertificateStored() {
        val isStored = SslHelper.isCertificateStored(context, deviceId)
        Assert.assertFalse(isStored)
    }

    @Test
    fun testCertificateStored() {
        sharedPreferences.edit().putString(certificateKey, certificateBase64).apply()
        Assert.assertTrue(SslHelper.isCertificateStored(context, deviceId))
        sharedPreferences.edit().remove(certificateKey).apply()
        Assert.assertFalse(SslHelper.isCertificateStored(context, deviceId))
    }

    @Test
    fun getAnyCertificate() {
        Assert.assertThrows(Exception::class.java) { SslHelper.getDeviceCertificate(context, deviceId) }
        sharedPreferences.edit().putString(certificateKey, certificateBase64).apply()
        Assert.assertNotNull(SslHelper.getDeviceCertificate(context, deviceId))
    }

    @Test
    fun getExpectedCertificate() {
        sharedPreferences.edit().putString(certificateKey, certificateBase64).apply()
        val cert = SslHelper.getDeviceCertificate(context, deviceId)
        Assert.assertEquals(certificateBase64, Base64.getEncoder().encodeToString(cert.encoded))
    }

    @Test
    fun getCertificateHash() {
        sharedPreferences.edit().putString(certificateKey, certificateBase64).apply()
        val cert = SslHelper.getDeviceCertificate(context, deviceId)
        val hash = SslHelper.getCertificateHash(cert)
        Assert.assertEquals(certificateHash, hash)
    }

    @Test
    fun parseCertificate() {
        val bytes = Base64.getDecoder().decode(certificateBase64)
        val cert = SslHelper.parseCertificate(bytes)
        val hash = SslHelper.getCertificateHash(cert)
        Assert.assertEquals(certificateHash, hash)
    }

    @Test
    fun getCommonName() {
        sharedPreferences.edit().putString(certificateKey, certificateBase64).apply()
        val cert = SslHelper.getDeviceCertificate(context, deviceId)
        val method = SslHelper::class.java.getDeclaredMethod("getCommonNameFromCertificate", X509Certificate::class.java)
        method.isAccessible = true
        val commonName = method.invoke(null, cert) as String
        val expected = "ee061a75_e403_4ecc_9261_5ffe2722f698"
        Assert.assertEquals(expected, commonName)
    }

}
