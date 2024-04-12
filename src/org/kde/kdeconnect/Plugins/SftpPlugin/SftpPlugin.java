/*
 * SPDX-FileCopyrightText: 2014 Samoilenko Yuri <kinnalru@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Plugins.SftpPlugin;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.Settings;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;
import org.kde.kdeconnect.Helpers.NetworkHelper;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect.UserInterface.AlertDialogFragment;
import org.kde.kdeconnect.UserInterface.DeviceSettingsAlertDialogFragment;
import org.kde.kdeconnect.UserInterface.MainActivity;
import org.kde.kdeconnect.UserInterface.PluginSettingsFragment;
import org.kde.kdeconnect.UserInterface.StartActivityAlertDialogFragment;
import org.kde.kdeconnect_tp.BuildConfig;
import org.kde.kdeconnect_tp.R;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

@PluginFactory.LoadablePlugin
public class SftpPlugin extends Plugin implements SharedPreferences.OnSharedPreferenceChangeListener {

    private final static String PACKET_TYPE_SFTP = "kdeconnect.sftp";
    private final static String PACKET_TYPE_SFTP_REQUEST = "kdeconnect.sftp.request";

    static final int PREFERENCE_KEY_STORAGE_INFO_LIST = R.string.sftp_preference_key_storage_info_list;

    private static final SimpleSftpServer server = new SimpleSftpServer();

    @Override
    public @NonNull String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_sftp);
    }

    @Override
    public @NonNull String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_sftp_desc);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public boolean checkRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return SftpSettingsFragment.getStorageInfoList(context, this).size() != 0;
        }
    }

    @Override
    public @NonNull AlertDialogFragment getPermissionExplanationDialog() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return new StartActivityAlertDialogFragment.Builder()
                    .setTitle(getDisplayName())
                    .setMessage(R.string.sftp_manage_storage_permission_explanation)
                    .setPositiveButton(R.string.open_settings)
                    .setNegativeButton(R.string.cancel)
                    .setIntentAction(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    .setIntentUrl("package:" + BuildConfig.APPLICATION_ID)
                    .setStartForResult(true)
                    .setRequestCode(MainActivity.RESULT_NEEDS_RELOAD)
                    .create();
        } else {
            return new DeviceSettingsAlertDialogFragment.Builder()
                    .setTitle(getDisplayName())
                    .setMessage(R.string.sftp_saf_permission_explanation)
                    .setPositiveButton(R.string.ok)
                    .setNegativeButton(R.string.cancel)
                    .setDeviceId(getDevice().getDeviceId())
                    .setPluginKey(getPluginKey())
                    .create();
        }
    }

    @Override
    public void onDestroy() {
        server.stop();
        if (getPreferences() != null) {
            getPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }
    }

    @Override
    public boolean onPacketReceived(@NonNull NetworkPacket np) {
        if (np.getBoolean("startBrowsing")) {
            if (!server.isInitialized()) {
                try {
                    server.initialize(context, getDevice());
                } catch (GeneralSecurityException e) {
                    throw new RuntimeException(e);
                }
            }

            ArrayList<String> paths = new ArrayList<>();
            ArrayList<String> pathNames = new ArrayList<>();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                List<StorageVolume> volumes = context.getSystemService(StorageManager.class).getStorageVolumes();
                for (StorageVolume sv : volumes) {
                    pathNames.add(sv.getDescription(context));
                    paths.add(sv.getDirectory().getPath());
                }
            } else {
                List<StorageInfo> storageInfoList = SftpSettingsFragment.getStorageInfoList(context, this);
                Collections.sort(storageInfoList, Comparator.comparing(StorageInfo::getUri));
                if (storageInfoList.size() > 0) {
                    getPathsAndNamesForStorageInfoList(paths, pathNames, storageInfoList);
                } else {
                    NetworkPacket np2 = new NetworkPacket(PACKET_TYPE_SFTP);
                    np2.set("errorMessage", context.getString(R.string.sftp_no_storage_locations_configured));
                    getDevice().sendPacket(np2);
                    return true;
                }
                removeChildren(storageInfoList);
                server.setSafRoots(storageInfoList);
            }

            if (server.start()) {
                if (getPreferences() != null) {
                    getPreferences().registerOnSharedPreferenceChangeListener(this);
                }

                NetworkPacket np2 = new NetworkPacket(PACKET_TYPE_SFTP);

                np2.set("ip", NetworkHelper.getLocalIpAddress().getHostAddress());
                np2.set("port", server.getPort());
                np2.set("user", SimpleSftpServer.USER);
                np2.set("password", server.regeneratePassword());

                //Kept for compatibility, in case "multiPaths" is not possible or the other end does not support it
                if (paths.size() == 1) {
                    np2.set("path", paths.get(0));
                } else {
                    np2.set("path", "/");
                }

                if (paths.size() > 0) {
                    np2.set("multiPaths", paths);
                    np2.set("pathNames", pathNames);
                }

                getDevice().sendPacket(np2);

                return true;
            }
        }
        return false;
    }

    private void getPathsAndNamesForStorageInfoList(List<String> paths, List<String> pathNames, List<StorageInfo> storageInfoList) {
        StorageInfo prevInfo = null;
        StringBuilder pathBuilder = new StringBuilder();

        for (StorageInfo curInfo : storageInfoList) {
            pathBuilder.setLength(0);
            pathBuilder.append("/");

            if (prevInfo != null && curInfo.uri.toString().startsWith(prevInfo.uri.toString())) {
                pathBuilder.append(prevInfo.displayName);
                pathBuilder.append("/");
                if (curInfo.uri.getPath() != null && prevInfo.uri.getPath() != null) {
                    pathBuilder.append(curInfo.uri.getPath().substring(prevInfo.uri.getPath().length()));
                } else {
                    throw new RuntimeException("curInfo.uri.getPath() or parentInfo.uri.getPath() returned null");
                }
            } else {
                pathBuilder.append(curInfo.displayName);

                if (prevInfo == null || !curInfo.uri.toString().startsWith(prevInfo.uri.toString())) {
                    prevInfo = curInfo;
                }
            }

            paths.add(pathBuilder.toString());
            pathNames.add(curInfo.displayName);
        }
    }

    private void removeChildren(List<StorageInfo> storageInfoList) {
        StorageInfo prevInfo = null;
        Iterator<StorageInfo> it = storageInfoList.iterator();

        while (it.hasNext()) {
            StorageInfo curInfo = it.next();

            if (prevInfo != null && curInfo.uri.toString().startsWith(prevInfo.uri.toString())) {
                it.remove();
            } else {
                if (prevInfo == null || !curInfo.uri.toString().startsWith(prevInfo.uri.toString())) {
                    prevInfo = curInfo;
                }
            }
        }
    }

    @Override
    public @NonNull String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_SFTP_REQUEST};
    }

    @Override
    public @NonNull String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_SFTP};
    }

    @Override
    public boolean hasSettings() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R;
    }

    @Override
    public boolean supportsDeviceSpecificSettings() { return true; }

    @Override
    public PluginSettingsFragment getSettingsFragment(Activity activity) {
        return SftpSettingsFragment.newInstance(getPluginKey(), R.xml.sftpplugin_preferences);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(context.getString(PREFERENCE_KEY_STORAGE_INFO_LIST))) {
            if (server.isStarted()) {
                server.stop();

                NetworkPacket np = new NetworkPacket(PACKET_TYPE_SFTP_REQUEST);
                np.set("startBrowsing", true);
                onPacketReceived(np);
            }
        }
    }

    static class StorageInfo {
        private static final String KEY_DISPLAY_NAME = "DisplayName";
        private static final String KEY_URI = "Uri";

        @NonNull
        String displayName;
        @NonNull
        final Uri uri;

        StorageInfo(@NonNull String displayName, @NonNull Uri uri) {
            this.displayName = displayName;
            this.uri = uri;
        }

        @NonNull
        Uri getUri() {
            return uri;
        }

        static StorageInfo copy(StorageInfo from) {
            //Both String and Uri are immutable
            return new StorageInfo(from.displayName, from.uri);
        }

        boolean isFileUri() {
            return uri.getScheme().equals(ContentResolver.SCHEME_FILE);
        }

        boolean isContentUri() {
            return uri.getScheme().equals(ContentResolver.SCHEME_CONTENT);
        }

        public JSONObject toJSON() throws JSONException {
            JSONObject jsonObject = new JSONObject();

            jsonObject.put(KEY_DISPLAY_NAME, displayName);
            jsonObject.put(KEY_URI, uri.toString());

            return jsonObject;
        }

        @NonNull
        static StorageInfo fromJSON(@NonNull JSONObject jsonObject) throws JSONException {
            String displayName = jsonObject.getString(KEY_DISPLAY_NAME);
            Uri uri = Uri.parse(jsonObject.getString(KEY_URI));

            return new StorageInfo(displayName, uri);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            StorageInfo that = (StorageInfo) o;

            if (!displayName.equals(that.displayName)) return false;
            return uri.equals(that.uri);
        }

        @Override
        public int hashCode() {
            int result = displayName.hashCode();
            result = 31 * result + uri.hashCode();
            return result;
        }
    }
}
