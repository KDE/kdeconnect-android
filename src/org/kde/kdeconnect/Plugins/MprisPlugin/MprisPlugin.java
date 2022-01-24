/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Plugins.MprisPlugin;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect_tp.R;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@PluginFactory.LoadablePlugin
public class MprisPlugin extends Plugin {
    public class MprisPlayer {
        private String player = "";
        private boolean playing = false;
        private String currentSong = "";
        private String title = "";
        private String artist = "";
        private String album = "";
        private String albumArtUrl = "";
        private String url = "";
        private String loopStatus = "";
        private boolean loopStatusAllowed = false;
        private boolean shuffle = false;
        private boolean shuffleAllowed = false;
        private int volume = 50;
        private long length = -1;
        private long lastPosition = 0;
        private long lastPositionTime;
        private boolean playAllowed = true;
        private boolean pauseAllowed = true;
        private boolean goNextAllowed = true;
        private boolean goPreviousAllowed = true;
        private boolean seekAllowed = true;

        MprisPlayer() {
            lastPositionTime = System.currentTimeMillis();
        }

        public String getCurrentSong() {
            return currentSong;
        }

        public String getTitle() {
            return title;
        }

        public String getArtist() {
            return artist;
        }

        public String getAlbum() {
            return album;
        }

        public String getPlayer() {
            return player;
        }

        boolean isSpotify() {
            return getPlayer().equalsIgnoreCase("spotify");
        }

        public String getLoopStatus() {
            return loopStatus;
        }

        public boolean getShuffle() {
            return shuffle;
        }

        public int getVolume() {
            return volume;
        }

        public long getLength() {
            return length;
        }

        public boolean isPlaying() {
            return playing;
        }

        public boolean isPlayAllowed() {
            return playAllowed;
        }

        public boolean isPauseAllowed() {
            return pauseAllowed;
        }

        public boolean isGoNextAllowed() {
            return goNextAllowed;
        }

        public boolean isGoPreviousAllowed() {
            return goPreviousAllowed;
        }

        public boolean isSeekAllowed() {
            return seekAllowed && getLength() >= 0 && getPosition() >= 0 && !isSpotify();
        }

        public boolean hasAlbumArt() {
            return !albumArtUrl.isEmpty();
        }

        /**
         * Returns the album art (if available). Note that this can return null even if hasAlbumArt() returns true.
         *
         * @return The album art, or null if not available
         */
        public Bitmap getAlbumArt() {
            return AlbumArtCache.getAlbumArt(albumArtUrl, MprisPlugin.this, player);
        }

        //@NonNull
        public String getUrl() {
            return url;
        }

        public boolean isLoopStatusAllowed() {
            return loopStatusAllowed && !isSpotify();
        }

        public boolean isShuffleAllowed() {
            return shuffleAllowed && !isSpotify();
        }

        public boolean isSetVolumeAllowed() {
            return !isSpotify() && (getVolume() > -1);
        }

        public long getPosition() {
            if (playing) {
                return lastPosition + (System.currentTimeMillis() - lastPositionTime);
            } else {
                return lastPosition;
            }
        }

        public void playPause() {
            if (isPauseAllowed() || isPlayAllowed()) {
                MprisPlugin.this.sendCommand(getPlayer(), "action", "PlayPause");
            }
        }

        public void play() {
            if (isPlayAllowed()) {
                MprisPlugin.this.sendCommand(getPlayer(), "action", "Play");
            }
        }

        public void pause() {
            if (isPauseAllowed()) {
                MprisPlugin.this.sendCommand(getPlayer(), "action", "Pause");
            }
        }

        public void stop() {
            MprisPlugin.this.sendCommand(getPlayer(), "action", "Stop");
        }

        public void previous() {
            if (isGoPreviousAllowed()) {
                MprisPlugin.this.sendCommand(getPlayer(), "action", "Previous");
            }
        }

        public void next() {
            if (isGoNextAllowed()) {
                MprisPlugin.this.sendCommand(getPlayer(), "action", "Next");
            }
        }

        public void setLoopStatus(String loopStatus) {
            MprisPlugin.this.sendCommand(getPlayer(), "setLoopStatus", loopStatus);
        }

        public void setShuffle(boolean shuffle) {
            MprisPlugin.this.sendCommand(getPlayer(), "setShuffle", shuffle);
        }

        public void setVolume(int volume) {
            if (isSetVolumeAllowed()) {
                MprisPlugin.this.sendCommand(getPlayer(), "setVolume", volume);
            }
        }

        public void setPosition(int position) {
            if (isSeekAllowed()) {
                MprisPlugin.this.sendCommand(getPlayer(), "SetPosition", position);

                lastPosition = position;
                lastPositionTime = System.currentTimeMillis();
            }
        }

