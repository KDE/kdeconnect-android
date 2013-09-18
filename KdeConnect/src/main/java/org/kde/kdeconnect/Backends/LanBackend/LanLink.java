package org.kde.kdeconnect.Backends.LanBackend;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.future.IoFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.LineDelimiter;
import org.apache.mina.filter.codec.textline.TextLineCodecFactory;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.json.JSONObject;
import org.kde.kdeconnect.Backends.BaseLink;
import org.kde.kdeconnect.Backends.BaseLinkProvider;
import org.kde.kdeconnect.NetworkPackage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;

public class LanLink extends BaseLink {

    private IoSession session = null;

    public void disconnect() {
        Log.i("LanLink","Disconnect: "+session.getRemoteAddress().toString());
        session.close(true);
    }

    public LanLink(IoSession session, String deviceId, BaseLinkProvider linkProvider) {
        super(deviceId, linkProvider);
        this.session = session;
    }

    private JSONObject sendPayload(final InputStream stream) {

        try {

            final ServerSocket server = new ServerSocket();
            boolean success = false;
            int tcpPort = 1764;
            while(!success) {
                try {
                    server.bind(new InetSocketAddress(tcpPort));
                    success = true;
                } catch(Exception e) {
                    tcpPort++;
                }
            }

            JSONObject payloadTransferInfo = new JSONObject();
            payloadTransferInfo.put("port", tcpPort);

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        OutputStream socket = server.accept().getOutputStream();
                        byte[] buffer = new byte[2048];
                        int bytesRead;
                        Log.e("LanLink","Beginning to send payload");
                        while ((bytesRead = stream.read(buffer)) != -1) {
                            Log.e("ok",""+bytesRead);
                            socket.write(buffer, 0, bytesRead);
                        }
                        Log.e("LanLink","Finished sending payload");
                    } catch(Exception e) {
                        e.printStackTrace();
                        Log.e("LanLink", "Exception with payload upload socket");
                    }
                }
            }).start();

            return payloadTransferInfo;

        } catch(Exception e) {

            e.printStackTrace();
            Log.e("LanLink", "Exception with payload upload socket");

            return null;
        }

    }
    @Override
    public boolean sendPackage(final NetworkPackage np) {
        if (session == null) {
            Log.e("LanLink", "sendPackage failed: not yet connected");
            return false;
        }

        if (np.hasPayload()) {
            JSONObject transferInfo = sendPayload(np.getPayload());
            np.setPayloadTransferInfo(transferInfo);
        }

        session.write(np.serialize());

        return true;
    }

    @Override
    public boolean sendPackageEncrypted(NetworkPackage np, PublicKey key) {
        if (session == null) {
            Log.e("LanLink","sendPackage failed: not yet connected");
            return false;
        }

        try {

            if (np.hasPayload()) {
                JSONObject transferInfo = sendPayload(np.getPayload());
                np.setPayloadTransferInfo(transferInfo);
            }

            np.encrypt(key);

            session.write(np.serialize());

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            Log.e("LanLink", "Encryption exception");
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
                np.setPayload(socket.getInputStream());
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("LanLink", "Exception connecting to payload remote socket");
            }

        }

        packageReceived(np);
    }
}
