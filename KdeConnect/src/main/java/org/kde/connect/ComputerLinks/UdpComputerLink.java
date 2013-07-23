package org.kde.connect.ComputerLinks;

import android.os.AsyncTask;
import android.util.Log;

import org.kde.connect.LinkProviders.AvahiLinkProvider;
import org.kde.connect.NetworkPackage;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UdpComputerLink extends BaseComputerLink {

    DatagramSocket socket = null;
    int UDP_PORT;
    InetAddress IP;

    final int BUFFER_SIZE = 5*1024;

    public UdpComputerLink(AvahiLinkProvider linkProvider, InetAddress ip, int port) throws IOException {
        super(linkProvider);
        UDP_PORT = port;
        IP = ip;
    }

    @Override
    public boolean sendPackage(NetworkPackage np) {
        if (socket == null) startReceivingPackages();
        Log.e("UdpComputerLink","sendPackage");
        final String messageStr = np.serialize();
        Log.e("UdpComputerLink", "aboutToSend");
        int msg_length = messageStr.length();
        byte[] message = messageStr.getBytes();
        DatagramPacket p = new DatagramPacket(message, msg_length, IP, UDP_PORT);
        Log.e("UdpComputerLink", "aboutToSend " + IP + ":" + UDP_PORT);
        try {
            socket.send(p);
        } catch (Exception e) {
            Log.e("UdpComputerLink", "Exception!");
        }
        Log.e("UdpComputerLink", "sent");
        return true;
    }

    public void startReceivingPackages() {
        try {
            socket = new DatagramSocket(UDP_PORT);
        }catch(Exception e) {
            Log.e("UdpComputerLink","Exception");
            e.printStackTrace();
        }
        Log.e("UdpComputerLink","" + IP + ":" + UDP_PORT);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.e("UdpComputerLink","Waiting for udp datagrams");
                    byte[] buffer = new byte[BUFFER_SIZE];
                    while(true){
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length );
                        socket.receive(packet);
                        String s = new String(packet.getData(), 0, packet.getLength());
                        packageReceived(NetworkPackage.unserialize(s));
                    }
                } catch (java.io.IOException e) {
                    Log.e("UdpComputerLink","Exception");
                    e.printStackTrace();
                }
            }
        }).run();
    }
}
