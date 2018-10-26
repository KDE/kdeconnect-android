/*
 * Copyright 2017 Matthijs Tijink <matthijstijink@gmail.com>
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.v4.media.session.MediaSessionCompat;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;

/**
 * Called when the mpris media notification's buttons are pressed
 */
public class MprisMediaNotificationReceiver extends BroadcastReceiver {
    public static final String ACTION_PLAY = "ACTION_PLAY";
    public static final String ACTION_PAUSE = "ACTION_PAUSE";
    public static final String ACTION_PREVIOUS = "ACTION_PREVIOUS";
    public static final String ACTION_NEXT = "ACTION_NEXT";
    public static final String ACTION_CLOSE_NOTIFICATION = "ACTION_CLOSE_NOTIFICATION";

    public static final String EXTRA_DEVICE_ID = "deviceId";
    public static final String EXTRA_MPRIS_PLAYER = "player";

    @Override
    public void onReceive(Context context, Intent intent) {
        //First case: buttons send by other applications via the media session APIs
        if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
            //Route these buttons to the media session, which will handle them
            MediaSessionCompat mediaSession = MprisMediaSession.getMediaSession();
            if (mediaSession == null) return;
            mediaSession.getController().dispatchMediaButtonEvent(intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT));
        } else {
            //Second case: buttons on the notification, which we created ourselves

            //Get the correct device, the mpris plugin and the mpris player
            BackgroundService service = BackgroundService.getInstance();
            if (service == null) return;
            Device device = service.getDevice(intent.getStringExtra(EXTRA_DEVICE_ID));
            if (device == null) return;
            MprisPlugin mpris = device.getPlugin(MprisPlugin.class);
            if (mpris == null) return;
            MprisPlugin.MprisPlayer player = mpris.getPlayerStatus(intent.getStringExtra(EXTRA_MPRIS_PLAYER));
            if (player == null) return;

            //Forward the action to the player
            switch (intent.getAction()) {
                case ACTION_PLAY:
                    player.play();
                    break;
                case ACTION_PAUSE:
                    player.pause();
                    break;
                case ACTION_PREVIOUS:
                    player.previous();
                    break;
                case ACTION_NEXT:
                    player.next();
                    break;
                case ACTION_CLOSE_NOTIFICATION:
                    //The user dismissed the notification: actually handle its removal correctly
                    MprisMediaSession.getInstance().closeMediaNotification();
            }
        }
    }
}
