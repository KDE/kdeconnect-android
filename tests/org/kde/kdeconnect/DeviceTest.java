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

package org.kde.kdeconnect;

import android.content.Context;
import android.content.SharedPreferences;
import android.test.AndroidTestCase;
import android.util.Base64;
import android.util.Log;

import org.kde.kdeconnect.Backends.BasePairingHandler;
import org.kde.kdeconnect.Backends.LanBackend.LanLink;
import org.kde.kdeconnect.Backends.LanBackend.LanLinkProvider;
import org.kde.kdeconnect.Backends.LanBackend.LanPairingHandler;
import org.mockito.Mockito;
import org.spongycastle.asn1.x500.X500NameBuilder;
import org.spongycastle.asn1.x500.style.BCStyle;
import org.spongycastle.cert.X509v3CertificateBuilder;
import org.spongycastle.cert.jcajce.JcaX509CertificateConverter;
import org.spongycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.spongycastle.jce.provider.BouncyCastleProvider;
import org.spongycastle.operator.ContentSigner;
import org.spongycastle.operator.jcajce.JcaContentSignerBuilder;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.Date;

public class DeviceTest extends AndroidTestCase {

    // Creating a paired device before each test case
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Dexmaker has problems guessing cache directory, setting manually
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().getPath());

        // Save new test device in settings
        Context context = getContext();

        String deviceId = "testDevice";
        String name = "Test Device";

        KeyPair keyPair;
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            keyPair = keyGen.genKeyPair();
        } catch(Exception e) {
            e.printStackTrace();
            Log.e("KDE/initializeRsaKeys", "Exception");
            return;
        }

        SharedPreferences settings = context.getSharedPreferences(deviceId, Context.MODE_PRIVATE);

        //Store device information needed to create a Device object in a future
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("deviceName", name);
        editor.putString("deviceType", Device.DeviceType.Phone.toString());
        editor.putString("publicKey", Base64.encodeToString(keyPair.getPublic().getEncoded(), 0).trim() + "\n");
        editor.apply();

        SharedPreferences preferences = context.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE);
        preferences.edit().putBoolean(deviceId,true).apply();
    }


    // Removing paired device info after each test case
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        // Remove saved test device
        Context context = getContext();

        String deviceId = "testDevice";

        SharedPreferences settings = context.getSharedPreferences(deviceId, Context.MODE_PRIVATE);

        //Store device information needed to create a Device object in a future
        SharedPreferences.Editor editor = settings.edit();
        editor.clear();
        editor.apply();

        SharedPreferences preferences = context.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE);
        preferences.edit().remove(deviceId).apply();
    }

    // Basic paired device testing
    public void testDevice(){

        Device device = new Device(getContext(), "testDevice");

        assertEquals(device.getDeviceId(), "testDevice");
        assertEquals(device.getDeviceType(), Device.DeviceType.Phone);
        assertEquals(device.getName(), "Test Device");
        assertEquals(device.isPaired(), true);

    }

    // Testing pairing done using reflection since it is private
    // Created an unpaired device inside this test
    public void testPairingDone(){

        NetworkPackage fakeNetworkPackage = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_IDENTITY);
        fakeNetworkPackage.set("deviceId", "unpairedTestDevice");
        fakeNetworkPackage.set("deviceName", "Unpaired Test Device");
        fakeNetworkPackage.set("protocolVersion", NetworkPackage.ProtocolVersion);
        fakeNetworkPackage.set("deviceType", Device.DeviceType.Phone.toString());

        LanLinkProvider linkProvider = Mockito.mock(LanLinkProvider.class);
        Mockito.when(linkProvider.getName()).thenReturn("LanLinkProvider");
        LanLink link = Mockito.mock(LanLink.class);
        Mockito.when(link.getLinkProvider()).thenReturn(linkProvider);
        Mockito.when(link.getPairingHandler(Mockito.any(Device.class), Mockito.any(BasePairingHandler.PairingHandlerCallback.class))).thenReturn(Mockito.mock(LanPairingHandler.class));
        Device device = new Device(getContext(), fakeNetworkPackage, link);

        KeyPair keyPair;
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            keyPair = keyGen.genKeyPair();
        } catch(Exception e) {
            e.printStackTrace();
            Log.e("KDE/initializeRsaKeys", "Exception");
            return;
        }
        device.publicKey = keyPair.getPublic();

        assertNotNull(device);
        assertEquals(device.getDeviceId(), "unpairedTestDevice");
        assertEquals(device.getName(), "Unpaired Test Device");
        assertEquals(device.getDeviceType(), Device.DeviceType.Phone);
        assertNotNull(device.publicKey);
        assertNull(device.certificate);

        Method method;
        try {
            method = Device.class.getDeclaredMethod("pairingDone");
            method.setAccessible(true);
            method.invoke(device);
        }catch (Exception e){
            e.printStackTrace();
        }

        assertEquals(device.isPaired(), true);

        SharedPreferences preferences = getContext().getSharedPreferences("trusted_devices", Context.MODE_PRIVATE);
        assertEquals(preferences.getBoolean(device.getDeviceId(), false), true);

        SharedPreferences settings = getContext().getSharedPreferences(device.getDeviceId(),Context.MODE_PRIVATE);
        assertEquals(settings.getString("deviceName", "Unknown device"), "Unpaired Test Device");
        assertEquals(settings.getString("deviceType", "tablet"), "phone");

        // Cleanup for unpaired test device
        preferences.edit().remove(device.getDeviceId()).apply();
        settings.edit().clear().apply();

    }

    public void testPairingDoneWithCertificate() throws Exception{
        KeyPair keyPair = null;
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            keyPair = keyGen.genKeyPair();
        } catch(Exception e) {
            e.printStackTrace();
            Log.e("KDE/initializeRsaKeys", "Exception");
        }

        X509Certificate certificate = null;
        try {

            BouncyCastleProvider BC = new BouncyCastleProvider();

            X500NameBuilder nameBuilder = new X500NameBuilder(BCStyle.INSTANCE);
            nameBuilder.addRDN(BCStyle.CN, "testDevice");
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
                    keyPair.getPublic()
            );
            ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256WithRSAEncryption").setProvider(BC).build(keyPair.getPrivate());
            certificate = new JcaX509CertificateConverter().setProvider(BC).getCertificate(certificateBuilder.build(contentSigner));

        } catch(Exception e) {
            e.printStackTrace();
            Log.e("KDE/initialiseCert", "Exception");
        }

        NetworkPackage fakeNetworkPackage = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_IDENTITY);
        fakeNetworkPackage.set("deviceId", "unpairedTestDevice");
        fakeNetworkPackage.set("deviceName", "Unpaired Test Device");
        fakeNetworkPackage.set("protocolVersion", NetworkPackage.ProtocolVersion);
        fakeNetworkPackage.set("deviceType", Device.DeviceType.Phone.toString());
        fakeNetworkPackage.set("certificate", Base64.encodeToString(certificate.getEncoded(), 0));

        LanLinkProvider linkProvider = Mockito.mock(LanLinkProvider.class);
        Mockito.when(linkProvider.getName()).thenReturn("LanLinkProvider");
        LanLink link = Mockito.mock(LanLink.class);
        Mockito.when(link.getPairingHandler(Mockito.any(Device.class), Mockito.any(BasePairingHandler.PairingHandlerCallback.class))).thenReturn(Mockito.mock(LanPairingHandler.class));
        Mockito.when(link.getLinkProvider()).thenReturn(linkProvider);
        Device device = new Device(getContext(), fakeNetworkPackage, link);
        device.publicKey = keyPair.getPublic();

        assertNotNull(device);
        assertEquals(device.getDeviceId(), "unpairedTestDevice");
        assertEquals(device.getName(), "Unpaired Test Device");
        assertEquals(device.getDeviceType(), Device.DeviceType.Phone);
        assertNotNull(device.publicKey);
        assertNotNull(device.certificate);

        Method method;
        try {
            method = Device.class.getDeclaredMethod("pairingDone");
            method.setAccessible(true);
            method.invoke(device);
        }catch (Exception e){
            e.printStackTrace();
        }

        assertEquals(device.isPaired(), true);

        SharedPreferences preferences = getContext().getSharedPreferences("trusted_devices", Context.MODE_PRIVATE);
        assertEquals(preferences.getBoolean(device.getDeviceId(), false), true);

        SharedPreferences settings = getContext().getSharedPreferences(device.getDeviceId(),Context.MODE_PRIVATE);
        assertEquals(settings.getString("deviceName", "Unknown device"), "Unpaired Test Device");
        assertEquals(settings.getString("deviceType", "tablet"), "phone");

        // Cleanup for unpaired test device
        preferences.edit().remove(device.getDeviceId()).apply();
        settings.edit().clear().apply();
    }

    public void testUnpair(){

        Device device = new Device(getContext(), "testDevice");

        device.unpair();

        assertEquals(device.isPaired(), false);

        SharedPreferences preferences = getContext().getSharedPreferences("trusted_devices", Context.MODE_PRIVATE);
        assertEquals(preferences.getBoolean(device.getDeviceId(), false), false);

    }


}
