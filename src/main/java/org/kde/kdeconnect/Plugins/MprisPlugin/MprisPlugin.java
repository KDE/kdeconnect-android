package org.kde.kdeconnect.Plugins.MprisPlugin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import org.kde.kdeconnect.NetworkPackage;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;


public class MprisPlugin extends Plugin {

    private String currentSong = "";
    private int volume = 50;
    private Handler playerStatusUpdated = null;

    private ArrayList<String> playerList = new ArrayList<String>();
    private Handler playerListUpdated = null;

    private String player = "";
    private boolean playing = false;

    /*static {
        PluginFactory.registerPlugin(MprisPlugin.class);
    }*/

    @Override
    public String getPluginName() {
        return "plugin_mpris";
    }

    @Override
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_mpris);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_mpris_desc);
    }

    @Override
    public Drawable getIcon() {
        return context.getResources().getDrawable(R.drawable.icon);
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public boolean onCreate() {
        requestPlayerList();
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
        device.sendPackage(np);
    }

    public void setVolume(int volume) {
        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_MPRIS);
        np.set("player",player);
        np.set("setVolume",volume);
        device.sendPackage(np);
    }

    public void Seek(int offset) {
        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_MPRIS);
        np.set("player",player);
        np.set("Seek",offset);
        device.sendPackage(np);
    }

    @Override
    public boolean onPackageReceived(NetworkPackage np) {
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
        device.sendPackage(np);
    }


    private void requestPlayerStatus() {
        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_MPRIS);
        np.set("player",player);
        np.set("requestNowPlaying",true);
        np.set("requestVolume",true);
        device.sendPackage(np);
    }

    @Override
    public AlertDialog getErrorDialog(Context baseContext) {
        return null;
    }

    @Override
    public Button getInterfaceButton(final Activity activity) {
        Button b = new Button(activity);
        b.setText(R.string.open_mpris_controls);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(activity, MprisActivity.class);
                intent.putExtra("deviceId", device.getDeviceId());
                activity.startActivity(intent);
            }
        });
        return b;
    }
}
