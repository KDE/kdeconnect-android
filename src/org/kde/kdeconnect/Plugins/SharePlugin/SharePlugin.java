/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.SharePlugin;

import android.Manifest;
import android.app.Activity;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;

import org.apache.commons.lang3.ArrayUtils;
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

    private final BackgroundJobHandler backgroundJobHandler;
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
    public boolean hasMainActivity(Context context) {
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
                receiveFile(np);
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
        return ShareSettingsFragment.newInstance(getPluginKey(), R.xml.shareplugin_preferences);
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return ArrayUtils.EMPTY_STRING_ARRAY;
        } else {
            return new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};
        }
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
