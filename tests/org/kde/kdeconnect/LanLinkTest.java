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

import android.test.AndroidTestCase;
import android.util.Log;

import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.session.IoSession;
import org.json.JSONObject;
import org.kde.kdeconnect.Backends.BaseLink;
import org.kde.kdeconnect.Backends.LanBackend.LanLink;
import org.kde.kdeconnect.Backends.LanBackend.LanLinkProvider;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class LanLinkTest extends AndroidTestCase {

    LanLink lanLink;
    IoSession session;
    Device.SendPackageStatusCallback callback;

    WriteFuture writeFutureSuccess, writeFutureFailure;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().getPath());

        LanLinkProvider linkProvider = Mockito.mock(LanLinkProvider.class);
        Mockito.when(linkProvider.getName()).thenReturn("LanLinkProvider");

        session = Mockito.mock(IoSession.class);
        Mockito.when(session.getId()).thenReturn(12345l);
        Mockito.when(session.getRemoteAddress()).thenReturn(new InetSocketAddress(5000));

        callback = Mockito.mock(Device.SendPackageStatusCallback.class);
        Mockito.doNothing().when(callback).sendSuccess();
        Mockito.doNothing().when(callback).sendProgress(Mockito.any(Integer.class));
        Mockito.doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                throw (Throwable) invocationOnMock.getArguments()[0];
            }
        }).when(callback).sendFailure(Mockito.any(Throwable.class));

        writeFutureSuccess = Mockito.mock(WriteFuture.class);
        Mockito.when(writeFutureSuccess.isWritten()).thenReturn(true);
        Mockito.when(writeFutureSuccess.getSession()).thenReturn(session);
        Mockito.when(writeFutureSuccess.isDone()).thenReturn(true);

        writeFutureFailure = Mockito.mock(WriteFuture.class);
        Mockito.when(writeFutureFailure.isWritten()).thenReturn(false);
        Mockito.when(writeFutureFailure.getSession()).thenReturn(session);
        Mockito.when(writeFutureFailure.isDone()).thenReturn(true);
        Mockito.when(writeFutureFailure.getException()).thenReturn(new RuntimeException());

        lanLink = new LanLink(session, "testDevice", linkProvider, BaseLink.ConnectionStarted.Remotely);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testSendPackageSuccess(){

        NetworkPackage testPackage = Mockito.mock(NetworkPackage.class);
        Mockito.when(testPackage.getType()).thenReturn("kdeconnect.test");
        Mockito.when(testPackage.getBoolean("isTesting")).thenReturn(true);
        Mockito.when(testPackage.getString("testName")).thenReturn("testSendPackageSuccess");
        Mockito.when(testPackage.serialize()).thenReturn("{\"id\":123,\"type\":\"kdeconnect.test\",\"body\":{\"isTesting\":true,\"testName\":\"testSendPackageSuccess\"}}");

        Mockito.when(session.write(testPackage.serialize())).thenReturn(writeFutureSuccess);
        lanLink.sendPackage(testPackage, callback);
    }

    public void testSendPackageFail(){

        NetworkPackage testPackage = Mockito.mock(NetworkPackage.class);
        Mockito.when(testPackage.getType()).thenReturn("kdeconnect.test");
        Mockito.when(testPackage.getBoolean("isTesting")).thenReturn(true);
        Mockito.when(testPackage.getString("testName")).thenReturn("testSendPackageFail");
        Mockito.when(testPackage.serialize()).thenReturn("{\"id\":123,\"type\":\"kdeconnect.test\",\"body\":{\"isTesting\":true,\"testName\":\"testSendPackageFail\"}}");

        Mockito.when(session.write(testPackage.serialize())).thenReturn(writeFutureFailure);
        try {
            lanLink.sendPackage(testPackage, callback);
        }catch (RuntimeException r){
            // Do nothing, test is fine, callback should throw this exception
        }catch (Exception e){
            // This should not happen
            throw e;
        }


    }


    public void testSendPayload() throws Exception{

        class Downloader extends Thread {

            NetworkPackage np;
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            public void setNetworkPackage(NetworkPackage networkPackage){
                this.np = networkPackage;
            }

            public ByteArrayOutputStream getOutputStream(){
                return outputStream;
            }

            @Override
            public void run(){
                try {

                    Socket socket = null;
                    try {
                        socket = new Socket();
                        int tcpPort = np.getPayloadTransferInfo().getInt("port");
                        InetSocketAddress address = (InetSocketAddress)session.getRemoteAddress();
                        socket.connect(new InetSocketAddress(address.getAddress(), tcpPort));
                        np.setPayload(socket.getInputStream(), np.getPayloadSize());
                    } catch (Exception e) {
                        try { socket.close(); } catch(Exception ignored) { throw ignored; }
                        e.printStackTrace();
                        Log.e("KDE/LanLinkTest", "Exception connecting to remote socket");
                        throw e;
                    }

                    final InputStream input = np.getPayload();
                    final long fileLength = np.getPayloadSize();

                    byte data[] = new byte[1024];
                    long progress = 0, prevProgressPercentage = 0;
                    int count;
                    while ((count = input.read(data)) >= 0) {
                        progress += count;
                        outputStream.write(data, 0, count);
                        if (fileLength > 0) {
                            if (progress >= fileLength) break;
                            long progressPercentage = (progress * 100 / fileLength);
                            if (progressPercentage != prevProgressPercentage) {
                                prevProgressPercentage = progressPercentage;
                            }
                        }

                    }
                    outputStream.close();
                    input.close();

                } catch(Exception e) {
                    Log.e("Downloader Test", "Exception");
                    e.printStackTrace();
                }
            }
        }


        final Downloader downloader = new Downloader();

        // Using byte array for payload, try to use input stream as used in real device
        String dataString = "Lorem ipsum dolor sit amet, consectetur adipiscing elit."+
                        " Cras vel erat et ante fringilla tristique. Sed consequat ligula at interdum "+
                        "rhoncus. Integer semper enim felis, id sodales tellus aliquet eget."+
                        " Sed fringilla ac metus eget dictum. Aliquam euismod non sem sit"+
                        " amet dapibus. Interdum et malesuada fames ac ante ipsum primis "+
                        "in faucibus. Nam et ligula placerat, varius justo eu, convallis "+
                        "lorem. Nam consequat consequat tortor et gravida. Praesent "+
                        "ultricies tortor eget ex elementum gravida. Suspendisse aliquet "+
                        "erat a orci feugiat dignissim.";

        // reallyLongString contains dataString 16 times
        String reallyLongString = dataString + dataString;
        reallyLongString = reallyLongString + reallyLongString;
        reallyLongString = reallyLongString + reallyLongString;
        reallyLongString = reallyLongString + reallyLongString;

        final byte[] data = reallyLongString.getBytes();

        final JSONObject sharePackageJson = new JSONObject("{\"id\":123,\"body\":{\"filename\":\"data.txt\"},\"payloadTransferInfo\":{},\"payloadSize\":8720,\"type\":\"kdeconnect.share\"}");

        // Mocking share package
        final NetworkPackage sharePackage = Mockito.mock(NetworkPackage.class);
        Mockito.when(sharePackage.getType()).thenReturn("kdeconnect.share");
        Mockito.when(sharePackage.hasPayload()).thenReturn(true);
        Mockito.when(sharePackage.hasPayloadTransferInfo()).thenReturn(true);
        Mockito.doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                return sharePackageJson.toString();
            }
        }).when(sharePackage).serialize();
        Mockito.when(sharePackage.getPayload()).thenReturn(new ByteArrayInputStream(data));
        Mockito.when(sharePackage.getPayloadSize()).thenReturn((long) data.length);
        Mockito.doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                return sharePackageJson.getJSONObject("payloadTransferInfo");
            }
        }).when(sharePackage).getPayloadTransferInfo();
        Mockito.doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                JSONObject object = (JSONObject)invocationOnMock.getArguments()[0];

                sharePackageJson.put("payloadTransferInfo", object);
                return null;
            }
        }).when(sharePackage).setPayloadTransferInfo(Mockito.any(JSONObject.class));

        Mockito.doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {

                String stringNetworkPackage = (String)invocationOnMock.getArguments()[0];
                final NetworkPackage np = NetworkPackage.unserialize(stringNetworkPackage);

                downloader.setNetworkPackage(np);
                downloader.start();

                return writeFutureSuccess;
            }
        }).when(session).write(Mockito.anyString());

        lanLink.sendPackage(sharePackage, callback);

        try {
            // Wait 1 secs for downloader to finish (if some error, it will continue and assert will fail)
            downloader.join(1*1000);
        }catch (Exception e){
            e.printStackTrace();
            throw e;
        }
        assertEquals(new String(data), new String(downloader.getOutputStream().toByteArray()));

    }
}
