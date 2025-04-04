/*
 * SPDX-FileCopyrightText: 2015 Vineet Garg <grg.vineet@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Helpers.SecurityHelpers;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.kde.kdeconnect.Helpers.DeviceHelper;
import org.kde.kdeconnect.Helpers.RandomHelper;
import org.kde.kdeconnect.KdeConnect;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.Socket;
import java.net.SocketException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Formatter;
import java.util.Locale;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

public class SslHelper {

    public static Certificate certificate; //my device's certificate
    private static final CertificateFactory factory;
    static {
        try {
            factory = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressLint({"CustomX509TrustManager", "TrustAllX509TrustManager"})
    private final static TrustManager[] trustAllCerts = new TrustManager[] {
        new X509TrustManager() {
            private final X509Certificate[] issuers = new X509Certificate[0];
            @Override public X509Certificate[] getAcceptedIssuers() { return issuers; }
            @Override public void checkClientTrusted(X509Certificate[] certs, String authType) { }
            @Override public void checkServerTrusted(X509Certificate[] certs, String authType) { }
        }
    };

    public static void initialiseCertificate(Context context) {
        PrivateKey privateKey;
        PublicKey publicKey;

        try {
            privateKey = RsaHelper.getPrivateKey(context);
            publicKey = RsaHelper.getPublicKey(context);
        } catch (Exception e) {
            Log.e("SslHelper", "Error getting keys, can't create certificate");
            return;
        }

        Log.i("SslHelper", "Key algorithm: " + publicKey.getAlgorithm());

        String deviceId = DeviceHelper.getDeviceId(context);

        boolean needsToGenerateCertificate = false;
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);

        if (settings.contains("certificate")) {
            final Date currDate = new Date();
            try {
                SharedPreferences globalSettings = PreferenceManager.getDefaultSharedPreferences(context);
                byte[] certificateBytes = Base64.decode(globalSettings.getString("certificate", ""), 0);
                X509Certificate cert = (X509Certificate) parseCertificate(certificateBytes);

                String certDeviceId = getCommonNameFromCertificate(cert);
                if (!certDeviceId.equals(deviceId)) {
                    Log.e("KDE/SslHelper", "The certificate stored is from a different device id! (found: " + certDeviceId + " expected:" + deviceId + ")");
                    needsToGenerateCertificate = true;
                }
                else if(cert.getNotAfter().getTime() < currDate.getTime()) {
                    Log.e("KDE/SslHelper", "The certificate expired: "+cert.getNotAfter());
                    needsToGenerateCertificate = true;
                } else if(cert.getNotBefore().getTime() > currDate.getTime()) {
                    Log.e("KDE/SslHelper", "The certificate is not effective yet: "+cert.getNotBefore());
                    needsToGenerateCertificate = true;
                }else {
                    certificate = cert;
                }
            } catch (Exception e) {
                Log.e("KDE/SslHelper", "Exception reading own certificate", e);
                needsToGenerateCertificate = true;
            }

        } else {
            needsToGenerateCertificate = true;
        }

        if (needsToGenerateCertificate) {
            KdeConnect.getInstance().removeRememberedDevices();
            Log.i("KDE/SslHelper", "Generating a certificate");
            try {
                //Fix for https://issuetracker.google.com/issues/37095309
                Locale initialLocale = Locale.getDefault();
                setLocale(Locale.ENGLISH, context);

                X500NameBuilder nameBuilder = new X500NameBuilder(BCStyle.INSTANCE);
                nameBuilder.addRDN(BCStyle.CN, deviceId);
                nameBuilder.addRDN(BCStyle.OU, "KDE Connect");
                nameBuilder.addRDN(BCStyle.O, "KDE");
                final LocalDate localDate = LocalDate.now();
                final Instant notBefore = localDate.minusYears(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
                final Instant notAfter = localDate.plusYears(10).atStartOfDay(ZoneId.systemDefault()).toInstant();
                X509v3CertificateBuilder certificateBuilder = new JcaX509v3CertificateBuilder(
                        nameBuilder.build(),
                        BigInteger.ONE,
                        Date.from(notBefore),
                        Date.from(notAfter),
                        nameBuilder.build(),
                        publicKey
                );
                String keyAlgorithm = privateKey.getAlgorithm();
                String signatureAlgorithm = "RSA".equals(keyAlgorithm)? "SHA512withRSA" : "SHA512withECDSA";
                ContentSigner contentSigner = new JcaContentSignerBuilder(signatureAlgorithm).build(privateKey);
                byte[] certificateBytes = certificateBuilder.build(contentSigner).getEncoded();
                certificate = parseCertificate(certificateBytes);

                SharedPreferences.Editor edit = settings.edit();
                edit.putString("certificate", Base64.encodeToString(certificateBytes, 0));
                edit.apply();

                setLocale(initialLocale, context);
            } catch (Exception e) {
                Log.e("KDE/initialiseCert", "Exception", e);
            }
        }
    }

    private static void setLocale(Locale locale, Context context) {
        Locale.setDefault(locale);
        Resources resources = context.getResources();
        Configuration config = resources.getConfiguration();
        config.locale = locale;
        resources.updateConfiguration(config, resources.getDisplayMetrics());
    }

    public static boolean isCertificateStored(Context context, String deviceId) {
        SharedPreferences devicePreferences = context.getSharedPreferences(deviceId, Context.MODE_PRIVATE);
        String cert = devicePreferences.getString("certificate", "");
        return !cert.isEmpty();
    }

    /**
     * Returns the stored certificate for a trusted device
     **/
    public static Certificate getDeviceCertificate(Context context, String deviceId) throws CertificateException {
        SharedPreferences devicePreferences = context.getSharedPreferences(deviceId, Context.MODE_PRIVATE);
        byte[] certificateBytes = Base64.decode(devicePreferences.getString("certificate", ""), 0);
        return parseCertificate(certificateBytes);
    }

    private static SSLContext getSslContextForDevice(Context context, String deviceId, boolean isDeviceTrusted) {
        //TODO: Cache
        try {
            // Get device private key
            PrivateKey privateKey = RsaHelper.getPrivateKey(context);

            // Setup keystore
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setKeyEntry("key", privateKey, "".toCharArray(), new Certificate[]{certificate});

            // Add device certificate if device trusted
            if (isDeviceTrusted) {
                Certificate remoteDeviceCertificate = getDeviceCertificate(context, deviceId);
                keyStore.setCertificateEntry(deviceId, remoteDeviceCertificate);
            }

            // Setup key manager factory
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, "".toCharArray());


            // Setup default trust manager
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);

            // Setup custom trust manager if device not trusted
            SSLContext tlsContext = SSLContext.getInstance("TLSv1.2"); // Use TLS up to 1.2, since 1.3 seems to cause issues in some (older?) devices
            if (isDeviceTrusted) {
                tlsContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), RandomHelper.secureRandom);
            } else {
                tlsContext.init(keyManagerFactory.getKeyManagers(), trustAllCerts, RandomHelper.secureRandom);
            }
            return tlsContext;
        } catch (Exception e) {
            Log.e("KDE/SslHelper", "Error creating tls context", e);
        }
        return null;

    }

    private static void configureSslSocket(SSLSocket socket, boolean isDeviceTrusted, boolean isClient) throws SocketException {
       socket.setSoTimeout(10000);
        if (isClient) {
            socket.setUseClientMode(true);
        } else {
            socket.setUseClientMode(false);
            if (isDeviceTrusted) {
                socket.setNeedClientAuth(true);
            } else {
                socket.setWantClientAuth(true);
            }
        }

    }

    public static SSLSocket convertToSslSocket(Context context, Socket socket, String deviceId, boolean isDeviceTrusted, boolean clientMode) throws IOException {
        SSLSocketFactory sslsocketFactory = SslHelper.getSslContextForDevice(context, deviceId, isDeviceTrusted).getSocketFactory();
        SSLSocket sslsocket = (SSLSocket) sslsocketFactory.createSocket(socket, socket.getInetAddress().getHostAddress(), socket.getPort(), true);
        SslHelper.configureSslSocket(sslsocket, isDeviceTrusted, clientMode);
        return sslsocket;
    }

    public static String getCertificateHash(Certificate certificate) {
        byte[] hash;
        try {
            hash = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
        } catch (NoSuchAlgorithmException | CertificateEncodingException e) {
            throw new RuntimeException(e);
        }
        Formatter formatter = new Formatter();
        for (byte b : hash) {
            formatter.format("%02x:", b);
        }
        return formatter.toString();
    }

    public static Certificate parseCertificate(byte[] certificateBytes) throws CertificateException {
        return factory.generateCertificate(new ByteArrayInputStream(certificateBytes));
    }

    private static String getCommonNameFromCertificate(X509Certificate cert) {
        X500Principal principal = cert.getSubjectX500Principal();
        X500Name x500name = new X500Name(principal.getName());
        RDN rdn = x500name.getRDNs(BCStyle.CN)[0];
        return IETFUtils.valueToString(rdn.getFirst().getValue());
    }

}
