package org.kde.connect;

import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;

import org.kde.connect.Receivers.BaseReceiver;
import org.kde.connect.Receivers.PhoneCallReceiver;
import org.kde.connect.Types.NetworkPackage;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;

public class BackgroundService extends Service {

    ArrayList<BaseReceiver> receivers = new ArrayList<BaseReceiver>();
    DesktopCommunication dc = new DesktopCommunication();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e("BackgroundService","Starting");

        SharedPreferences settings = getSharedPreferences("KdeConnect", 0);

        if (settings.getBoolean("listenCalls", true)) {
            receivers.add(new PhoneCallReceiver(getApplicationContext(), dc));
        }

        NetworkPackage p = new NetworkPackage(System.currentTimeMillis());
        p.setType(NetworkPackage.Type.PING);
        dc.asyncSend(p.toString());

        return Service.START_STICKY;
    }

    @Override
    public void onCreate() {
        Log.e("BackgroundService","Creating");
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        Log.e("BackgroundService","Destroying");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.e("BackgroundService", "Binding");
        return null;
    }

}
