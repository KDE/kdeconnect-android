package org.kde.connect.ComputerLinks;

import android.os.AsyncTask;
import android.util.Log;

import org.kde.connect.Types.NetworkPackage;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UdpComputerLink extends BaseComputerLink {

    int UDP_PORT;
    InetAddress IP;

    final int BUFFER_SIZE = 5*1024;

    public UdpComputerLink(InetAddress ip, int port) {
        UDP_PORT = port;
        IP = ip;

        Log.e("UdpComputerLink","" + ip + ":" + UDP_PORT);
/*
        new AsyncTask<Void,Void,Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    Log.e("UdpComputerLink","Waiting for udp datagrams");
                    byte[] buffer = new byte[BUFFER_SIZE];
                    DatagramSocket socket = new DatagramSocket(UDP_PORT);
                    while(true){
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length );
                        socket.receive(packet);
                        String s = new String(packet.getData(), 0, packet.getLength());
                        packageReceived(NetworkPackage.fromString(s));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }
        }.execute();
*/
    }

    @Override
    public void sendPackage(NetworkPackage np) {
        Log.e("UdpComputerLink","sendPackage");
        final String messageStr = np.toString();
        new AsyncTask<Void,Void,Void>(){
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    Log.e("UdpComputerLink","A");
                    DatagramSocket s = new DatagramSocket();
                    int msg_length = messageStr.length();
                    byte[] message = messageStr.getBytes();
                    Log.e("UdpComputerLink","B");
                    DatagramPacket p = new DatagramPacket(message, msg_length,IP,UDP_PORT);
                    Log.e("UdpComputerLink","C");
                    s.send(p);
                    Log.e("UdpComputerLink","D");
                    Log.e("Sent", messageStr);
                } catch(Exception e) {
                    e.printStackTrace();
                }
                return null;
            }
        }.execute();
    }
}
