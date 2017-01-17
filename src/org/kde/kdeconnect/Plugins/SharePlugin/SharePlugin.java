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

import android.app.Activity;
import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.ContextCompat;
import android.support.v4.provider.DocumentFile;
import android.util.Log;
import android.widget.Toast;

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Helpers.FilesHelper;
import org.kde.kdeconnect.Helpers.MediaStoreHelper;
import org.kde.kdeconnect.Helpers.NotificationHelper;
import org.kde.kdeconnect.NetworkPackage;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.UserInterface.SettingsActivity;
import org.kde.kdeconnect_tp.R;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class SharePlugin extends Plugin {

    //public final static String PACKAGE_TYPE_SHARE = "kdeconnect.share";
    public final static String PACKAGE_TYPE_SHARE_REQUEST = "kdeconnect.share.request";

    final static boolean openUrlsDirectly = true;

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
    public boolean onPackageReceived(NetworkPackage np) {

        try {
            if (np.hasPayload()) {

                Log.i("SharePlugin", "hasPayload");

                final InputStream input = np.getPayload();
                final long fileLength = np.getPayloadSize();
                final String originalFilename = np.getString("filename", Long.toString(System.currentTimeMillis()));

                //We need to check for already existing files only when storing in the default path.
                //User-defined paths use the new Storage Access Framework that already handles this.
                final boolean customDestination = ShareSettingsActivity.isCustomDestinationEnabled(context);
                final String defaultPath = ShareSettingsActivity.getDefaultDestinationDirectory().getAbsolutePath();
                final String filename = customDestination? originalFilename : FilesHelper.findNonExistingNameForNewFile(defaultPath, originalFilename);

                final String nameWithoutExtension = FilesHelper.getFileNameWithoutExt(filename);
                final String mimeType = FilesHelper.getMimeTypeFromFile(filename);

                final DocumentFile destinationFolderDocument = ShareSettingsActivity.getDestinationDirectory(context);
                final DocumentFile destinationDocument = destinationFolderDocument.createFile(mimeType, nameWithoutExtension);
                final OutputStream destinationOutput = context.getContentResolver().openOutputStream(destinationDocument.getUri());
                final Uri destinationUri = destinationDocument.getUri();

                final int notificationId = (int)System.currentTimeMillis();
                Resources res = context.getResources();
                final NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                        .setContentTitle(res.getString(R.string.incoming_file_title, device.getName()))
                        .setContentText(res.getString(R.string.incoming_file_text, filename))
                        .setTicker(res.getString(R.string.incoming_file_title, device.getName()))
                        .setSmallIcon(android.R.drawable.stat_sys_download)
                        .setAutoCancel(true)
                        .setOngoing(true)
                        .setProgress(100,0,true);

                final NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                NotificationHelper.notifyCompat(notificationManager,notificationId, builder.build());

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        boolean successful = true;
                        try {
                            byte data[] = new byte[1024];
                            long progress = 0, prevProgressPercentage = 0;
                            int count;
                            while ((count = input.read(data)) >= 0) {
                                progress += count;
                                destinationOutput.write(data, 0, count);
                                if (fileLength > 0) {
                                    if (progress >= fileLength) break;
                                    long progressPercentage = (progress * 100 / fileLength);
                                    if (progressPercentage != prevProgressPercentage) {
                                        prevProgressPercentage = progressPercentage;
                                        builder.setProgress(100, (int) progressPercentage, false);
                                        NotificationHelper.notifyCompat(notificationManager, notificationId, builder.build());
                                    }
                                }
                                //else Log.e("SharePlugin", "Infinite loop? :D");
                            }

                            destinationOutput.flush();

                        } catch (Exception e) {
                            successful = false;
                            Log.e("SharePlugin", "Receiver thread exception");
                            e.printStackTrace();
                        } finally {
                            try { destinationOutput.close(); } catch (Exception e) {}
                            try { input.close(); } catch (Exception e) {}
                        }

                        try {
                            Log.i("SharePlugin", "Transfer finished: "+destinationUri.getPath());

                            //Update the notification and allow to open the file from it
                            Resources res = context.getResources();
                            String message = successful? res.getString(R.string.received_file_title, device.getName()) : res.getString(R.string.received_file_fail_title, device.getName());
                            NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                                    .setContentTitle(message)
                                    .setTicker(message)
                                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                                    .setAutoCancel(true);
                            if (successful) {
                                Intent intent = new Intent(Intent.ACTION_VIEW);
                                intent.setDataAndType(destinationUri, mimeType);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
                                stackBuilder.addNextIntent(intent);
                                PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
                                builder.setContentText(res.getString(R.string.received_file_text, destinationDocument.getName()))
                                       .setContentIntent(resultPendingIntent);
                            }
                            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                            if (prefs.getBoolean("share_notification_preference", true)) {
                                builder.setDefaults(Notification.DEFAULT_ALL);
                            }
                            NotificationHelper.notifyCompat(notificationManager, notificationId, builder.build());

                            if (successful) {
                                if (!customDestination) {
                                    Log.i("SharePlugin","Adding to downloads");
                                    DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                                    manager.addCompletedDownload(destinationUri.getLastPathSegment(), device.getName(), true, mimeType, destinationUri.getPath(), fileLength, false);
                                } else {
                                    //Make sure it is added to the Android Gallery anyway
                                    MediaStoreHelper.indexFile(context, destinationUri);
                                }
                            }

                        } catch (Exception e) {
                            Log.e("SharePlugin", "Receiver thread exception");
                            e.printStackTrace();
                        }
                    }
                }).start();

            } else if (np.has("text")) {
                Log.i("SharePlugin", "hasText");

                String text = np.getString("text");
                if(android.os.Build.VERSION.SDK_INT >= 11) {
                    ClipboardManager cm = (ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setText(text);
                } else {
                    android.text.ClipboardManager clipboard = (android.text.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setText(text);
                }
                Toast.makeText(context, R.string.shareplugin_text_saved, Toast.LENGTH_LONG).show();
            } else if (np.has("url")) {

                String url = np.getString("url");

                Log.i("SharePlugin", "hasUrl: "+url);

                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                if (openUrlsDirectly) {
                    context.startActivity(browserIntent);
                } else {
                    Resources res = context.getResources();
                    TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
                    stackBuilder.addNextIntent(browserIntent);
                    PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(
                            0,
                            PendingIntent.FLAG_UPDATE_CURRENT
                    );

                    Notification noti = new NotificationCompat.Builder(context)
                            .setContentTitle(res.getString(R.string.received_url_title, device.getName()))
                            .setContentText(res.getString(R.string.received_url_text, url))
                            .setContentIntent(resultPendingIntent)
                            .setTicker(res.getString(R.string.received_url_title, device.getName()))
                            .setSmallIcon(R.drawable.ic_notification)
                            .setAutoCancel(true)
                            .setDefaults(Notification.DEFAULT_ALL)
                            .build();

                    NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                    NotificationHelper.notifyCompat(notificationManager, (int) System.currentTimeMillis(), noti);
                }
            } else {
                Log.e("SharePlugin", "Error: Nothing attached!");
            }


        } catch(Exception e) {
            Log.e("SharePlugin","Exception");
            e.printStackTrace();
        }

        return true;
    }

    @Override
    public void startPreferencesActivity(SettingsActivity parentActivity) {
        Intent intent = new Intent(parentActivity, ShareSettingsActivity.class);
        intent.putExtra("plugin_display_name", getDisplayName());
        intent.putExtra("plugin_key", getPluginKey());
        parentActivity.startActivity(intent);
    }

    static void queuedSendUriList(Context context, final Device device, final ArrayList<Uri> uriList) {

        //Read all the data early, as we only have permissions to do it while the activity is alive
        final ArrayList<NetworkPackage> toSend = new ArrayList<>();
        for (Uri uri : uriList) {
            toSend.add(uriToNetworkPackage(context, uri));
        }

        //Callback that shows a progress notification
        final NotificationUpdateCallback notificationUpdateCallback = new NotificationUpdateCallback(context, device, toSend);

        //Do the sending in background
        new Thread(new Runnable() {
            @Override
            public void run() {
                //Actually send the files
                try {
                    for (NetworkPackage np : toSend) {
                        boolean success = device.sendPackageBlocking(np, notificationUpdateCallback);
                        if (!success) {
                            Log.e("SharePlugin","Error sending files");
                            return;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();

    }

    //Create the network package from the URI
    private static NetworkPackage uriToNetworkPackage(final Context context, final Uri uri) {

        try {

            ContentResolver cr = context.getContentResolver();
            InputStream inputStream = cr.openInputStream(uri);

            NetworkPackage np = new NetworkPackage(PACKAGE_TYPE_SHARE_REQUEST);
            long size = -1;

            if (uri.getScheme().equals("file")) {
                // file:// is a non media uri, so we cannot query the ContentProvider

                np.set("filename", uri.getLastPathSegment());

                try {
                    size = new File(uri.getPath()).length();
                } catch(Exception e) {
                    Log.e("SendFileActivity", "Could not obtain file size");
                    e.printStackTrace();
                }

            }else{
                // Probably a content:// uri, so we query the Media content provider

                Cursor cursor = null;
                try {
                    String[] proj = { MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DISPLAY_NAME };
                    cursor = cr.query(uri, proj, null, null, null);
                    int column_index = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
                    cursor.moveToFirst();
                    String path = cursor.getString(column_index);
                    np.set("filename", Uri.parse(path).getLastPathSegment());
                    size = new File(path).length();
                } catch(Exception unused) {

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
                    } catch(Exception e) {
                        Log.e("SendFileActivity", "Could not obtain file size");
                        e.printStackTrace();
                    }
                } finally {
                    try { cursor.close(); } catch (Exception e) { }
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


    @Override
    public String[] getSupportedPackageTypes() {
        return new String[]{PACKAGE_TYPE_SHARE_REQUEST};
    }

    @Override
    public String[] getOutgoingPackageTypes() {
        return new String[]{PACKAGE_TYPE_SHARE_REQUEST};
    }


}
