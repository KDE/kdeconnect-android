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
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;

import org.apache.commons.lang3.StringUtils;
import org.kde.kdeconnect.Helpers.FilesHelper;
import org.kde.kdeconnect.Helpers.IntentHelper;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect.UserInterface.PluginSettingsFragment;
import org.kde.kdeconnect.async.BackgroundJob;
import org.kde.kdeconnect.async.BackgroundJobHandler;
import org.kde.kdeconnect_tp.R;

import java.net.URL;
import java.util.ArrayList;

/**
 * A Plugin for sharing and receiving files and uris.
 * <p>
 *     All of the associated I/O work is scheduled on background
 *     threads by {@link BackgroundJobHandler}.
 * </p>
 */
@PluginFactory.LoadablePlugin
public class SharePlugin extends Plugin {
    final static String ACTION_CANCEL_SHARE = "org.kde.kdeconnect.Plugins.SharePlugin.CancelShare";
    final static String CANCEL_SHARE_DEVICE_ID_EXTRA = "deviceId";
    final static String CANCEL_SHARE_BACKGROUND_JOB_ID_EXTRA = "backgroundJobId";

    private final static String PACKET_TYPE_SHARE_REQUEST = "kdeconnect.share.request";
    final static String PACKET_TYPE_SHARE_REQUEST_UPDATE = "kdeconnect.share.request.update";

    final static String KEY_NUMBER_OF_FILES = "numberOfFiles";
    final static String KEY_TOTAL_PAYLOAD_SIZE = "totalPayloadSize";

    private BackgroundJobHandler backgroundJobHandler;
    private final Handler handler;

    private CompositeReceiveFileJob receiveFileJob;
    private CompositeUploadFileJob uploadFileJob;
    private final Callback receiveFileJobCallback;

    public SharePlugin() {
        backgroundJobHandler = BackgroundJobHandler.newFixedThreadPoolBackgroundJobHander(5);
        handler = new Handler(Looper.getMainLooper());
        receiveFileJobCallback = new Callback();
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
        return ContextCompat.getDrawable(context, R.drawable.share_plugin_action_24dp);
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
                if (receiveFileJob != null && receiveFileJob.isRunning()) {
                    receiveFileJob.updateTotals(np.getInt(KEY_NUMBER_OF_FILES), np.getLong(KEY_TOTAL_PAYLOAD_SIZE));
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

        IntentHelper.startActivityFromBackground(context, browserIntent, url);
    }

    private void receiveText(NetworkPacket np) {
        String text = np.getString("text");
        ClipboardManager cm = ContextCompat.getSystemService(context, ClipboardManager.class);
        cm.setText(text);
        handler.post(() -> Toast.makeText(context, R.string.shareplugin_text_saved, Toast.LENGTH_LONG).show());
    }

    @WorkerThread
    private void receiveFile(NetworkPacket np) {
        CompositeReceiveFileJob job;

        boolean hasNumberOfFiles = np.has(KEY_NUMBER_OF_FILES);
        boolean isOpen = np.getBoolean("open", false);

        if (hasNumberOfFiles && !isOpen && receiveFileJob != null) {
            job = receiveFileJob;
        } else {
            job = new CompositeReceiveFileJob(device, receiveFileJobCallback);
        }

        if (!hasNumberOfFiles) {
            np.set(KEY_NUMBER_OF_FILES, 1);
            np.set(KEY_TOTAL_PAYLOAD_SIZE, np.getPayloadSize());
        }

        job.addNetworkPacket(np);

        if (job != receiveFileJob) {
            if (hasNumberOfFiles && !isOpen) {
                receiveFileJob = job;
            }
            backgroundJobHandler.runJob(job);
        }
    }

    @Override
    public PluginSettingsFragment getSettingsFragment(Activity activity) {
        return ShareSettingsFragment.newInstance(getPluginKey());
    }

    void sendUriList(final ArrayList<Uri> uriList) {
        CompositeUploadFileJob job;

        if (uploadFileJob == null) {
            job = new CompositeUploadFileJob(device, this.receiveFileJobCallback);
        } else {
            job = uploadFileJob;
        }

        //Read all the data early, as we only have permissions to do it while the activity is alive
        for (Uri uri : uriList) {
            NetworkPacket np = FilesHelper.uriToNetworkPacket(context, uri, PACKET_TYPE_SHARE_REQUEST);

            if (np != null) {
                job.addNetworkPacket(np);
            }
        }

        if (job != uploadFileJob) {
            uploadFileJob = job;
            backgroundJobHandler.runJob(uploadFileJob);
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

                    sendUriList(uriList);
                } catch (Exception e) {
                    Log.e("ShareActivity", "Exception");
                    e.printStackTrace();
                }

            } else if (extras.containsKey(Intent.EXTRA_TEXT)) {
                String text = extras.getString(Intent.EXTRA_TEXT);
                String subject = extras.getString(Intent.EXTRA_SUBJECT);

                //Hack: Detect shared youtube videos, so we can open them in the browser instead of as text
                if (StringUtils.endsWith(subject, "YouTube")) {
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

    private class Callback implements BackgroundJob.Callback<Void> {
        @Override
        public void onResult(@NonNull BackgroundJob job, Void result) {
            if (job == receiveFileJob) {
                receiveFileJob = null;
            } else if (job == uploadFileJob) {
                uploadFileJob = null;
            }
        }

        @Override
        public void onError(@NonNull BackgroundJob job, @NonNull Throwable error) {
            if (job == receiveFileJob) {
                receiveFileJob = null;
            } else if (job == uploadFileJob) {
                uploadFileJob = null;
            }
        }
    }

    void cancelJob(long jobId) {
        if (backgroundJobHandler.isRunning(jobId)) {
            BackgroundJob job = backgroundJobHandler.getJob(jobId);

            if (job != null) {
                job.cancel();

                if (job == receiveFileJob) {
                    receiveFileJob = null;
                } else if (job == uploadFileJob) {
                    uploadFileJob = null;
                }
            }
        }
    }
}
