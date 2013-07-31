package org.kde.connect.PackageInterfaces;

import android.R;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.kde.connect.Device;
import org.kde.connect.NetworkPackage;

import java.util.ArrayList;


public class MprisControlPackageInterface extends BasePackageInterface {

    Context context;

    String nowPlaying = "";
    Handler nowPlayingUpdated = null;

    ArrayList<String> playerList = new ArrayList<String>();
    Handler playerListUpdated = null;

    String player = "";

    @Override
    public boolean onCreate(Context ctx) {
        context = ctx;
        return true;
    }

    @Override
    public void onDestroy() {
        playerList.clear();
    }

    public void sendAction(String s) {
        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_MPRIS);
        np.set("action",s);
        np.set("player",player);
        sendPackage(np);
    }

    @Override
    public boolean onDeviceConnected(Device d) {
        requestPlayerList();
        return true;
    }

    @Override
    public boolean onPackageReceived(Device d, NetworkPackage np) {
        if (!np.getType().equals(NetworkPackage.PACKAGE_TYPE_MPRIS)) return false;

        if (np.has("nowPlaying")) {
            nowPlaying = np.getString("nowPlaying");
            if (nowPlayingUpdated != null) {
                try {
                    nowPlayingUpdated.dispatchMessage(new Message());
                } catch(Exception e) {
                    e.printStackTrace();
                    Log.e("MprisControl","Exception");
                    nowPlayingUpdated = null;
                }
            }
        }

        if (np.has("playerList")) {
            playerList = np.getStringList("playerList");
            if (playerListUpdated != null) {
                try {
                    playerListUpdated.dispatchMessage(new Message());
                } catch(Exception e) {
                    e.printStackTrace();
                    Log.e("MprisControl","Exception");
                    playerListUpdated = null;
                }
            }
        }

        return true;
    }

    public void setNowPlayingUpdatedHandler(Handler h) {
        nowPlayingUpdated = h;
        if (!nowPlaying.isEmpty()) h.dispatchMessage(new Message());
        requestNowPlaying();
    }

    public String getNowPlaying() {
        return nowPlaying;
    }

    public void setPlayerListUpdatedHandler(Handler h) {
        playerListUpdated = h;
        if (!playerList.isEmpty()) h.dispatchMessage(new Message());
        requestPlayerList();
    }

    public ArrayList<String> getPlayerList() {
        return playerList;
    }

    public void setPlayer(String s) {
        player = s;

        requestNowPlaying();
    }


    private void requestPlayerList() {
        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_MPRIS);
        np.set("requestPlayerList",true);
        sendPackage(np);
    }


    private void requestNowPlaying() {
        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_MPRIS);
        np.set("player",player);
        np.set("requestNowPlaying",true);
        sendPackage(np);
    }


}
