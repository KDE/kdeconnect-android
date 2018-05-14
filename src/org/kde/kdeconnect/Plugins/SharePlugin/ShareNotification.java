package org.kde.kdeconnect.Plugins.SharePlugin;

/*
 * Copyright 2017 Nicolas Fella <nicolas.fella@gmx.de>
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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.FileProvider;

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Helpers.NotificationHelper;
import org.kde.kdeconnect_tp.R;

import java.io.File;
import java.io.FileNotFoundException;

public class ShareNotification {

    private final String filename;
    private NotificationManager notificationManager;
    private int notificationId;
    private NotificationCompat.Builder builder;
    private Device device;

    public ShareNotification(Device device, String filename) {
        this.device = device;
        this.filename = filename;
        notificationId = (int) System.currentTimeMillis();
        notificationManager = (NotificationManager) device.getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        builder = new NotificationCompat.Builder(device.getContext(), NotificationHelper.Channels.DEFAULT)
                .setContentTitle(device.getContext().getResources().getString(R.string.incoming_file_title, device.getName()))
                .setContentText(device.getContext().getResources().getString(R.string.incoming_file_text, filename))
                .setTicker(device.getContext().getResources().getString(R.string.incoming_file_title, device.getName()))
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setAutoCancel(true)
                .setOngoing(true)
                .setProgress(100, 0, true);
    }

    public void show() {
        NotificationHelper.notifyCompat(notificationManager, notificationId, builder.build());
    }

    public int getId() {
        return notificationId;
    }

    public void setProgress(int progress) {
        builder.setProgress(100, progress, false)
                .setContentTitle(device.getContext().getResources().getString(R.string.incoming_file_title, device.getName()) + " (" + progress + "%)");
    }

    public void setFinished(boolean success) {
        String message = success ? device.getContext().getResources().getString(R.string.received_file_title, device.getName()) : device.getContext().getResources().getString(R.string.received_file_fail_title, device.getName());
        builder = new NotificationCompat.Builder(device.getContext(), NotificationHelper.Channels.DEFAULT);
        builder.setContentTitle(message)
                .setTicker(message)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setAutoCancel(true)
                .setOngoing(false);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(device.getContext());
        if (prefs.getBoolean("share_notification_preference", true)) {
            builder.setDefaults(Notification.DEFAULT_ALL);
        }
    }

    public void setURI(Uri destinationUri, String mimeType) {
        /*
         * We only support file URIs (because sending a content uri to another app does not work for security reasons).
         * In effect, that means only the default download folder currently works.
         *
         * TODO: implement our own content provider (instead of support-v4's FileProvider). It should:
         *  - Proxy to real files (in case of the default download folder)
         *  - Proxy to the underlying content uri (in case of a custom download folder)
         */

        //If it's an image, try to show it in the notification
        if (mimeType.startsWith("image/")) {
            try {
                Bitmap image = BitmapFactory.decodeStream(device.getContext().getContentResolver().openInputStream(destinationUri));
                if (image != null) {
                    builder.setLargeIcon(image);
                    builder.setStyle(new NotificationCompat.BigPictureStyle()
                        .bigPicture(image));
                }
            } catch (FileNotFoundException ignored) {}
        }
        if (!"file".equals(destinationUri.getScheme())) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType(mimeType);
        if (Build.VERSION.SDK_INT >= 24) {
            //Nougat and later require "content://" uris instead of "file://" uris
            File file = new File(destinationUri.getPath());
            Uri contentUri = FileProvider.getUriForFile(device.getContext(), "org.kde.kdeconnect_tp.fileprovider", file);
            intent.setDataAndType(contentUri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
        } else {
            intent.setDataAndType(destinationUri, mimeType);
            shareIntent.putExtra(Intent.EXTRA_STREAM, destinationUri);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(device.getContext());
        stackBuilder.addNextIntent(intent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentText(device.getContext().getResources().getString(R.string.received_file_text, filename))
                .setContentIntent(resultPendingIntent);

        shareIntent = Intent.createChooser(shareIntent,
                device.getContext().getString(R.string.share_received_file, destinationUri.getLastPathSegment()));
        PendingIntent sharePendingIntent = PendingIntent.getActivity(device.getContext(), 0,
                shareIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Action.Builder shareAction = new NotificationCompat.Action.Builder(
                R.drawable.ic_share_white, device.getContext().getString(R.string.share), sharePendingIntent);
        builder.addAction(shareAction.build());
    }
}