        public void seek(int offset) {
            if (isSeekAllowed()) {
                MprisPlugin.this.sendCommand(getPlayer(), "Seek", offset);
            }
        }
    }

    public final static String DEVICE_ID_KEY = "deviceId";
    private final static String PACKET_TYPE_MPRIS = "kdeconnect.mpris";
    private final static String PACKET_TYPE_MPRIS_REQUEST = "kdeconnect.mpris.request";

    private final ConcurrentHashMap<String, MprisPlayer> players = new ConcurrentHashMap<>();
    private boolean supportAlbumArtPayload = false;
    private final HashMap<String, Handler> playerStatusUpdated = new HashMap<>();
    private final HashMap<String, Handler> playerListUpdated = new HashMap<>();

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
        return ContextCompat.getDrawable(context, R.drawable.mpris_plugin_action_24dp);
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public boolean onCreate() {
        MprisMediaSession.getInstance().onCreate(context.getApplicationContext(), this, device.getDeviceId());

        //Always request the player list so the data is up-to-date
        requestPlayerList();

        AlbumArtCache.initializeDiskCache(context);
        AlbumArtCache.registerPlugin(this);

        return true;
    }

    @Override
    public void onDestroy() {
        players.clear();
        AlbumArtCache.deregisterPlugin(this);
        MprisMediaSession.getInstance().onDestroy(this, device.getDeviceId());
    }

