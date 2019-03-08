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
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.kde.kdeconnect.Helpers.FilesHelper;
import org.kde.kdeconnect.Helpers.NotificationHelper;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect.UserInterface.PluginSettingsFragment;
import org.kde.kdeconnect_tp.R;

import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

@PluginFactory.LoadablePlugin
public class SharePlugin extends Plugin {
    private final static String PACKET_TYPE_SHARE_REQUEST = "kdeconnect.share.request";
    private final static String PACKET_TYPE_SHARE_REQUEST_UPDATE = "kdeconnect.share.request.update";

    final static String KEY_NUMBER_OF_FILES = "numberOfFiles";
    final static String KEY_TOTAL_PAYLOAD_SIZE = "totalPayloadSize";

    private final static boolean openUrlsDirectly = true;
    private ExecutorService executorService;
    private final Handler handler;
    private CompositeReceiveFileRunnable receiveFileRunnable;
    private final Callback receiveFileRunnableCallback;

    public SharePlugin() {
        executorService = Executors.newFixedThreadPool(5);
        handler = new Handler(Looper.getMainLooper());
        receiveFileRunnableCallback = new Callback();
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
            if (np.getType().equals(PACKET_TYPE_SHARE_REQUEST_UPDATE)) {
                if (receiveFileRunnable != null && receiveFileRunnable.isRunning()) {
                    receiveFileRunnable.updateTotals(np.getInt(KEY_NUMBER_OF_FILES), np.getLong(KEY_TOTAL_PAYLOAD_SIZE));
                } else {
                    Log.d("SharePlugin", "Received update packet but CompositeUploadJob is null or not running");
                }

                return true;
            }

            if (np.has("filename")) {
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
        handler.post(() -> Toast.makeText(context, R.string.shareplugin_text_saved, Toast.LENGTH_LONG).show());
    }

    @WorkerThread
    private void receiveFile(NetworkPacket np) {
        CompositeReceiveFileRunnable runnable;

        boolean hasNumberOfFiles = np.has(KEY_NUMBER_OF_FILES);
        boolean hasOpen = np.has("open");

        if (hasNumberOfFiles && !hasOpen && receiveFileRunnable != null) {
            runnable = receiveFileRunnable;
        } else {
            runnable = new CompositeReceiveFileRunnable(device, receiveFileRunnableCallback);
        }

        if (!hasNumberOfFiles) {
            np.set(KEY_NUMBER_OF_FILES, 1);
            np.set(KEY_TOTAL_PAYLOAD_SIZE, np.getPayloadSize());
        }

        runnable.addNetworkPacket(np);

        if (runnable != receiveFileRunnable) {
            if (hasNumberOfFiles && !hasOpen) {
                receiveFileRunnable = runnable;
            }
            executorService.execute(runnable);
        }
    }

    @Override
    public PluginSettingsFragment getSettingsFragment(Activity activity) {
        return ShareSettingsFragment.newInstance(getPluginKey());
    }

    void queuedSendUriList(final ArrayList<Uri> uriList) {

        //Read all the data early, as we only have permissions to do it while the activity is alive
        final ArrayList<NetworkPacket> toSend = new ArrayList<>();
        for (Uri uri : uriList) {
            NetworkPacket np = FilesHelper.uriToNetworkPacket(context, uri, PACKET_TYPE_SHARE_REQUEST);

            if (np != null) {
                toSend.add(np);
            }
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
        return new String[]{PACKET_TYPE_SHARE_REQUEST, PACKET_TYPE_SHARE_REQUEST_UPDATE};
    }

    @Override
    public String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_SHARE_REQUEST};
    }

    @Override
    public String[] getOptionalPermissions() {
        return new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};
    }

    private class Callback implements CompositeReceiveFileRunnable.CallBack {
        @Override
        public void onSuccess(CompositeReceiveFileRunnable runnable) {
            if (runnable == receiveFileRunnable) {
                receiveFileRunnable = null;
            }
        }

        @Override
        public void onError(CompositeReceiveFileRunnable runnable, Throwable error) {
            Log.e("SharePlugin", "onError() - " + error.getMessage());
            if (runnable == receiveFileRunnable) {
                receiveFileRunnable = null;
            }
        }
    }
}
