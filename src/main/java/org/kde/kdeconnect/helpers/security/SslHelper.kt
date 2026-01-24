/*
 * SPDX-FileCopyrightText: 2015 Vineet Garg <grg.vineet@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.helpers.security

import android.annotation.SuppressLint
import android.content.Context
import android.preference.PreferenceManager
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x500.style.IETFUtils
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.kde.kdeconnect.helpers.DeviceHelper.getDeviceId
import org.kde.kdeconnect.helpers.RandomHelper
import org.kde.kdeconnect.helpers.security.RsaHelper.getPrivateKey
import org.kde.kdeconnect.helpers.security.RsaHelper.getPublicKey
import org.kde.kdeconnect.helpers.TrustedDevices
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.net.Socket
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Formatter
import java.util.Locale
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object SslHelper {
    lateinit var certificate: Certificate //my device's certificate
    private val factory: CertificateFactory = CertificateFactory.getInstance("X.509")

    @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    private val trustAllCerts: Array<TrustManager> = arrayOf(object : X509TrustManager {
        private val issuers = emptyArray<X509Certificate>()
        override fun getAcceptedIssuers(): Array<X509Certificate> = issuers
        override fun checkClientTrusted(certs: Array<X509Certificate?>?, authType: String?) = Unit
        override fun checkServerTrusted(certs: Array<X509Certificate?>?, authType: String?) = Unit
    })

    fun initialiseCertificate(context: Context) {
        val privateKey: PrivateKey = getPrivateKey(context)
        val publicKey: PublicKey = getPublicKey(context)

        Log.i(LOG_TAG, "Key algorithm: " + publicKey.algorithm)

        val deviceId = getDeviceId(context)

        var needsToGenerateCertificate = false
        val settings = PreferenceManager.getDefaultSharedPreferences(context)

        if (settings.contains("certificate")) {
            val currDate = Date()
            try {
                val certificateBytes = Base64.decode(settings.getString("certificate", ""), 0)
                val cert = parseCertificate(certificateBytes) as X509Certificate

                val certDeviceId = getCommonNameFromCertificate(cert)
                if (certDeviceId != deviceId) {
                    Log.e(LOG_TAG,"The certificate stored is from a different device id! (found: $certDeviceId expected:$deviceId)")
                    needsToGenerateCertificate = true
                } else if (cert.notAfter.time < currDate.time) {
                    Log.e(LOG_TAG, "The certificate expired: " + cert.notAfter)
                    needsToGenerateCertificate = true
                } else if (cert.notBefore.time > currDate.time) {
                    Log.e(LOG_TAG, "The certificate is not effective yet: " + cert.notBefore)
                    needsToGenerateCertificate = true
                } else {
                    certificate = cert
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Exception reading own certificate", e)
                needsToGenerateCertificate = true
            }
        } else {
            needsToGenerateCertificate = true
        }

        if (needsToGenerateCertificate) {
            TrustedDevices.removeAllTrustedDevices(context)
            Log.i(LOG_TAG, "Generating a certificate")
            //Fix for https://issuetracker.google.com/issues/37095309
            val initialLocale = Locale.getDefault()
            setLocale(Locale.ENGLISH, context)

            val nameBuilder = X500NameBuilder(BCStyle.INSTANCE)
            nameBuilder.addRDN(BCStyle.CN, deviceId)
            nameBuilder.addRDN(BCStyle.OU, "KDE Connect")
            nameBuilder.addRDN(BCStyle.O, "KDE")
            val localDate = LocalDate.now()
            val notBefore = localDate.minusYears(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
            val notAfter = localDate.plusYears(10).atStartOfDay(ZoneId.systemDefault()).toInstant()
            val certificateBuilder: X509v3CertificateBuilder = JcaX509v3CertificateBuilder(
                nameBuilder.build(),
                BigInteger.ONE,
                Date.from(notBefore),
                Date.from(notAfter),
                nameBuilder.build(),
                publicKey
            )
            val keyAlgorithm = privateKey.algorithm
            val signatureAlgorithm = if ("RSA" == keyAlgorithm) "SHA512withRSA" else "SHA512withECDSA"
            val contentSigner = JcaContentSignerBuilder(signatureAlgorithm).build(privateKey)
            val certificateBytes = certificateBuilder.build(contentSigner).encoded
            certificate = parseCertificate(certificateBytes)

            settings.edit {
                putString("certificate", Base64.encodeToString(certificateBytes, 0))
            }

            setLocale(initialLocale, context)
        }
    }

    private fun setLocale(locale: Locale, context: Context) {
        Locale.setDefault(locale)
        val resources = context.resources
        val config = resources.configuration
        config.locale = locale
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun getSslContextForDevice(context: Context, deviceId: String, isDeviceTrusted: Boolean): SSLContext {
        // TODO: This method is called for each payload that is sent. Cache the result.
        val privateKey = getPrivateKey(context)

        // Setup keystore
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        keyStore.setKeyEntry("key", privateKey, "".toCharArray(), arrayOf(certificate))

        // Add device certificate if device trusted
        if (isDeviceTrusted) {
            val remoteDeviceCertificate = TrustedDevices.getDeviceCertificate(context, deviceId)
            keyStore.setCertificateEntry(deviceId, remoteDeviceCertificate)
        }

        // Setup key manager factory
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyStore, "".toCharArray())

        // Setup default trust manager
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(keyStore)

        // Setup custom trust manager if device not trusted
        val tlsContext = SSLContext.getInstance("TLSv1.2") // Use TLS up to 1.2, since 1.3 seems to cause issues in some (older?) devices
        if (isDeviceTrusted) {
            tlsContext.init(keyManagerFactory.keyManagers, trustManagerFactory.trustManagers, RandomHelper.secureRandom)
        } else {
            tlsContext.init(keyManagerFactory.keyManagers, trustAllCerts, RandomHelper.secureRandom)
        }
        return tlsContext
    }

    private fun configureSslSocket(socket: SSLSocket, isDeviceTrusted: Boolean, isClient: Boolean) {
        socket.setSoTimeout(10000)
        if (isClient) {
            socket.useClientMode = true
        } else {
            socket.useClientMode = false
            if (isDeviceTrusted) {
                socket.needClientAuth = true
            } else {
                socket.wantClientAuth = true
            }
        }
    }

    @JvmStatic
    fun convertToSslSocket(context: Context, socket: Socket, deviceId: String, isDeviceTrusted: Boolean, clientMode: Boolean): SSLSocket {
        val sslSocketFactory = getSslContextForDevice(context, deviceId, isDeviceTrusted).socketFactory
        val sslSocket = sslSocketFactory.createSocket(socket, socket.getInetAddress().hostAddress, socket.getPort(), true) as SSLSocket
        configureSslSocket(sslSocket, isDeviceTrusted, clientMode)
        return sslSocket
    }

    fun getCertificateHash(certificate: Certificate): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
        val formatter = Formatter()
        for (b in hash) {
            formatter.format("%02x:", b)
        }
        return formatter.toString()
    }

    fun parseCertificate(certificateBytes: ByteArray): Certificate {
        return factory.generateCertificate(ByteArrayInputStream(certificateBytes))
    }

    fun getCommonNameFromCertificate(cert: X509Certificate): String {
        val principal = cert.getSubjectX500Principal()
        val x500name = X500Name(principal.name)
        val rdn = x500name.getRDNs(BCStyle.CN).first()
        return IETFUtils.valueToString(rdn.getFirst().value)
    }

    private const val LOG_TAG = "KDE/SslHelper"
}
