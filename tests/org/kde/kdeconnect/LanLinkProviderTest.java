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

import android.support.v4.util.LongSparseArray;
import android.test.AndroidTestCase;

import org.kde.kdeconnect.Backends.LanBackend.LanLink;
import org.kde.kdeconnect.Backends.LanBackend.LanLinkProvider;
import org.mockito.Mockito;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

import io.netty.channel.Channel;

public class LanLinkProviderTest extends AndroidTestCase {

    private LanLinkProvider linkProvider;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().getPath());

        linkProvider = new LanLinkProvider(getContext());
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

    }

    public void testUdpPackageReceived() throws Exception {

        final int port = 5000;

        NetworkPackage networkPackage = Mockito.mock(NetworkPackage.class);
        Mockito.when(networkPackage.getType()).thenReturn("kdeconnect.identity");
        Mockito.when(networkPackage.getString("deviceId")).thenReturn("testDevice");
        Mockito.when(networkPackage.getString("deviceName")).thenReturn("Test Device");
        Mockito.when(networkPackage.getInt("protocolVersion")).thenReturn(5);
        Mockito.when(networkPackage.getString("deviceType")).thenReturn("phone");
        Mockito.when(networkPackage.getInt("tcpPort")).thenReturn(port);

        final String serialized = "{\"type\":\"kdeconnect.identity\",\"id\":12345,\"body\":{\"deviceName\":\"Test Device\",\"deviceType\":\"phone\",\"deviceId\":\"testDevice\",\"protocolVersion\":5,\"tcpPort\": "+ port +"}}";
        Mockito.when(networkPackage.serialize()).thenReturn(serialized);

        // Making a server socket, so that original LanLinkProvider can connect to it when it receives our fake package
        final ServerSocket serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress(port));

        final Thread thread = new Thread (new Runnable() {
            @Override
            public void run() {
                try {
                    Socket socket = serverSocket.accept();
                    InputStream inputStream = socket.getInputStream();
                    while (true) {
                        if (inputStream.available() != 0) {
                            // Data received from socket should be an identity package
                            byte[] inputData = new byte[inputStream.available()];
                            inputStream.read(inputData);

                            NetworkPackage receivedPackage = NetworkPackage.unserialize(new String(inputData));
                            NetworkPackage identityPackage = NetworkPackage.createIdentityPackage(getContext());

                            // If any assertion fails, its output will be in logcat, not on test case thread anymore
                            assertEquals(receivedPackage.getType(), identityPackage.getType());
                            assertEquals(receivedPackage.getString("deviceName"), identityPackage.getString("deviceName"));
                            assertEquals(receivedPackage.getString("deviceId"), identityPackage.getString("deviceId"));
                            assertEquals(receivedPackage.getInt("protocolVersion"), identityPackage.getInt("protocolVersion"));

                            serverSocket.close();
                            // Socket not closed to ensure visibleComputers contains entry for testDevice
                            break;
                        }
                    }

                }catch (Exception e){
                    fail("Exception in thread");
                }
            }
        });

        try {
            thread.start();
            linkProvider.onStart();

            Method method = LanLinkProvider.class.getDeclaredMethod("identityPackageReceived", NetworkPackage.class, Channel.class, LanLink.ConnectionStarted.class);
            method.setAccessible(true);
            method.invoke(linkProvider, networkPackage, Mockito.mock(Channel.class), LanLink.ConnectionStarted.Remotely);

        }catch (Exception e){
            throw e;
        }

        // Wait 1 secs for our server, and then end test
        thread.join(1 * 1000);

        // visibleComputers should contain an entry for testDevice
        HashMap<String, LanLink> visibleComputers;
        try {
            Field field = LanLinkProvider.class.getDeclaredField("visibleComputers");
            field.setAccessible(true);
            visibleComputers = (HashMap<String, LanLink>)field.get(linkProvider);
        }catch (Exception e){
            throw e;
        }
        assertNotNull(visibleComputers.get("testDevice"));

    }


    public void testTcpIdentityPackageReceived() throws Exception{

        NetworkPackage networkPackage = Mockito.mock(NetworkPackage.class);
        Mockito.when(networkPackage.getType()).thenReturn("kdeconnect.identity");
        Mockito.when(networkPackage.getString("deviceId")).thenReturn("testDevice");
        Mockito.when(networkPackage.getString("deviceName")).thenReturn("Test Device");
        Mockito.when(networkPackage.getInt("protocolVersion")).thenReturn(5);
        Mockito.when(networkPackage.getString("deviceType")).thenReturn("phone");

        String serialized = "{\"type\":\"kdeconnect.identity\",\"id\":12345,\"body\":{\"deviceName\":\"Test Device\",\"deviceType\":\"phone\",\"deviceId\":\"testDevice\",\"protocolVersion\":5}}";
        Mockito.when(networkPackage.serialize()).thenReturn(serialized);

        Channel channel = Mockito.mock(Channel.class);
        try {
            Method method = LanLinkProvider.class.getDeclaredMethod("identityPackageReceived", NetworkPackage.class, Channel.class, LanLink.ConnectionStarted.class);
            method.setAccessible(true);
            method.invoke(linkProvider, networkPackage, channel, LanLink.ConnectionStarted.Locally);
        }catch (Exception e){
            throw e;
        }

        LongSparseArray<LanLink> nioLinks;
        try {
            Field field = LanLinkProvider.class.getDeclaredField("nioLinks");
            field.setAccessible(true);
            nioLinks = (LongSparseArray<LanLink>)field.get(linkProvider);
        }catch (Exception e){
            throw e;
        }
        assertNotNull(nioLinks.get(channel.hashCode()));


        HashMap<String, LanLink> visibleComputers;
        try {
            Field field = LanLinkProvider.class.getDeclaredField("visibleComputers");
            field.setAccessible(true);
            visibleComputers = (HashMap<String, LanLink>)field.get(linkProvider);
        }catch (Exception e){
            throw e;
        }
        assertNotNull(visibleComputers.get("testDevice"));

        // TODO: Testing session closed
        //assertNull(nioSessions.get(12345l));
        //assertNull(visibleComputers.get("testDevice"));
    }
}
