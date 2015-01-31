/*
 * Copyright 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License or (at your option) version 3 or any later version
 * accepted by the membership of KDE e.V. (or its successor approved
 * by the membership of KDE e.V.), which shall act as a proxy
 * defined in Section 14 of version 3 of the license.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
*/

package org.kde.kdeconnect.Plugins.MprisPlugin;

import android.app.Activity;
import android.app.AlertDialog;
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
import java.util.HashMap;

public class MprisPlugin extends Plugin {

    private String player = "";
    private boolean playing = false;
    private String currentSong = "";
    private int volume = 50;
    private long length = -1;
    private long lastPosition;
    private long lastPositionTime;
    private HashMap<String,Handler> playerStatusUpdated = new HashMap<String,Handler>();

    private ArrayList<String> playerList = new ArrayList<String>();
    private HashMap<String,Handler> playerListUpdated = new HashMap<String,Handler>();

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
    public boolean hasSettings() {
        return true;
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public boolean onCreate() {
        requestPlayerList();
        lastPositionTime = System.currentTimeMillis();
        return true;
    }

    @Override
    public void onDestroy() {
        playerList.clear();
    }

    public void sendAction(String player, String action) {
        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_MPRIS);
        np.set("player", player);
        np.set("action", action);
        device.sendPackage(np);
    }
    public void sendAction(String action) {
        sendAction(player, action);
    }

    public void setVolume(int volume) {
        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_MPRIS);
        np.set("player", player);
        np.set("setVolume",volume);
        device.sendPackage(np);
    }

    public void setPosition(int position) {
        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_MPRIS);
        np.set("player", player);
        np.set("SetPosition", position);
        device.sendPackage(np);
        this.lastPosition = position;
        this.lastPositionTime = System.currentTimeMillis();
    }

    public void Seek(int offset) {
        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_MPRIS);
        np.set("player", player);
        np.set("Seek", offset);
        device.sendPackage(np);
    }

    @Override
    public boolean onPackageReceived(NetworkPackage np) {
        if (!np.getType().equals(NetworkPackage.PACKAGE_TYPE_MPRIS)) return false;

        if (np.has("nowPlaying") || np.has("volume") || np.has("isPlaying") || np.has("length") || np.has("pos")) {
            if (np.getString("player").equals(player)) {
                currentSong = np.getString("nowPlaying", currentSong);
                volume = np.getInt("volume", volume);
                length = np.getLong("length", length);
                if(np.has("pos")){
                    lastPosition = np.getLong("pos", lastPosition);
                    lastPositionTime = System.currentTimeMillis();
                }
                playing = np.getBoolean("isPlaying", playing);
                for (String key : playerStatusUpdated.keySet()) {
                    try {
                        playerStatusUpdated.get(key).dispatchMessage(new Message());
                    } catch(Exception e) {
                        e.printStackTrace();
                        Log.e("MprisControl","Exception");
                        playerStatusUpdated.remove(key);
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
                for (String key : playerListUpdated.keySet()) {
                    try {
                        playerListUpdated.get(key).dispatchMessage(new Message());
                    } catch(Exception e) {
                        e.printStackTrace();
                        Log.e("MprisControl","Exception");
                        playerListUpdated.remove(key);
                    }
                }
            }
        }

        return true;
    }

    public void setPlayerStatusUpdatedHandler(String id, Handler h) {
        playerStatusUpdated.put(id, h);
        if (playerListUpdated.size() == 1) {
            requestPlayerStatus();
        }

        h.dispatchMessage(new Message());
    }

    public void setPlayerListUpdatedHandler(String id, Handler h) {
        if (playerList.size() > 0) {
            h.dispatchMessage(new Message());
        }

        playerListUpdated.put(id,h);
        if (playerListUpdated.size() == 1) {
            requestPlayerList();
        }
    }

    public void setPlayer(String player) {
        if (player == null || player.equals(this.player)) return;
        this.player = player;
        currentSong = "";
        volume = 50;
        playing = false;
        for (String key : playerStatusUpdated.keySet()) {
            try {
                playerStatusUpdated.get(key).dispatchMessage(new Message());
            } catch(Exception e) {
                e.printStackTrace();
                Log.e("MprisControl","Exception");
                playerStatusUpdated.remove(key);
            }
        }
        requestPlayerStatus();
    }

    public ArrayList<String> getPlayerList() {
        return playerList;
    }

    public String getCurrentSong() {
        return currentSong;
    }

    public String getPlayer() {
        return player;
    }

    public int getVolume() {
        return volume;
    }

    public long getLength(){ return length; }

    public boolean isPlaying() {
        return playing;
    }

    public long getPosition(){
        if(playing) {
            return lastPosition + (System.currentTimeMillis() - lastPositionTime);
        } else {
            return lastPosition;
        }
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
    public AlertDialog getErrorDialog(Activity deviceActivity) {
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
