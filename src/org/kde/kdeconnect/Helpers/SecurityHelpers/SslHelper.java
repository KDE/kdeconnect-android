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

package org.kde.kdeconnect.Helpers.SecurityHelpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

import org.apache.mina.filter.ssl.SslFilter;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Date;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import io.netty.handler.ssl.SslContext;


public class SslHelper {

    public enum SslMode{
        Client,
        Server
    }

    public static X509Certificate certificate; //my device's certificate

    public static void initialiseCertificate(Context context){
        PrivateKey privateKey;
        PublicKey publicKey;

        try {
            privateKey = RsaHelper.getPrivateKey(context);
            publicKey = RsaHelper.getPublicKey(context);
        }catch (Exception e){
            Log.e("SslHelper", "Error getting keys, can't create certificate");
            return;
        }
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        if (!settings.contains("certificate")) {
            try {

                BouncyCastleProvider BC = new BouncyCastleProvider();

                X500NameBuilder nameBuilder = new X500NameBuilder(BCStyle.INSTANCE);
                nameBuilder.addRDN(BCStyle.CN, settings.getString("device_name_preference", "")); // TODO : Chamge it to deviceId
                nameBuilder.addRDN(BCStyle.OU, "KDE Connect");
                nameBuilder.addRDN(BCStyle.O, "KDE");
                Date notBefore = new Date(System.currentTimeMillis());
                Date notAfter = new Date(System.currentTimeMillis() + System.currentTimeMillis());
                X509v3CertificateBuilder certificateBuilder = new JcaX509v3CertificateBuilder(
                        nameBuilder.build(),
                        BigInteger.ONE,
                        notBefore,
                        notAfter,
                        nameBuilder.build(),
                        publicKey
                );
                ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256WithRSAEncryption").setProvider(BC).build(privateKey);
                certificate = new JcaX509CertificateConverter().setProvider(BC).getCertificate(certificateBuilder.build(contentSigner));

                SharedPreferences.Editor edit = settings.edit();
                edit.putString("certificate", Base64.encodeToString(certificate.getEncoded(), 0));
                edit.apply();

            } catch(Exception e) {
                e.printStackTrace();
                Log.e("KDE/initialiseCertificate", "Exception");
                return;
            }

        } else {
            try {
                SharedPreferences globalSettings = PreferenceManager.getDefaultSharedPreferences(context);
                byte[] certificateBytes = Base64.decode(globalSettings.getString("certificate", ""), 0);
                X509CertificateHolder certificateHolder = new X509CertificateHolder(certificateBytes);
                certificate = new JcaX509CertificateConverter().setProvider(new BouncyCastleProvider()).getCertificate(certificateHolder);
            } catch (Exception e) {
                Log.e("KDE/SslHelper", "Exception reading own certificate");
                e.printStackTrace();
            }
        }
    }

    public static SSLContext getSslContext(Context context, String deviceId, boolean isDeviceTrusted) {
        try {
            // Get device private key
            PrivateKey privateKey = RsaHelper.getPrivateKey(context);

            // Get remote device certificate if trusted
            java.security.cert.Certificate remoteDeviceCertificate = null;
            if (isDeviceTrusted){
                SharedPreferences devicePreferences = context.getSharedPreferences(deviceId, Context.MODE_PRIVATE);
                byte[] certificateBytes = Base64.decode(devicePreferences.getString("certificate", ""), 0);
                X509CertificateHolder certificateHolder = new X509CertificateHolder(certificateBytes);
                remoteDeviceCertificate = new JcaX509CertificateConverter().setProvider(new BouncyCastleProvider()).getCertificate(certificateHolder);
            }

            // Setup keystore
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setKeyEntry("key", privateKey, "".toCharArray(), new java.security.cert.Certificate[]{certificate});;
            // Set certificate if device trusted
            if (remoteDeviceCertificate != null){
                keyStore.setCertificateEntry("remoteCertificate", remoteDeviceCertificate);
            }

            // Setup key manager factory
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, "".toCharArray());


            // Setup default trust manager
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);

            // Setup custom trust manager if device not trusted
            TrustManager[] trustAllCerts = new TrustManager[] {new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }

                @Override
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }

            }
            };

            SSLContext tlsContext = SSLContext.getInstance("TLS");
            if (isDeviceTrusted) {
                tlsContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), new SecureRandom());
            }else {
                tlsContext.init(keyManagerFactory.getKeyManagers(), trustAllCerts, new SecureRandom());
            }
            return tlsContext;
        } catch (Exception e) {
            Log.e("KDE/SslHelper", "Error creating tls context");
            e.printStackTrace();
        }
        return null;

    }

    public static SSLEngine getSslEngine(final Context context, final String deviceId, SslMode sslMode) {

        try{

            SharedPreferences preferences = context.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE);
            final boolean isDeviceTrusted = preferences.getBoolean(deviceId, false);

            SSLContext tlsContext = getSslContext(context, deviceId, isDeviceTrusted);
            SSLEngine sslEngine = tlsContext.createSSLEngine();

            if (sslMode == SslMode.Client){
                sslEngine.setUseClientMode(true);
            }else{
                sslEngine.setUseClientMode(false);
                if (isDeviceTrusted) {
                    sslEngine.setNeedClientAuth(true);
                }else {
                    sslEngine.setWantClientAuth(true);
                }
            }

            return sslEngine;
        }catch (Exception e){
            e.printStackTrace();
            Log.e("SslHelper", "Error creating ssl filter");
        }
        return null;
    }

    // TODO : Remove this method, and use the above one, since it trusts all certificates
    public static SslFilter getTrustAllCertsFilter(Context context, SslMode sslMode){
        try{

            // Get device private key
            PrivateKey privateKey = RsaHelper.getPrivateKey(context);

            // Get my certificate
            SharedPreferences globalSettings = PreferenceManager.getDefaultSharedPreferences(context);
            byte[] myCertificateBytes = Base64.decode(globalSettings.getString("certificate", ""), 0);
            X509CertificateHolder myCertificateHolder = new X509CertificateHolder(myCertificateBytes);
            X509Certificate myCertificate = new JcaX509CertificateConverter().setProvider(new BouncyCastleProvider()).getCertificate(myCertificateHolder);


            // Setup keystore
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setKeyEntry("key", privateKey, "".toCharArray(), new java.security.cert.Certificate[]{myCertificate});;

            // Setup key manager factory
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, "".toCharArray());


            // Setup default trust manager
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);

            // Setup custom trust manager if device not trusted
            TrustManager[] trustAllCerts = new TrustManager[] {new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }

                @Override
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }

            }
            };

            // TODO : if device trusted, set need client auth

            SSLContext tlsContext = SSLContext.getInstance("TLS");
            tlsContext.init(keyManagerFactory.getKeyManagers(), trustAllCerts, new SecureRandom());
            SslFilter filter = new SslFilter(tlsContext,true);

            if (sslMode == SslMode.Client){
                filter.setUseClientMode(true);
            }else{
                filter.setUseClientMode(false);
                filter.setWantClientAuth(true);
            }

            return filter;
        }catch (Exception e){
            e.printStackTrace();
            Log.e("SslHelper", "Error creating ssl filter");
        }
        return null;
    }
}
