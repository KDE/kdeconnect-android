/*
 * Copyright 2014 Da-Jin Chu <dajinchu@gmail.com>
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

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;
import android.widget.RemoteViews;

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Helpers.NotificationsHelper;
import org.kde.kdeconnect_tp.R;

public class NotificationPanel {

    private static final int notificationId = 182144338; //Random number, fixed id to make sure we don't produce more than one notification

    private String deviceId;
    private String player;

    private NotificationManager nManager;
    private NotificationCompat.Builder nBuilder;
    private RemoteViews remoteView;

    public NotificationPanel(Context context, Device device, String player) {
        this.deviceId = device.getDeviceId();
        this.player = player;

        //FIXME: When the mpris plugin gets destroyed and recreated, we should add this listener again
        final MprisPlugin mpris = (MprisPlugin)device.getPlugin("plugin_mpris");
        if (mpris != null) {
            mpris.setPlayerStatusUpdatedHandler("notification", new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    String song = mpris.getCurrentSong();
                    boolean isPlaying = mpris.isPlaying();
                    updateStatus(song, isPlaying);
                }
            });
        }

        Intent launch = new Intent(context, MprisActivity.class);
        launch.putExtra("deviceId", deviceId);
        launch.putExtra("player", player);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addParentStack(MprisActivity.class);
        stackBuilder.addNextIntent(launch);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        nManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        remoteView = new RemoteViews(context.getPackageName(), R.layout.mpris_notification);
        nBuilder = new NotificationCompat.Builder(context)
                .setContentTitle("KDE Connect")
                .setLocalOnly(true)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(resultPendingIntent)
                .setOngoing(true);

        String deviceName = device.getName();
        String playerOnDevice = context.getString(R.string.mpris_player_on_device, player, deviceName);
        remoteView.setTextViewText(R.id.notification_player, playerOnDevice);

        Intent playpause = new Intent(context, NotificationReturnSlot.class);
        playpause.putExtra("action", "play");
        playpause.putExtra("deviceId", deviceId);
        playpause.putExtra("player", player);
        PendingIntent btn1 = PendingIntent.getBroadcast(context, NotificationsHelper.getUniqueId(), playpause, 0);
        remoteView.setOnClickPendingIntent(R.id.notification_play_pause, btn1);

        Intent next = new Intent(context, NotificationReturnSlot.class);
        next.putExtra("action", "next");
        next.putExtra("deviceId", deviceId);
        next.putExtra("player", player);
        PendingIntent btn2 = PendingIntent.getBroadcast(context, NotificationsHelper.getUniqueId(), next, 0);
        remoteView.setOnClickPendingIntent(R.id.notification_next, btn2);

        Intent prev = new Intent(context, NotificationReturnSlot.class);
        prev.putExtra("action", "prev");
        prev.putExtra("deviceId", deviceId);
        prev.putExtra("player", player);
        PendingIntent btn3 = PendingIntent.getBroadcast(context, NotificationsHelper.getUniqueId(), prev, 0);
        remoteView.setOnClickPendingIntent(R.id.notification_prev, btn3);

        nBuilder.setContent(remoteView);
        nManager.notify(notificationId, nBuilder.build());
    }

    protected void updateStatus(String songName, boolean isPlaying) {
        if (remoteView == null) return;
        remoteView.setTextViewText(R.id.notification_song, songName);
        if (isPlaying) {
            remoteView.setImageViewResource(R.id.notification_play_pause, android.R.drawable.ic_media_pause);
        } else {
            remoteView.setImageViewResource(R.id.notification_play_pause, android.R.drawable.ic_media_play);
        }
        nBuilder.setContent(remoteView);
        nManager.notify(notificationId, nBuilder.build());
    }

    public void dismiss() {
        nManager.cancel(notificationId);
        remoteView = null;
    }

    public String getPlayer() {
        return player;
    }

    public String getDeviceId() {
        return deviceId;
    }
}