package org.kde.connect;

import android.os.AsyncTask;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class DesktopCommunication {

    final int UDP_PORT = 10600;
    final String IP = "192.168.1.48";

    public void asyncSend(final String messageStr) {
        new AsyncTask<Void,Void,Void>(){
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    int server_port = UDP_PORT;
                    DatagramSocket s = new DatagramSocket();
                    InetAddress local = InetAddress.getByName(IP);
                    int msg_length = messageStr.length();
                    byte[] message = messageStr.getBytes();
                    DatagramPacket p = new DatagramPacket(message, msg_length,local,server_port);
                    s.send(p);
                    Log.e("Sent", messageStr);
                } catch(Exception e) {
                    e.printStackTrace();
                }
                return null;
            }
        }.execute();
    }


}
