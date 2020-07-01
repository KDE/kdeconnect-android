/*
 * Copyright 2014 Samoilenko Yuri <kinnalru@gmail.com>
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

package org.kde.kdeconnect.Plugins.SftpPlugin;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect.UserInterface.AlertDialogFragment;
import org.kde.kdeconnect.UserInterface.DeviceSettingsAlertDialogFragment;
import org.kde.kdeconnect.UserInterface.PluginSettingsFragment;
import org.kde.kdeconnect_tp.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

@PluginFactory.LoadablePlugin
public class SftpPlugin extends Plugin implements SharedPreferences.OnSharedPreferenceChangeListener {

    private final static String PACKET_TYPE_SFTP = "kdeconnect.sftp";
    private final static String PACKET_TYPE_SFTP_REQUEST = "kdeconnect.sftp.request";

    static int PREFERENCE_KEY_STORAGE_INFO_LIST = R.string.sftp_preference_key_storage_info_list;
    private static int PREFERENCE_KEY_ADD_CAMERA_SHORTCUT = R.string.sftp_preference_key_add_camera_shortcut;

    private static final SimpleSftpServer server = new SimpleSftpServer();

    @Override
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_sftp);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_sftp_desc);
    }

    @Override
    public boolean onCreate() {
        try {
            server.init(context, device);

            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
                return SftpSettingsFragment.getStorageInfoList(context, this).size() != 0;
            }

            return true;
        } catch (Exception e) {
            Log.e("SFTP", "Exception in server.init()", e);
            return false;
        }
    }

    @Override
    public boolean checkOptionalPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return SftpSettingsFragment.getStorageInfoList(context, this).size() != 0;
        }

        return true;
    }

    @Override
    public AlertDialogFragment getOptionalPermissionExplanationDialog() {
        return new DeviceSettingsAlertDialogFragment.Builder()
                .setTitle(getDisplayName())
                .setMessage(R.string.sftp_saf_permission_explanation)
                .setPositiveButton(R.string.ok)
                .setNegativeButton(R.string.cancel)
                .setDeviceId(device.getDeviceId())
                .setPluginKey(getPluginKey())
                .create();
    }

    @Override
    public void onDestroy() {
        server.stop();
        if (preferences != null) {
            preferences.unregisterOnSharedPreferenceChangeListener(this);
        }
    }

    @Override
    public boolean onPacketReceived(NetworkPacket np) {
        if (np.getBoolean("startBrowsing")) {
            ArrayList<String> paths = new ArrayList<>();
            ArrayList<String> pathNames = new ArrayList<>();

            List<StorageInfo> storageInfoList = SftpSettingsFragment.getStorageInfoList(context, this);
            Collections.sort(storageInfoList, Comparator.comparing(StorageInfo::getUri));

            if (storageInfoList.size() > 0) {
                getPathsAndNamesForStorageInfoList(paths, pathNames, storageInfoList);
            } else {
                NetworkPacket np2 = new NetworkPacket(PACKET_TYPE_SFTP);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    np2.set("errorMessage", context.getString(R.string.sftp_no_storage_locations_configured));
                } else {
                    np2.set("errorMessage", context.getString(R.string.sftp_no_sdcard_detected));
                }

                device.sendPacket(np2);

                return true;
            }

            removeChildren(storageInfoList);

            if (server.start(storageInfoList)) {
                if (preferences != null) {
                    preferences.registerOnSharedPreferenceChangeListener(this);
                }

                NetworkPacket np2 = new NetworkPacket(PACKET_TYPE_SFTP);

                //TODO: ip is not used on desktop any more remove both here and from desktop code when nobody ships 1.2.0
                np2.set("ip", server.getLocalIpAddress());
                np2.set("port", server.getPort());
                np2.set("user", SimpleSftpServer.USER);
                np2.set("password", server.getPassword());

                //Kept for compatibility, in case "multiPaths" is not possible or the other end does not support it
                np2.set("path", "/");

                if (paths.size() > 0) {
                    np2.set("multiPaths", paths);
                    np2.set("pathNames", pathNames);
                }

                device.sendPacket(np2);

                return true;
            }
        }
        return false;
    }

    private void getPathsAndNamesForStorageInfoList(List<String> paths, List<String> pathNames, List<StorageInfo> storageInfoList) {
        StorageInfo prevInfo = null;
        StringBuilder pathBuilder = new StringBuilder();

        boolean addCameraShortcuts = false;

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            if (preferences != null) {
                addCameraShortcuts = preferences.getBoolean(context.getString(PREFERENCE_KEY_ADD_CAMERA_SHORTCUT), true);
            }
        }

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

            if (addCameraShortcuts) {
                if (new File(curInfo.uri.getPath(), "/DCIM/Camera").exists()) {
                    paths.add(pathBuilder.toString() + "/DCIM/Camera");
                    if (storageInfoList.size() > 1) {
                        pathNames.add(context.getString(R.string.sftp_camera) + "(" + curInfo.displayName + ")");
                    } else {
                        pathNames.add(context.getString(R.string.sftp_camera));
                    }
                }
            }
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
    public String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_SFTP_REQUEST};
    }

    @Override
    public String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_SFTP};
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public boolean supportsDeviceSpecificSettings() { return true; }

    @Override
    public void copyGlobalToDeviceSpecificSettings(SharedPreferences globalSharedPreferences) {
        String KeyStorageInfoList = context.getString(PREFERENCE_KEY_STORAGE_INFO_LIST);
        String KeyAddCameraShortcut = context.getString(PREFERENCE_KEY_ADD_CAMERA_SHORTCUT);

        if (this.preferences != null &&
                (!this.preferences.contains(KeyStorageInfoList) || !this.preferences.contains(KeyAddCameraShortcut))) {
            this.preferences
                    .edit()
                    .putString(KeyStorageInfoList, globalSharedPreferences.getString(KeyStorageInfoList, "[]"))
                    .putBoolean(KeyAddCameraShortcut, globalSharedPreferences.getBoolean(KeyAddCameraShortcut, true))
                    .apply();
        }
    }

    @Override
    public void removeSettings(SharedPreferences sharedPreferences) {
        sharedPreferences
                .edit()
                .remove(context.getString(PREFERENCE_KEY_STORAGE_INFO_LIST))
                .remove(context.getString(PREFERENCE_KEY_ADD_CAMERA_SHORTCUT))
                .apply();
    }

    @Override
    public PluginSettingsFragment getSettingsFragment(Activity activity) {
        return SftpSettingsFragment.newInstance(getPluginKey());
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(context.getString(PREFERENCE_KEY_STORAGE_INFO_LIST)) ||
                key.equals(context.getString(PREFERENCE_KEY_ADD_CAMERA_SHORTCUT))) {
            //TODO: There used to be a way to request an un-mount (see desktop SftpPlugin's Mounter::onPackageReceived) but that is not handled anymore by the SftpPlugin on KDE.
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

        @NonNull String displayName;
        @NonNull Uri uri;

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
