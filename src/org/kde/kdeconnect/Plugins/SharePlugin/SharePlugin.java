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

package org.kde.kdeconnect.Plugins.SharePlugin;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.support.annotation.WorkerThread;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v4.provider.DocumentFile;
import android.util.Log;
import android.widget.Toast;

import org.kde.kdeconnect.Helpers.FilesHelper;
import org.kde.kdeconnect.Helpers.MediaStoreHelper;
import org.kde.kdeconnect.Helpers.NotificationHelper;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.UserInterface.DeviceSettingsActivity;
import org.kde.kdeconnect_tp.R;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SharePlugin extends Plugin implements ReceiveFileRunnable.CallBack {

    private final static String PACKET_TYPE_SHARE_REQUEST = "kdeconnect.share.request";

    private final static boolean openUrlsDirectly = true;
    private ShareNotification shareNotification;
    private FinishReceivingRunnable finishReceivingRunnable;
    private ExecutorService executorService;
    private ShareInfo currentShareInfo;
    private Handler handler;

    public SharePlugin() {
        executorService = Executors.newSingleThreadExecutor();
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public boolean onCreate() {
        optionalPermissionExplanation = R.string.share_optional_permission_explanation;
        return true;
    }

    @Override
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_sharereceiver);
    }

    @Override
    public Drawable getIcon() {
        return ContextCompat.getDrawable(context, R.drawable.share_plugin_action);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_sharereceiver_desc);
    }

    @Override
    public boolean hasMainActivity() {
        return true;
    }

    @Override
    public String getActionName() {
        return context.getString(R.string.send_files);
    }

    @Override
    public void startMainActivity(Activity parentActivity) {
        Intent intent = new Intent(parentActivity, SendFileActivity.class);
        intent.putExtra("deviceId", device.getDeviceId());
        parentActivity.startActivity(intent);
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    @WorkerThread
    public boolean onPacketReceived(NetworkPacket np) {
        try {
            if (np.hasPayload()) {
                if (isPermissionGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    receiveFile(np);
                } else {
                    Log.i("SharePlugin", "no Permission for Storage");
                }

            } else if (np.has("text")) {
                Log.i("SharePlugin", "hasText");
                receiveText(np);
            } else if (np.has("url")) {
                receiveUrl(np);
            } else {
                Log.e("SharePlugin", "Error: Nothing attached!");
            }

        } catch (Exception e) {
            Log.e("SharePlugin", "Exception");
            e.printStackTrace();
        }

        return true;
    }

    private void receiveUrl(NetworkPacket np) {
        String url = np.getString("url");

        Log.i("SharePlugin", "hasUrl: " + url);

        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (openUrlsDirectly) {
            context.startActivity(browserIntent);
        } else {
            Resources res = context.getResources();

            PendingIntent resultPendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    browserIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
            );

            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            Notification noti = new NotificationCompat.Builder(context, NotificationHelper.Channels.DEFAULT)
                    .setContentTitle(res.getString(R.string.received_url_title, device.getName()))
                    .setContentText(res.getString(R.string.received_url_text, url))
                    .setContentIntent(resultPendingIntent)
                    .setTicker(res.getString(R.string.received_url_title, device.getName()))
                    .setSmallIcon(R.drawable.ic_notification)
                    .setAutoCancel(true)
                    .setDefaults(Notification.DEFAULT_ALL)
                    .build();

            NotificationHelper.notifyCompat(notificationManager, (int) System.currentTimeMillis(), noti);
        }
    }

    private void receiveText(NetworkPacket np) {
        String text = np.getString("text");
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setText(text);
        Toast.makeText(context, R.string.shareplugin_text_saved, Toast.LENGTH_LONG).show();
    }

    @WorkerThread
    private void receiveFile(NetworkPacket np) {
        if (finishReceivingRunnable != null) {
            Log.i("SharePlugin", "receiveFile: canceling finishReceivingRunnable");
            handler.removeCallbacks(finishReceivingRunnable);
            finishReceivingRunnable = null;
        }

        ShareInfo info = new ShareInfo();
        info.currentFileNumber = currentShareInfo == null ? 1 : currentShareInfo.currentFileNumber + 1;
        info.inputStream = np.getPayload();
        info.fileSize = np.getPayloadSize();
        info.fileName = np.getString("filename", Long.toString(System.currentTimeMillis()));
        info.shouldOpen = np.getBoolean("open");
        info.setNumberOfFiles(np.getInt("numberOfFiles", 1));
        info.setTotalTransferSize(np.getLong("totalPayloadSize", 1));

        if (currentShareInfo == null) {
            currentShareInfo = info;
        } else {
            synchronized (currentShareInfo) {
                currentShareInfo.setNumberOfFiles(info.numberOfFiles());
                currentShareInfo.setTotalTransferSize(info.totalTransferSize());
            }
        }

        String filename = info.fileName;
        final DocumentFile destinationFolderDocument;

        //We need to check for already existing files only when storing in the default path.
        //User-defined paths use the new Storage Access Framework that already handles this.
        //If the file should be opened immediately store it in the standard location to avoid the FileProvider trouble (See ShareNotification::setURI)
        if (np.getBoolean("open") || !ShareSettingsActivity.isCustomDestinationEnabled(context)) {
            final String defaultPath = ShareSettingsActivity.getDefaultDestinationDirectory().getAbsolutePath();
            filename = FilesHelper.findNonExistingNameForNewFile(defaultPath, filename);
            destinationFolderDocument = DocumentFile.fromFile(new File(defaultPath));
        } else {
            destinationFolderDocument = ShareSettingsActivity.getDestinationDirectory(context);
        }
        String displayName = FilesHelper.getFileNameWithoutExt(filename);
        String mimeType = FilesHelper.getMimeTypeFromFile(filename);

        if ("*/*".equals(mimeType)) {
            displayName = filename;
        }

        info.fileDocument = destinationFolderDocument.createFile(mimeType, displayName);
        assert info.fileDocument != null;
        info.fileDocument.getType();
        try {
            info.outputStream = new BufferedOutputStream(context.getContentResolver().openOutputStream(info.fileDocument.getUri()));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        }

        if (shareNotification == null) {
            shareNotification = new ShareNotification(device);
        }

        shareNotification.setTitle(context.getResources().getQuantityString(R.plurals.incoming_file_title, info.numberOfFiles(), info.numberOfFiles(), device.getName()));
        //shareNotification.setProgress(0, context.getResources().getQuantityString(R.plurals.incoming_files_text, numFiles, filename, currentFileNum, numFiles));
        shareNotification.show();

        ReceiveFileRunnable runnable = new ReceiveFileRunnable(info, this);
        executorService.execute(runnable);
    }

    @Override
    public void startPreferencesActivity(DeviceSettingsActivity parentActivity) {
        Intent intent = new Intent(parentActivity, ShareSettingsActivity.class);
        intent.putExtra("plugin_display_name", getDisplayName());
        intent.putExtra("plugin_key", getPluginKey());
        parentActivity.startActivity(intent);
    }

    void queuedSendUriList(final ArrayList<Uri> uriList) {

        //Read all the data early, as we only have permissions to do it while the activity is alive
        final ArrayList<NetworkPacket> toSend = new ArrayList<>();
        for (Uri uri : uriList) {
            toSend.add(uriToNetworkPacket(context, uri));
        }

        //Callback that shows a progress notification
        final NotificationUpdateCallback notificationUpdateCallback = new NotificationUpdateCallback(context, device, toSend);

        //Do the sending in background
        new Thread(() -> {
            //Actually send the files
            try {
                for (NetworkPacket np : toSend) {
                    boolean success = device.sendPacketBlocking(np, notificationUpdateCallback);
                    if (!success) {
                        Log.e("SharePlugin", "Error sending files");
                        return;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

    }

    //Create the network package from the URI
    private static NetworkPacket uriToNetworkPacket(final Context context, final Uri uri) {

        try {

            ContentResolver cr = context.getContentResolver();
            InputStream inputStream = cr.openInputStream(uri);

            NetworkPacket np = new NetworkPacket(PACKET_TYPE_SHARE_REQUEST);
            long size = -1;

            if (uri.getScheme().equals("file")) {
                // file:// is a non media uri, so we cannot query the ContentProvider

                np.set("filename", uri.getLastPathSegment());

                try {
                    size = new File(uri.getPath()).length();
                } catch (Exception e) {
                    Log.e("SendFileActivity", "Could not obtain file size");
                    e.printStackTrace();
                }

            } else {
                // Probably a content:// uri, so we query the Media content provider

                Cursor cursor = null;
                try {
                    String[] proj = {MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DISPLAY_NAME};
                    cursor = cr.query(uri, proj, null, null, null);
                    int column_index = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
                    cursor.moveToFirst();
                    String path = cursor.getString(column_index);
                    np.set("filename", Uri.parse(path).getLastPathSegment());
                    size = new File(path).length();
                } catch (Exception unused) {

                    Log.w("SendFileActivity", "Could not resolve media to a file, trying to get info as media");

                    try {
                        int column_index = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
                        cursor.moveToFirst();
                        String name = cursor.getString(column_index);
                        np.set("filename", name);
                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.e("SendFileActivity", "Could not obtain file name");
                    }

                    try {
                        int column_index = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE);
                        cursor.moveToFirst();
                        //For some reason this size can differ from the actual file size!
                        size = cursor.getInt(column_index);
                    } catch (Exception e) {
                        Log.e("SendFileActivity", "Could not obtain file size");
                        e.printStackTrace();
                    }
                } finally {
                    try {
                        cursor.close();
                    } catch (Exception e) {
                    }
                }

            }

            np.setPayload(inputStream, size);

            return np;
        } catch (Exception e) {
            Log.e("SendFileActivity", "Exception sending files");
            e.printStackTrace();
            return null;
        }
    }

    public void share(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras != null) {
            if (extras.containsKey(Intent.EXTRA_STREAM)) {

                try {

                    ArrayList<Uri> uriList;
                    if (!Intent.ACTION_SEND.equals(intent.getAction())) {
                        uriList = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                    } else {
                        Uri uri = extras.getParcelable(Intent.EXTRA_STREAM);
                        uriList = new ArrayList<>();
                        uriList.add(uri);
                    }

                    queuedSendUriList(uriList);

                } catch (Exception e) {
                    Log.e("ShareActivity", "Exception");
                    e.printStackTrace();
                }

            } else if (extras.containsKey(Intent.EXTRA_TEXT)) {
                String text = extras.getString(Intent.EXTRA_TEXT);
                String subject = extras.getString(Intent.EXTRA_SUBJECT);

                //Hack: Detect shared youtube videos, so we can open them in the browser instead of as text
                if (subject != null && subject.endsWith("YouTube")) {
                    int index = text.indexOf(": http://youtu.be/");
                    if (index > 0) {
                        text = text.substring(index + 2); //Skip ": "
                    }
                }

                boolean isUrl;
                try {
                    new URL(text);
                    isUrl = true;
                } catch (Exception e) {
                    isUrl = false;
                }
                NetworkPacket np = new NetworkPacket(SharePlugin.PACKET_TYPE_SHARE_REQUEST);
                if (isUrl) {
                    np.set("url", text);
                } else {
                    np.set("text", text);
                }
                device.sendPacket(np);
            }
        }

    }

    @Override
    public String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_SHARE_REQUEST};
    }

    @Override
    public String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_SHARE_REQUEST};
    }

    @Override
    public String[] getOptionalPermissions() {
        return new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};
    }

    @Override
    public void onProgress(ShareInfo info, int progress) {
        if (progress == 0 && currentShareInfo != info) {
            currentShareInfo = info;
        }

        shareNotification.setProgress(progress, context.getResources().getQuantityString(R.plurals.incoming_files_text, info.numberOfFiles(), info.fileName, info.currentFileNumber, info.numberOfFiles()));
        shareNotification.show();
    }

    @Override
    public void onSuccess(ShareInfo info) {
        Log.i("SharePlugin", "onSuccess() - Transfer finished for file: " + info.fileDocument.getUri().getPath());

        if (info.shouldOpen) {
            shareNotification.cancel();

            Intent intent = new Intent(Intent.ACTION_VIEW);
            if (Build.VERSION.SDK_INT >= 24) {
                //Nougat and later require "content://" uris instead of "file://" uris
                File file = new File(info.fileDocument.getUri().getPath());
                Uri contentUri = FileProvider.getUriForFile(device.getContext(), "org.kde.kdeconnect_tp.fileprovider", file);
                intent.setDataAndType(contentUri, info.fileDocument.getType());
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                intent.setDataAndType(info.fileDocument.getUri(), info.fileDocument.getType());
            }

            context.startActivity(intent);
        } else {
            if (!ShareSettingsActivity.isCustomDestinationEnabled(context)) {
                Log.i("SharePlugin", "Adding to downloads");
                DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                manager.addCompletedDownload(info.fileDocument.getUri().getLastPathSegment(), device.getName(), true, info.fileDocument.getType(), info.fileDocument.getUri().getPath(), info.fileSize, false);
            } else {
                //Make sure it is added to the Android Gallery anyway
                MediaStoreHelper.indexFile(context, info.fileDocument.getUri());
            }

            if (info.numberOfFiles() == 1 || info.currentFileNumber == info.numberOfFiles()) {
                finishReceivingRunnable = new FinishReceivingRunnable(info);
                Log.i("SharePlugin", "onSuccess() - scheduling finishReceivingRunnable");
                handler.postDelayed(finishReceivingRunnable, 1000);
            }
        }
    }

    @Override
    public void onError(ShareInfo info, Throwable error) {
        Log.e("SharePlugin", "onError: " + error.getMessage());

        info.fileDocument.delete();

        int failedFiles = info.numberOfFiles() - (info.currentFileNumber - 1);
        shareNotification.setFinished(context.getResources().getQuantityString(R.plurals.received_files_fail_title, failedFiles, failedFiles, info.numberOfFiles(), device.getName()));
        shareNotification.show();
        shareNotification = null;
        currentShareInfo = null;
    }

    private class FinishReceivingRunnable implements Runnable {
        private final ShareInfo info;

        private FinishReceivingRunnable(ShareInfo info) {
            this.info = info;
        }

        @Override
        public void run() {
            Log.i("SharePlugin", "FinishReceivingRunnable: Finishing up");

            if (shareNotification != null) {
                //Update the notification and allow to open the file from it
                shareNotification.setFinished(context.getResources().getQuantityString(R.plurals.received_files_title, info.numberOfFiles(), info.numberOfFiles(), device.getName()));

                if (info.numberOfFiles() == 1) {
                    shareNotification.setURI(info.fileDocument.getUri(), info.fileDocument.getType(), info.fileName);
                }

                shareNotification.show();
                shareNotification = null;
            }

            finishReceivingRunnable = null;
            currentShareInfo = null;
        }
    }
}