    private void sendCommand(String player, String method, String value) {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MPRIS_REQUEST);
        np.set("player", player);
        np.set(method, value);
        device.sendPacket(np);
    }

    private void sendCommand(String player, String method, boolean value) {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MPRIS_REQUEST);
        np.set("player", player);
        np.set(method, value);
        device.sendPacket(np);
    }

    private void sendCommand(String player, String method, int value) {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MPRIS_REQUEST);
        np.set("player", player);
        np.set(method, value);
        device.sendPacket(np);
    }

    @Override
    public boolean onPacketReceived(NetworkPacket np) {
        if (np.getBoolean("transferringAlbumArt", false)) {
            AlbumArtCache.payloadToDiskCache(np.getString("albumArtUrl"), np.getPayload());
            return true;
        }

        if (np.has("player")) {
            MprisPlayer playerStatus = players.get(np.getString("player"));
            if (playerStatus != null) {
                playerStatus.currentSong = np.getString("nowPlaying", playerStatus.currentSong);
                //Note: title, artist and album will not be available for all desktop clients
                playerStatus.title = np.getString("title", playerStatus.title);
                playerStatus.artist = np.getString("artist", playerStatus.artist);
                playerStatus.album = np.getString("album", playerStatus.album);
                playerStatus.url = np.getString("url", playerStatus.url);
                if (np.has("loopStatus")) {
                    playerStatus.loopStatus = np.getString("loopStatus", playerStatus.loopStatus);
                    playerStatus.loopStatusAllowed = true;
                }
                if (np.has("shuffle")) {
                    playerStatus.shuffle = np.getBoolean("shuffle", playerStatus.shuffle);
                    playerStatus.shuffleAllowed = true;
                }
                playerStatus.volume = np.getInt("volume", playerStatus.volume);
                playerStatus.length = np.getLong("length", playerStatus.length);
                if (np.has("pos")) {
                    playerStatus.lastPosition = np.getLong("pos", playerStatus.lastPosition);
                    playerStatus.lastPositionTime = System.currentTimeMillis();
                }
                playerStatus.playing = np.getBoolean("isPlaying", playerStatus.playing);
                playerStatus.playAllowed = np.getBoolean("canPlay", playerStatus.playAllowed);
                playerStatus.pauseAllowed = np.getBoolean("canPause", playerStatus.pauseAllowed);
                playerStatus.goNextAllowed = np.getBoolean("canGoNext", playerStatus.goNextAllowed);
                playerStatus.goPreviousAllowed = np.getBoolean("canGoPrevious", playerStatus.goPreviousAllowed);
                playerStatus.seekAllowed = np.getBoolean("canSeek", playerStatus.seekAllowed);
                String newAlbumArtUrlstring = np.getString("albumArtUrl", playerStatus.albumArtUrl);
                try {
                    //Turn the url into canonical form (and check its validity)
                    URL newAlbumArtUrl = new URL(newAlbumArtUrlstring);
                    playerStatus.albumArtUrl = newAlbumArtUrl.toString();
                } catch (MalformedURLException ignored) {
                    playerStatus.albumArtUrl = "";
                }

                for (String key : playerStatusUpdated.keySet()) {
                    try {
                        playerStatusUpdated.get(key).dispatchMessage(new Message());
                    } catch (Exception e) {
                        Log.e("MprisControl", "Exception", e);
                        playerStatusUpdated.remove(key);
                    }
                }
            }
        }

        //Remember if the connected device support album art payloads
        supportAlbumArtPayload = np.getBoolean("supportAlbumArtPayload", supportAlbumArtPayload);

        List<String> newPlayerList = np.getStringList("playerList");
        if (newPlayerList != null) {
            boolean equals = true;
            for (String newPlayer : newPlayerList) {
                if (!players.containsKey(newPlayer)) {
                    equals = false;

                    MprisPlayer player = new MprisPlayer();
                    player.player = newPlayer;
                    players.put(newPlayer, player);

                    //Immediately ask for the data of this player
                    requestPlayerStatus(newPlayer);
                }
            }
            Iterator<HashMap.Entry<String, MprisPlayer>> iter = players.entrySet().iterator();
            while (iter.hasNext()) {
                String oldPlayer = iter.next().getKey();
                final boolean found = newPlayerList.stream().anyMatch(newPlayer ->
                        newPlayer.equals(oldPlayer));

                if (!found) {
                    iter.remove();
                    equals = false;
                }
            }
            if (!equals) {
                for (String key : playerListUpdated.keySet()) {
                    try {
                        playerListUpdated.get(key).dispatchMessage(new Message());
                    } catch (Exception e) {
                        Log.e("MprisControl", "Exception", e);
                        playerListUpdated.remove(key);
                    }
                }
            }
        }

        return true;
    }

    @Override
    public String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_MPRIS};
    }

    @Override
    public String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_MPRIS_REQUEST};
    }

    public void setPlayerStatusUpdatedHandler(String id, Handler h) {
        playerStatusUpdated.put(id, h);

        h.dispatchMessage(new Message());
    }

    public void removePlayerStatusUpdatedHandler(String id) {
        playerStatusUpdated.remove(id);
    }

    public void setPlayerListUpdatedHandler(String id, Handler h) {
        playerListUpdated.put(id, h);

        h.dispatchMessage(new Message());
    }

    public void removePlayerListUpdatedHandler(String id) {
        playerListUpdated.remove(id);
    }

    public List<String> getPlayerList() {
        List<String> playerlist = new ArrayList<>(players.keySet());
        Collections.sort(playerlist);
        return playerlist;
    }

    public MprisPlayer getPlayerStatus(String player) {
        if (player == null) {
            return null;
        }
        return players.get(player);
    }

    public MprisPlayer getEmptyPlayer() {
        return new MprisPlayer();
    }

    /**
     * Returns a playing mpris player, if any exist
     *
     * @return null if no players are playing, a playing player otherwise
     */
    public MprisPlayer getPlayingPlayer() {
        return players.values().stream().filter(MprisPlayer::isPlaying).findFirst().orElse(null);
    }

    boolean hasPlayer(MprisPlayer player) {
        if (player == null) {
            return false;
        }
        return players.containsValue(player);
    }

    private void requestPlayerList() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MPRIS_REQUEST);
        np.set("requestPlayerList", true);
        device.sendPacket(np);
    }

    private void requestPlayerStatus(String player) {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MPRIS_REQUEST);
        np.set("player", player);
        np.set("requestNowPlaying", true);
        np.set("requestVolume", true);
        device.sendPacket(np);
    }

    @Override
    public boolean hasMainActivity() {
        return true;
    }

    @Override
    public void startMainActivity(Activity parentActivity) {
        Intent intent = new Intent(parentActivity, MprisActivity.class);
        intent.putExtra("deviceId", device.getDeviceId());
        parentActivity.startActivity(intent);
    }

    @Override
    public String getActionName() {
        return context.getString(R.string.open_mpris_controls);
    }

    public void fetchedAlbumArt(String url) {
        if (players.values().stream().anyMatch(player -> url.equals(player.albumArtUrl))) {
            for (String key : playerStatusUpdated.keySet()) {
                try {
                    playerStatusUpdated.get(key).dispatchMessage(new Message());
                } catch (Exception e) {
                    Log.e("MprisControl", "Exception", e);
                    playerStatusUpdated.remove(key);
                }
            }
        }
    }

    public boolean askTransferAlbumArt(String url, String playerName) {
        //First check if the remote supports transferring album art
        if (!supportAlbumArtPayload) return false;
        if (url.isEmpty()) return false;

        MprisPlayer player = getPlayerStatus(playerName);
        if (player == null) return false;

        if (player.albumArtUrl.equals(url)) {
            NetworkPacket np = new NetworkPacket(PACKET_TYPE_MPRIS_REQUEST);
            np.set("player", player.getPlayer());
            np.set("albumArtUrl", url);
            device.sendPacket(np);
            return true;
        }
        return false;
    }
}
