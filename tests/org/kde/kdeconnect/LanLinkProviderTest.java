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
import android.util.Log;

import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.transport.socket.nio.NioDatagramAcceptor;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.kde.kdeconnect.Backends.LanBackend.LanLink;
import org.kde.kdeconnect.Backends.LanBackend.LanLinkProvider;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

public class LanLinkProviderTest extends AndroidTestCase {

    private NioSocketAcceptor tcpAcceptor = null;
    private NioDatagramAcceptor udpAcceptor = null;
    private LanLinkProvider linkProvider;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().getPath());

        linkProvider = new LanLinkProvider(getContext());

        try {
            Field field = LanLinkProvider.class.getDeclaredField("tcpAcceptor");
            field.setAccessible(true);
            tcpAcceptor = (NioSocketAcceptor)field.get(linkProvider);
            assertNotNull(tcpAcceptor);
        }catch (Exception e){
            fail("Error getting tcpAcceptor from LanLinkProvider");
        }

        try{
            Field field = LanLinkProvider.class.getDeclaredField("udpAcceptor");
            field.setAccessible(true);
            udpAcceptor = (NioDatagramAcceptor)field.get(linkProvider);
            assertNotNull(udpAcceptor);
        }catch (Exception e){
            fail("Error getting udp acceptor from LanLinkProvider");
        }

    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        tcpAcceptor.dispose();
        udpAcceptor.dispose();
    }

    public void testTcpAcceptor(){

        assertNotNull(tcpAcceptor.getHandler());
        assertEquals(tcpAcceptor.getSessionConfig().isKeepAlive(), true);
        assertEquals(tcpAcceptor.getSessionConfig().isReuseAddress(), true);
        assertNotNull(tcpAcceptor.getFilterChain().get("codec"));

    }

    public void testUdpAcceptor(){

        assertNull(udpAcceptor.getHandler());
        assertEquals(udpAcceptor.getSessionConfig().isReuseAddress(), true);
        assertNotNull(udpAcceptor.getFilterChain().get("codec"));
    }

    public void testOnStart() throws Exception{

        IoSession session = Mockito.mock(IoSession.class);
        Mockito.when(session.getId()).thenReturn(12345l);
        Mockito.when(session.getRemoteAddress()).thenReturn(new InetSocketAddress(5000));

        linkProvider.onStart();

        assertNotNull(udpAcceptor.getHandler());
        assertEquals(udpAcceptor.getLocalAddress().getPort(), 1714);
    }

    public void testUdpPackageReceived() throws Exception {

        final int port = 5000;

        NetworkPackage networkPackage = Mockito.mock(NetworkPackage.class);
        Mockito.when(networkPackage.getType()).thenReturn("kdeconnect.identity");
        Mockito.when(networkPackage.getString("deviceId")).thenReturn("testDevice");
        Mockito.when(networkPackage.getString("deviceName")).thenReturn("Test Device");
        Mockito.when(networkPackage.getInt("protocolVersion")).thenReturn(NetworkPackage.ProtocolVersion);
        Mockito.when(networkPackage.getString("deviceType")).thenReturn("phone");
        Mockito.when(networkPackage.getInt("tcpPort")).thenReturn(port);

        final String serialized = "{\"type\":\"kdeconnect.identity\",\"id\":12345,\"body\":{\"deviceName\":\"Test Device\",\"deviceType\":\"phone\",\"deviceId\":\"testDevice\",\"protocolVersion\":5,\"tcpPort\": "+ port +"}}";
        Mockito.when(networkPackage.serialize()).thenReturn(serialized);

        // Mocking udp session
        IoSession session = Mockito.mock(IoSession.class);
        Mockito.when(session.getId()).thenReturn(12345l);
        Mockito.when(session.getRemoteAddress()).thenReturn(new InetSocketAddress(port));


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
                    assertEquals("Exception in thread",1,5);
                }
            }
        });

        try {
            thread.start();
            linkProvider.onStart();
            udpAcceptor.getHandler().messageReceived(session, networkPackage.serialize());
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

        IoSession session = Mockito.mock(IoSession.class);
        Mockito.when(session.getId()).thenReturn(12345l);

        NetworkPackage networkPackage = Mockito.mock(NetworkPackage.class);
        Mockito.when(networkPackage.getType()).thenReturn("kdeconnect.identity");
        Mockito.when(networkPackage.getString("deviceId")).thenReturn("testDevice");
        Mockito.when(networkPackage.getString("deviceName")).thenReturn("Test Device");
        Mockito.when(networkPackage.getInt("protocolVersion")).thenReturn(NetworkPackage.ProtocolVersion);
        Mockito.when(networkPackage.getString("deviceType")).thenReturn("phone");

        String serialized = "{\"type\":\"kdeconnect.identity\",\"id\":12345,\"body\":{\"deviceName\":\"Test Device\",\"deviceType\":\"phone\",\"deviceId\":\"testDevice\",\"protocolVersion\":5}}";
        Mockito.when(networkPackage.serialize()).thenReturn(serialized);

        try {
            tcpAcceptor.getHandler().messageReceived(session, networkPackage.serialize());
        }catch (Exception e){
            throw e;
        }

        LongSparseArray<LanLink> nioSessions;
        try {
            Field field = LanLinkProvider.class.getDeclaredField("nioSessions");
            field.setAccessible(true);
            nioSessions = (LongSparseArray<LanLink>)field.get(linkProvider);
        }catch (Exception e){
            throw e;
        }
        assertNotNull(nioSessions.get(12345l));


        HashMap<String, LanLink> visibleComputers;
        try {
            Field field = LanLinkProvider.class.getDeclaredField("visibleComputers");
            field.setAccessible(true);
            visibleComputers = (HashMap<String, LanLink>)field.get(linkProvider);
        }catch (Exception e){
            throw e;
        }
        assertNotNull(visibleComputers.get("testDevice"));


        // Testing session closed
        try {
            tcpAcceptor.getHandler().sessionClosed(session);
        }catch (Exception e){
            throw e;
        }
        assertNull(nioSessions.get(12345l));
        assertNull(visibleComputers.get("testDevice"));
    }
}
