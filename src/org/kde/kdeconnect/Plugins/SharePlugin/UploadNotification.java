/*
 * Copyright 2019 Erik Duisters <e.duisters1@gmail.com>
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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package org.kde.kdeconnect.Plugins.SharePlugin;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Helpers.NotificationHelper;
import org.kde.kdeconnect_tp.R;

class UploadNotification {
    private final NotificationManager notificationManager;
    private NotificationCompat.Builder builder;
    private final int notificationId;
    private final Device device;
    private long jobId;

    UploadNotification(Device device, long jobId) {
        this.device = device;
        this.jobId = jobId;

        notificationId = (int) System.currentTimeMillis();
        notificationManager = ContextCompat.getSystemService(device.getContext(), NotificationManager.class);
        builder = new NotificationCompat.Builder(device.getContext(), NotificationHelper.Channels.FILETRANSFER)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setAutoCancel(true)
                .setOngoing(true)
                .setProgress(100, 0, true);
        addCancelAction();
    }

    void addCancelAction() {
        Intent cancelIntent = new Intent(device.getContext(), ShareBroadcastReceiver.class);
        cancelIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        cancelIntent.setAction(SharePlugin.ACTION_CANCEL_SHARE);
        cancelIntent.putExtra(SharePlugin.CANCEL_SHARE_BACKGROUND_JOB_ID_EXTRA, jobId);
        cancelIntent.putExtra(SharePlugin.CANCEL_SHARE_DEVICE_ID_EXTRA, device.getDeviceId());
        PendingIntent cancelPendingIntent = PendingIntent.getBroadcast(device.getContext(), 0, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        builder.addAction(R.drawable.ic_reject_pairing_24dp, device.getContext().getString(R.string.cancel), cancelPendingIntent);
    }

    public void setTitle(String title) {
        builder.setContentTitle(title);
        builder.setTicker(title);
    }

    public void setProgress(int progress, String progressMessage) {
        builder.setProgress( 100, progress, false);
        builder.setContentText(progressMessage);
        builder.setStyle(new NotificationCompat.BigTextStyle().bigText(progressMessage));
    }

    public void setFinished(String message) {
        builder = new NotificationCompat.Builder(device.getContext(), NotificationHelper.Channels.FILETRANSFER);
        builder.setContentTitle(message)
                .setTicker(message)
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setAutoCancel(true)
                .setOngoing(false);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(device.getContext());
        if (prefs.getBoolean("share_notification_preference", true)) {
            builder.setDefaults(Notification.DEFAULT_ALL);
        }
    }

    public void setFailed(String message) {
        setFinished(message);
        builder.setSmallIcon(android.R.drawable.stat_notify_error);
    }

    public void cancel() {
        notificationManager.cancel(notificationId);
    }

    void show() {
        NotificationHelper.notifyCompat(notificationManager, notificationId, builder.build());
    }
}

