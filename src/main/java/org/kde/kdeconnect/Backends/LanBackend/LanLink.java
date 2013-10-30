package org.kde.kdeconnect.Backends.LanBackend;

import android.util.Log;

import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.session.IoSession;
import org.json.JSONObject;
import org.kde.kdeconnect.Backends.BaseLink;
import org.kde.kdeconnect.Backends.BaseLinkProvider;
import org.kde.kdeconnect.NetworkPackage;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.PublicKey;

public class LanLink extends BaseLink {

    private IoSession session = null;

    public void disconnect() {
        if (session == null) return;
        //Log.i("LanLink", "Disconnect: "+session.getRemoteAddress().toString());
        session.close(true);
    }

    public LanLink(IoSession session, String deviceId, BaseLinkProvider linkProvider) {
        super(deviceId, linkProvider);
        this.session = session;
    }

    private Thread sendPayload(NetworkPackage np) {

        try {

            final InputStream stream = np.getPayload();

            ServerSocket candidateServer = null;
            boolean success = false;
            int tcpPort = 1739;
            while(!success) {
                try {
                    candidateServer = new ServerSocket();
                    candidateServer.bind(new InetSocketAddress(tcpPort));
                    success = true;
                } catch(Exception e) {
                    Log.e("LanLink", "Exception openning serversocket: "+e);
                    tcpPort++;
                    if (tcpPort >= 1764) {
                        Log.e("LanLink", "No more ports available");
                        return null;
                    }
                }
            }
            JSONObject payloadTransferInfo = new JSONObject();
            payloadTransferInfo.put("port", tcpPort);

            final ServerSocket server = candidateServer;
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    //TODO: Timeout when waiting for a connection and close the socket
                    OutputStream socket = null;
                    try {
                        socket = server.accept().getOutputStream();
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        Log.e("LanLink","Beginning to send payload");
                        while ((bytesRead = stream.read(buffer)) != -1) {
                            //Log.e("ok",""+bytesRead);
                            socket.write(buffer, 0, bytesRead);
                        }
                        Log.e("LanLink","Finished sending payload");
                    } catch(Exception e) {
                        e.printStackTrace();
                        Log.e("LanLink", "Exception with payload upload socket");
                    } finally {
                        if (socket != null) {
                            try { socket.close(); } catch(Exception e) { }
                        }
                        try { server.close(); } catch(Exception e) { }
                    }
                }
            });
            thread.start();

            np.setPayloadTransferInfo(payloadTransferInfo);

            return thread;

        } catch(Exception e) {

            e.printStackTrace();
            Log.e("LanLink", "Exception with payload upload socket");

            return null;
        }

    }

    //Blocking, do not call from main thread
    @Override
    public boolean sendPackage(final NetworkPackage np) {

        if (session == null) {
            Log.e("LanLink", "sendPackage failed: not yet connected");
            return false;
        }

        try {
            Thread thread = null;
            if (np.hasPayload()) {
                thread = sendPayload(np);
                if (thread == null) return false;
            }

            WriteFuture future = session.write(np.serialize());
            future.awaitUninterruptibly();
            if (!future.isWritten()) return false;

            if (thread != null) {
                thread.join(); //Wait for thread to finish
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("LanLink", "sendPackage exception");
            return false;
        }

    }

    //Blocking, do not call from main thread
    @Override
    public boolean sendPackageEncrypted(NetworkPackage np, PublicKey key) {

        if (session == null) {
            Log.e("LanLink", "sendPackage failed: not yet connected");
            return false;
        }

        try {

            Thread thread = null;
            if (np.hasPayload()) {
                thread = sendPayload(np);
                if (thread == null) return false;
            }

            np = np.encrypt(key);
            WriteFuture future = session.write(np.serialize());
            if (!future.await().isWritten()) return false;

            if (thread != null) {
                thread.join(); //Wait for thread to finish
            }

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            Log.e("LanLink", "sendPackageEncrypted exception");
            return false;
        }

    }

    public void injectNetworkPackage(NetworkPackage np) {

        if (np.getType().equals(NetworkPackage.PACKAGE_TYPE_ENCRYPTED)) {

            try {
                np = np.decrypt(privateKey);
            } catch(Exception e) {
                e.printStackTrace();
                Log.e("onPackageReceived","Exception reading the key needed to decrypt the package");
            }

        }

        if (np.hasPayloadTransferInfo()) {

            try {
                Socket socket = new Socket();
                int tcpPort = np.getPayloadTransferInfo().getInt("port");
                InetSocketAddress address = (InetSocketAddress)session.getRemoteAddress();
                socket.connect(new InetSocketAddress(address.getAddress(), tcpPort));
                np.setPayload(socket.getInputStream(), np.getPayloadSize());
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("LanLink", "Exception connecting to payload remote socket");
            }

        }

        packageReceived(np);
    }
}
