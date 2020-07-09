/*
 * Copyright 2018 Nicolas Fella <nicolas.fella@gmx.de>
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

package org.kde.kdeconnect.Plugins.MprisReceiverPlugin;

import android.content.ComponentName;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import org.apache.commons.lang3.StringUtils;
import org.kde.kdeconnect.Helpers.AppsHelper;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.NotificationsPlugin.NotificationReceiver;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect.UserInterface.MainActivity;
import org.kde.kdeconnect.UserInterface.StartActivityAlertDialogFragment;
import org.kde.kdeconnect_tp.R;

import java.util.HashMap;
import java.util.List;

@PluginFactory.LoadablePlugin
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
public class MprisReceiverPlugin extends Plugin {
    private final static String PACKET_TYPE_MPRIS = "kdeconnect.mpris";
    private final static String PACKET_TYPE_MPRIS_REQUEST = "kdeconnect.mpris.request";

    private static final String TAG = "MprisReceiver";

    private HashMap<String, MprisReceiverPlayer> players;
    private MediaSessionChangeListener mediaSessionChangeListener;

    @Override
    public boolean onCreate() {

        if (!hasPermission())
            return false;

        players = new HashMap<>();
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
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_mprisreceiver);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_mprisreceiver_desc);
    }

    @Override
    public boolean onPacketReceived(NetworkPacket np) {

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
    public String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_MPRIS_REQUEST};
    }

    @Override
    public String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_MPRIS};
    }

    private final class MediaSessionChangeListener implements MediaSessionManager.OnActiveSessionsChangedListener {
        @Override
        public void onActiveSessionsChanged(@Nullable List<MediaController> controllers) {

            if (null == controllers) {
                return;
            }

            players.clear();

            createPlayers(controllers);
            sendPlayerList();

        }
    }

    private void createPlayer(MediaController controller) {
        // Skip the media session we created ourselves as KDE Connect
        if (controller.getPackageName().equals(context.getPackageName())) return;

        MprisReceiverPlayer player = new MprisReceiverPlayer(controller, AppsHelper.appNameLookup(context, controller.getPackageName()));
        controller.registerCallback(new MprisReceiverCallback(this, player), new Handler(Looper.getMainLooper()));
        players.put(player.getName(), player);
    }

    private void sendPlayerList() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MPRIS);
        np.set("playerList", players.keySet());
        device.sendPacket(np);
    }

    @Override
    public int getMinSdk() {
        return Build.VERSION_CODES.LOLLIPOP_MR1;
    }

    void sendMetadata(MprisReceiverPlayer player) {
        NetworkPacket np = new NetworkPacket(MprisReceiverPlugin.PACKET_TYPE_MPRIS);
        np.set("player", player.getName());
        if (player.getArtist().isEmpty()) {
            np.set("nowPlaying", player.getTitle());
        } else {
            np.set("nowPlaying", player.getArtist() + " - " + player.getTitle());
        }
        np.set("title", player.getTitle());
        np.set("artist", player.getArtist());
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
        device.sendPacket(np);
    }

    @Override
    public boolean checkRequiredPermissions() {
        //Notifications use a different kind of permission, because it was added before the current runtime permissions model
        return hasPermission();
    }

    @Override
    public DialogFragment getPermissionExplanationDialog() {
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
