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
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;

import org.kde.kdeconnect_tp.R;

public class NotificationPanel {

    String deviceId;

    private MprisActivity parent;
    private NotificationManager nManager;
    private NotificationCompat.Builder nBuilder;
    private RemoteViews remoteView;

    public NotificationPanel(MprisActivity parent, String deviceId) {
        this.parent = parent;
        this.deviceId = deviceId;
        nBuilder = new NotificationCompat.Builder(parent)
                .setContentTitle("Mpris Activity")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true);

        remoteView = new RemoteViews(parent.getPackageName(), R.layout.notification_layout);

        //set the button listeners
        setListeners(remoteView);
        nBuilder.setContent(remoteView);

        nManager = (NotificationManager) parent.getSystemService(Context.NOTIFICATION_SERVICE);
        nManager.notify(2, nBuilder.build());
    }

    public void updateStatus(String songName, boolean isPlaying){
        remoteView.setTextViewText(R.id.notification_song, songName);
        if(isPlaying){
            remoteView.setImageViewResource(R.id.notification_play_pause, android.R.drawable.ic_media_pause);
        }else{
            remoteView.setImageViewResource(R.id.notification_play_pause, android.R.drawable.ic_media_play);
        }
        nBuilder.setContent(remoteView);
        nManager.notify(2,nBuilder.build());
    }

    public void setListeners(RemoteViews view){
        Intent playpause = new Intent(parent,NotificationReturnSlot.class);
        playpause.putExtra("action", "play");
        playpause.putExtra("deviceId",deviceId);
        Log.i("Panel", deviceId);
        PendingIntent btn1 = PendingIntent.getBroadcast(parent, 1, playpause, 0);
        view.setOnClickPendingIntent(R.id.notification_play_pause, btn1);

        Intent next = new Intent(parent, NotificationReturnSlot.class);
        next.putExtra("action", "next");
        next.putExtra("deviceId",deviceId);
        PendingIntent btn2 = PendingIntent.getBroadcast(parent, 2, next, 0);
        view.setOnClickPendingIntent(R.id.notification_next, btn2);

        Intent prev = new Intent(parent, NotificationReturnSlot.class);
        prev.putExtra("action", "prev");
        prev.putExtra("deviceId",deviceId);
        PendingIntent btn3 = PendingIntent.getBroadcast(
                parent, 3, prev, 0);
        view.setOnClickPendingIntent(R.id.notification_prev, btn3);
    }

    public void notificationCancel() {
        nManager.cancel(2);
    }
}