package org.kde.connect.ComputerLinks;

import android.os.AsyncTask;
import android.util.Log;

import org.kde.connect.LinkProviders.BaseLinkProvider;
import org.kde.connect.NetworkPackage;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Iterator;

public class TcpComputerLink extends BaseComputerLink {

    private boolean listenign = false;
    private SocketChannel sChannel;
    private Charset charset = Charset.forName("US-ASCII");

    public TcpComputerLink(BaseLinkProvider linkProvider, InetAddress ip, int port) throws java.io.IOException {
        super(linkProvider);

        sChannel = SocketChannel.open();
        sChannel.connect(new InetSocketAddress(ip, port));
        sChannel.configureBlocking(false);

    }

    @Override
    public boolean sendPackage(NetworkPackage np) {
        Log.e("TcpComputerLink","sendPackage");
        try {
            String s = np.serialize();
            Log.e("SendPackage",s);

            CharsetEncoder enc = charset.newEncoder();
            ByteBuffer bytes = enc.encode(CharBuffer.wrap(s));
            Log.e("asdads",""+bytes.array().length);
            int written = sChannel.write(bytes);

            Log.e("SendPackage","sent "+written+" bytes");
        } catch(Exception e) {
            Log.e("TcpComputerLink","Exception");
            e.printStackTrace();
        }
        return true;
    }

    public void startReceivingPackages() {
        if (listenign) return;

        listenign = true;

        try {

            /*
            Log.e("TcpComputerLink","start receiving packages");
            InputStreamReader reader =  new InputStreamReader(socket.getInputStream());
            Log.e("TcpComputerLink","Entering loop");

            while (socket.isConnected()) {
                if (serverMessage == null) {
                    //wait(100);
                    continue;
                }
                NetworkPackage np = NetworkPackage.unserialize(serverMessage);
                Log.e("TcpComputerLink",serverMessage);
                packageReceived(np);
            }*/


            final Selector selector = Selector.open();
            sChannel.register(selector, SelectionKey.OP_CONNECT + SelectionKey.OP_READ);

            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        try {
                            selector.select(); //blocking
                            Iterator it = selector.selectedKeys().iterator();
                            // Process each key at a time
                            while (it.hasNext()) {
                                // Get the selection key
                                SelectionKey selKey = (SelectionKey)it.next();

                                // Remove it from the list to indicate that it is being processed
                                it.remove();

                                if (selKey.isValid() && selKey.isConnectable()) {
                                    // Get channel with connection request
                                    SocketChannel sChannel = (SocketChannel)selKey.channel();

                                    boolean success = sChannel.finishConnect();
                                    if (!success) {
                                        // An error occurred; handle it

                                        // Unregister the channel with this selector
                                        selKey.cancel();
                                    }
                                }
                                if (selKey.isValid() && selKey.isReadable()) {
                                    // Get channel with bytes to read

                                    SocketChannel sChannel = (SocketChannel)selKey.channel();

                                    ByteBuffer buffer = ByteBuffer.allocate(4096);
                                    int read = sChannel.read(buffer);
                                    //TODO: Check if there is more to read (or we have read more than one package)
                                    String s = new String( buffer.array(), 0, read, charset );
                                    Log.e("readable","Read "+read+" bytes: "+s);

                                    NetworkPackage np = NetworkPackage.unserialize(s);
                                    packageReceived(np);

                                }
                            }
                        } catch (Exception e) {
                            Log.e("TcpComputerLink","Inner loop exception");
                            e.printStackTrace();
                            listenign = false;
                            break;
                        }
                    }
                    Log.e("TcpComputerLink","Exiting loop");
                }
            }).run();

        } catch (Exception e) {
            Log.e("TcpComputerLink","Exception");
            listenign = false;
            e.printStackTrace();
        }

    }
}
