package org.kde.connect.PackageInterfaces;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.kde.connect.Device;
import org.kde.connect.NetworkPackage;

import java.util.ArrayList;


public class MprisControlPackageInterface extends BasePackageInterface {

    private Context context;

    private String currentSong = "";
    int volume = 50;
    private Handler playerStatusUpdated = null;

    private ArrayList<String> playerList = new ArrayList<String>();
    private Handler playerListUpdated = null;

    private String player = "";
    private boolean playing = false;


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
        np.set("player",player);
        np.set("action",s);
        sendPackage(np);
    }

    public void setVolume(int volume) {
        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_MPRIS);
        np.set("player",player);
        np.set("setVolume",volume);
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

        if (np.has("nowPlaying") || np.has("volume") || np.has("isPlaying")) {
            if (np.getString("player").equals(player)) {
                currentSong = np.getString("nowPlaying", currentSong);
                volume = np.getInt("volume", volume);
                playing = np.getBoolean("isPlaying", playing);
                if (playerStatusUpdated != null) {
                    try {
                        playerStatusUpdated.dispatchMessage(new Message());
                    } catch(Exception e) {
                        e.printStackTrace();
                        Log.e("MprisControl","Exception");
                        playerStatusUpdated = null;
                    }
                }
            }
        }

        if (np.has("playerList")) {

            ArrayList<String> newPlayerList = np.getStringList("playerList");
            boolean equals = false;
            if (newPlayerList.size() == playerList.size()) {
                equals = true;
                for (int i=0; i<newPlayerList.size(); i++) {
                    if (!newPlayerList.get(i).equals(playerList.get(i))) {
                        equals = false;
                        break;
                    }
                }
            }
            if (!equals) {
                playerList = newPlayerList;
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
        }

        return true;
    }

    public void setPlayerStatusUpdatedHandler(Handler h) {
        playerStatusUpdated = h;
        if (currentSong.length() > 0) h.dispatchMessage(new Message());
        requestPlayerStatus();
    }

    public String getCurrentSong() {
        return currentSong;
    }

    public void setPlayerListUpdatedHandler(Handler h) {
        playerListUpdated = h;
        if (playerList.size() > 0) h.dispatchMessage(new Message());
        requestPlayerList();
    }

    public ArrayList<String> getPlayerList() {
        return playerList;
    }

    public void setPlayer(String s) {
        player = s;
        currentSong = "";
        volume = 50;
        playing = false;
        if (playerStatusUpdated != null) {
            try {
                playerStatusUpdated.dispatchMessage(new Message());
            } catch(Exception e) {
                e.printStackTrace();
                Log.e("MprisControl","Exception");
                playerStatusUpdated = null;
            }
        }
        requestPlayerStatus();
    }

    public int getVolume() {
        return volume;
    }

    public boolean isPlaying() {
        return playing;
    }

    private void requestPlayerList() {
        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_MPRIS);
        np.set("requestPlayerList",true);
        sendPackage(np);
    }


    private void requestPlayerStatus() {
        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_MPRIS);
        np.set("player",player);
        np.set("requestNowPlaying",true);
        np.set("requestVolume",true);
        sendPackage(np);
    }


}
