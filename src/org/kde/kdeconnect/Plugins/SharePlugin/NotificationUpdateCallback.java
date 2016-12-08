package org.kde.kdeconnect.Plugins.SharePlugin;

import android.app.NotificationManager;
import android.content.Context;
import android.content.res.Resources;
import android.support.v4.app.NotificationCompat;

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Helpers.NotificationHelper;
import org.kde.kdeconnect.NetworkPackage;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;

class NotificationUpdateCallback extends Device.SendPackageStatusCallback {

    final Context context;
    final Resources res;
    final Device device;
    final NotificationManager notificationManager;
    final NotificationCompat.Builder builder;

    final ArrayList<NetworkPackage> toSend;

    final int notificationId;

    int sentFiles = 0;
    final int numFiles;

    NotificationUpdateCallback(Context context, Device device, ArrayList<NetworkPackage> toSend) {
        this.context = context;
        this.toSend = toSend;
        this.device = device;
        this.res = context.getResources();

        String title;
        if (toSend.size() > 1) {
            title = res.getString(R.string.outgoing_files_title, device.getName());
        } else {
            title = res.getString(R.string.outgoing_file_title, device.getName());
        }
        notificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        builder = new NotificationCompat.Builder(context)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setAutoCancel(true)
                .setProgress(100, 0, false)
                .setContentTitle(title)
                .setTicker(title);

        notificationId = (int)System.currentTimeMillis();

        numFiles = toSend.size();

    }

    @Override
    public void onProgressChanged(int progress) {
        builder.setProgress(100 * numFiles, (100 * sentFiles) + progress, false);
        NotificationHelper.notifyCompat(notificationManager, notificationId, builder.build());
    }

    @Override
    public void onSuccess() {
        sentFiles++;
        if (sentFiles == numFiles) {
            updateDone(true);
        } else {
            updateText();
        }
        NotificationHelper.notifyCompat(notificationManager, notificationId, builder.build());
    }

    @Override
    public void onFailure(Throwable e) {
        updateDone(false);
        NotificationHelper.notifyCompat(notificationManager, notificationId, builder.build());
    }

    private void updateText() {
        String text;
        if (numFiles > 1) {
            text = res.getString(R.string.outgoing_files_text, sentFiles, numFiles);
        } else {
            text = res.getString(R.string.outgoing_file_text, device.getName());
        }
        builder.setContentText(text);
    }

    private void updateDone(boolean successful) {
        int icon;
        String title;
        String text;
        int progress;
        if (successful) {
            progress = 1;
            if (numFiles > 1) {
                text = res.getString(R.string.outgoing_files_text, sentFiles, numFiles);
            } else {
                final String filename = toSend.get(0).getString("filename");
                text = res.getString(R.string.sent_file_text, filename);
            }
            title = res.getString(R.string.sent_file_title, device.getName());
            icon = android.R.drawable.stat_sys_upload_done;
        } else {
            progress = 0;
            final String filename = toSend.get(sentFiles).getString("filename");
            title = res.getString(R.string.sent_file_failed_title, device.getName());
            text = res.getString(R.string.sent_file_failed_text, filename);
            icon = android.R.drawable.stat_notify_error;
        }

        builder.setOngoing(false)
                .setTicker(title)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(icon)
                .setProgress(progress, progress, false); //setting progress to 0 out of 0 remove the progress bar

    }

}

