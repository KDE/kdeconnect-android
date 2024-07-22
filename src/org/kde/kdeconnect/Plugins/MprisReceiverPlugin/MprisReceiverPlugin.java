/*
 * SPDX-FileCopyrightText: 2018 Nicolas Fella <nicolas.fella@gmx.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.MprisReceiverPlugin;

import android.content.ComponentName;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import org.apache.commons.lang3.StringUtils;
import org.kde.kdeconnect.Helpers.AppsHelper;
import org.kde.kdeconnect.Helpers.ThreadHelper;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.NotificationsPlugin.NotificationReceiver;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect.UserInterface.MainActivity;
import org.kde.kdeconnect.UserInterface.StartActivityAlertDialogFragment;
import org.kde.kdeconnect_tp.R;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@PluginFactory.LoadablePlugin
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
public class MprisReceiverPlugin extends Plugin {
    private final static String PACKET_TYPE_MPRIS = "kdeconnect.mpris";
    private final static String PACKET_TYPE_MPRIS_REQUEST = "kdeconnect.mpris.request";

    private static final String TAG = "MprisReceiver";

    private HashMap<String, MprisReceiverPlayer> players;
    private HashMap<String, MprisReceiverCallback> playerCbs;
    private MediaSessionChangeListener mediaSessionChangeListener;

    public @NonNull String getDeviceId() {
        return device.getDeviceId();
    }

    @Override
    public boolean onCreate() {

        if (!hasPermission())
            return false;
        players = new HashMap<>();
        playerCbs = new HashMap<>();
        try {
            MediaSessionManager manager = ContextCompat.getSystemService(context, MediaSessionManager.class);
            if (null == manager)
                return false;

            assert(mediaSessionChangeListener == null);
            mediaSessionChangeListener = new MediaSessionChangeListener();
            manager.addOnActiveSessionsChangedListener(mediaSessionChangeListener, new ComponentName(context, NotificationReceiver.class), new Handler(Looper.getMainLooper()));

            createPlayers(manager.getActiveSessions(new ComponentName(context, NotificationReceiver.class)));
            sendPlayerList();
        } catch (Exception e) {
            Log.e(TAG, "Exception", e);
        }

        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        MediaSessionManager manager = ContextCompat.getSystemService(context, MediaSessionManager.class);
        if (manager != null && mediaSessionChangeListener != null) {
            manager.removeOnActiveSessionsChangedListener(mediaSessionChangeListener);
            mediaSessionChangeListener = null;
        }
    }

    private void createPlayers(List<MediaController> sessions) {
        for (MediaController controller : sessions) {
            createPlayer(controller);
        }
    }

    @Override
    public @NonNull String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_mprisreceiver);
    }

    @Override
    public @NonNull String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_mprisreceiver_desc);
    }

    @Override
    public boolean onPacketReceived(@NonNull NetworkPacket np) {
        if (np.getBoolean("requestPlayerList")) {
            sendPlayerList();
            return true;
        }

        if (!np.has("player")) {
            return false;
        }
        MprisReceiverPlayer player = players.get(np.getString("player"));

        if (null == player) {
            return false;
        }
        String artUrl = np.getString("albumArtUrl", "");
        if (!artUrl.isEmpty()) {
            String playerName = player.getName();
            MprisReceiverCallback cb = playerCbs.get(playerName);
            if (cb == null) {
                Log.e(TAG, "no callback for " + playerName + " (player likely stopped)");
                return false;
            }
            // run it on a different thread to avoid blocking
            ThreadHelper.execute(() -> sendAlbumArt(playerName, cb, artUrl));
            return true;
        }

        if (np.getBoolean("requestNowPlaying", false)) {
            sendMetadata(player);
            return true;
        }

        if (np.has("SetPosition")) {
            long position = np.getLong("SetPosition", 0);
            player.setPosition(position);
        }

        if (np.has("setVolume")) {
            int volume = np.getInt("setVolume", 100);
            player.setVolume(volume);
            //Setting volume doesn't seem to always trigger the callback
            sendMetadata(player);
        }

        if (np.has("action")) {
            String action = np.getString("action");

            switch (action) {
                case "Play":
                    player.play();
                    break;
                case "Pause":
                    player.pause();
                    break;
                case "PlayPause":
                    player.playPause();
                    break;
                case "Next":
                    player.next();
                    break;
                case "Previous":
                    player.previous();
                    break;
                case "Stop":
                    player.stop();
                    break;
            }
        }

        return true;
    }

    @Override
    public @NonNull String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_MPRIS_REQUEST};
    }

    @Override
    public @NonNull String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_MPRIS};
    }

    private final class MediaSessionChangeListener implements MediaSessionManager.OnActiveSessionsChangedListener {
        @Override
        public void onActiveSessionsChanged(@Nullable List<MediaController> controllers) {

            if (null == controllers) {
                return;
            }
            for (MprisReceiverPlayer p : players.values()) {
                p.getController().unregisterCallback(Objects.requireNonNull(playerCbs.get(p.getName())));
            }
            playerCbs.clear();
            players.clear();

            createPlayers(controllers);
            sendPlayerList();

        }
    }

    private void createPlayer(MediaController controller) {
        // Skip the media session we created ourselves as KDE Connect
        if (controller.getPackageName().equals(context.getPackageName())) return;

        MprisReceiverPlayer player = new MprisReceiverPlayer(controller, AppsHelper.appNameLookup(context, controller.getPackageName()));
        MprisReceiverCallback cb = new MprisReceiverCallback(this, player);
        controller.registerCallback(cb, new Handler(Looper.getMainLooper()));
        playerCbs.put(player.getName(), cb);
        players.put(player.getName(), player);
    }

    private void sendPlayerList() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MPRIS);
        np.set("playerList", players.keySet());
        np.set("supportAlbumArtPayload", true);
        getDevice().sendPacket(np);
    }

    @Override
    public int getMinSdk() {
        return Build.VERSION_CODES.LOLLIPOP_MR1;
    }

    void sendAlbumArt(String playerName, @NonNull MprisReceiverCallback cb, @Nullable String requestedUrl) {
        // NOTE: It is possible that the player gets killed in the middle of this method.
        // The proper thing to do this case would be to abort the send - but that gets into the
        //   territory of async cancellation or putting a lock.
        // For now, we just continue to send the art- cb stores the bitmap, so it will be valid.
        //   cb will get GC'd after this method completes.
        String localArtUrl = cb.getArtUrl();
        if (localArtUrl == null) {
            Log.w(TAG, "art not found!");
            return;
        }
        String artUrl = requestedUrl == null ? localArtUrl : requestedUrl;
        if (requestedUrl != null && !requestedUrl.contentEquals(localArtUrl)) {
            Log.w(TAG, "sendAlbumArt: Doesn't match current url");
            Log.d(TAG, "current:   " + localArtUrl);
            Log.d(TAG, "requested: " + requestedUrl);
            return;
        }
        byte[] p = cb.getArtAsArray();
        if (p == null) {
            Log.w(TAG, "sendAlbumArt: Failed to get art stream");
            return;
        }
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MPRIS);
        np.setPayload(new NetworkPacket.Payload(p));
        np.set("player", playerName);
        np.set("transferringAlbumArt", true);
        np.set("albumArtUrl", artUrl);
        getDevice().sendPacket(np);
    }

    void sendMetadata(MprisReceiverPlayer player) {
        NetworkPacket np = new NetworkPacket(MprisReceiverPlugin.PACKET_TYPE_MPRIS);
        np.set("player", player.getName());
        np.set("title", player.getTitle());
        np.set("artist", player.getArtist());
        String nowPlaying = Stream.of(player.getArtist(), player.getTitle())
            .filter(StringUtils::isNotEmpty).collect(Collectors.joining(" - "));
        np.set("nowPlaying", nowPlaying); // GSConnect 50 (so, Ubuntu 22.04) needs this
        np.set("album", player.getAlbum());
        np.set("isPlaying", player.isPlaying());
        np.set("pos", player.getPosition());
        np.set("length", player.getLength());
        np.set("canPlay", player.canPlay());
        np.set("canPause", player.canPause());
        np.set("canGoPrevious", player.canGoPrevious());
        np.set("canGoNext", player.canGoNext());
        np.set("canSeek", player.canSeek());
        np.set("volume", player.getVolume());
        MprisReceiverCallback cb = playerCbs.get(player.getName());
        assert cb != null;
        String artUrl = cb.getArtUrl();
        if (artUrl != null) {
            np.set("albumArtUrl", artUrl);
            Log.v(TAG, "Sending metadata with url " + artUrl);
        } else {
            Log.v(TAG, "Sending metadata without url ");
        }
        getDevice().sendPacket(np);
    }

    @Override
    public boolean checkRequiredPermissions() {
        //Notifications use a different kind of permission, because it was added before the current runtime permissions model
        return hasPermission();
    }

    @Override
    public @NonNull DialogFragment getPermissionExplanationDialog() {
        return new StartActivityAlertDialogFragment.Builder()
                .setTitle(R.string.pref_plugin_mpris)
                .setMessage(R.string.no_permission_mprisreceiver)
                .setPositiveButton(R.string.open_settings)
                .setNegativeButton(R.string.cancel)
                .setIntentAction("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                .setStartForResult(true)
                .setRequestCode(MainActivity.RESULT_NEEDS_RELOAD)
                .create();
    }

    private boolean hasPermission() {
        String notificationListenerList = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        return StringUtils.contains(notificationListenerList, context.getPackageName());
    }

}
