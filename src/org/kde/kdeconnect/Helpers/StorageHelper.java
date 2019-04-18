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

package org.kde.kdeconnect.Helpers;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.StringTokenizer;

import androidx.annotation.NonNull;

//Code from http://stackoverflow.com/questions/9340332/how-can-i-get-the-list-of-mounted-external-storage-of-android-device/19982338#19982338
//modified to work on Lollipop and other devices
public class StorageHelper {

    public static class StorageInfo {

        public final String path;
        public final boolean readonly;
        public final boolean removable;
        public final int number;

        public StorageInfo(String path, boolean readonly, boolean removable, int number) {
            this.path = path;
            this.readonly = readonly;
            this.removable = removable;
            this.number = number;
        }

    }

    /*
     * This function is bullshit because there is no proper way to do this in Android.
     * Patch after patch I'm making it even more horrible by trying to make it work for *more*
     * devices while trying no to break previously working ones.
     * If this function was a living being, it would be begging "please kill me".
     */
    public static List<StorageInfo> getStorageList() {

        List<StorageInfo> list = new ArrayList<>();
        String def_path = Environment.getExternalStorageDirectory().getPath();
        boolean def_path_removable = Environment.isExternalStorageRemovable();
        String def_path_state = Environment.getExternalStorageState();
        boolean def_path_available = def_path_state.equals(Environment.MEDIA_MOUNTED)
                || def_path_state.equals(Environment.MEDIA_MOUNTED_READ_ONLY);
        boolean def_path_readonly = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED_READ_ONLY);

        HashSet<String> paths = new HashSet<>();
        int cur_removable_number = 1;

        if (def_path_available) {
            paths.add(def_path);
            list.add(0, new StorageInfo(def_path, def_path_readonly, def_path_removable, def_path_removable ? cur_removable_number++ : -1));
        }

        File storage = new File("/storage/");
        if (storage.exists() && storage.isDirectory() && storage.canRead()) {
            String mounts = null;
            try (Scanner scanner = new Scanner(new File("/proc/mounts"))) {
                mounts = scanner.useDelimiter("\\A").next();
            } catch (Exception e) {
                Log.e("StorageHelper", "Exception while getting storageList", e);
            }

            File[] dirs = storage.listFiles();
            for (File dir : dirs) {
                //Log.e("getStorageList", "path: "+dir.getAbsolutePath());
                if (dir.isDirectory() && dir.canRead() && dir.canExecute()) {
                    String path, path2;
                    path2 = dir.getAbsolutePath();
                    try {
                        //Log.e(dir.getAbsolutePath(), dir.getCanonicalPath());
                        path = dir.getCanonicalPath();
                    } catch (Exception e) {
                        path = path2;
                    }
                    if (!path.startsWith("/storage/emulated") || dirs.length == 1) {
                        if (!paths.contains(path) && !paths.contains(path2)) {
                            if (mounts == null || mounts.contains(path) || mounts.contains(path2)) {
                                list.add(0, new StorageInfo(path, dir.canWrite(), true, cur_removable_number++));
                                paths.add(path);
                            }
                        }
                    }
                }
            }
        } else {

            //Legacy code for Android < 4.0 that still didn't have /storage

            ArrayList<String> entries = new ArrayList<>();
            try (BufferedReader buf_reader = new BufferedReader(new FileReader("/proc/mounts"))){
                String entry;
                while ((entry = buf_reader.readLine()) != null) {
                    //Log.e("getStorageList", entry);
                    if (entry.contains("vfat") || entry.contains("exfat") || entry.contains("ntfs") || entry.contains("/mnt")) {
                        if (entry.contains("/storage/sdcard")) entries.add(0, entry);
                        else entries.add(entry);
                    }
                }
            } catch (Exception e) {
                Log.e("StorageHelper", "Exception", e);
            }

            for (String line : entries) {
                StringTokenizer tokens = new StringTokenizer(line, " ");
                tokens.nextToken(); //device
                String mount_point = tokens.nextToken(); //mount point
                if (paths.contains(mount_point)) {
                    continue;
                }
                tokens.nextToken(); //file system
                List<String> flags = Arrays.asList(tokens.nextToken().split(",")); //flags
                boolean readonly = flags.contains("ro");

                if (line.contains("/dev/block/vold")) {
                    if (!line.contains("/mnt/secure")
                            && !line.contains("/mnt/asec")
                            && !line.contains("/mnt/obb")
                            && !line.contains("/dev/mapper")
                            && !line.contains("tmpfs")) {
                        paths.add(mount_point);
                        list.add(new StorageInfo(mount_point, readonly, true, cur_removable_number++));
                    }
                }
            }

        }

        return list;
    }

    /* treeUri                                                                       documentId
     * ==================================================================================================
     * content://com.android.providers.downloads.documents/tree/downloads         => downloads
     * content://com.android.externalstorage.documents/tree/1715-1D1F:            => 1715-1D1F:
     * content://com.android.externalstorage.documents/tree/1715-1D1F:My%20Photos => 1715-1D1F:My Photos
     * content://com.android.externalstorage.documents/tree/primary:              => primary:
     * content://com.android.externalstorage.documents/tree/primary:DCIM          => primary:DCIM
     * content://com.android.externalstorage.documents/tree/primary:Download/bla  => primary:Download/bla
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static String getDisplayName(@NonNull Context context, @NonNull Uri treeUri) {
        List<String> pathSegments = treeUri.getPathSegments();

        if (!pathSegments.get(0).equals("tree")) {
            throw new IllegalArgumentException("treeUri is not valid");
        }

        String documentId = DocumentsContract.getTreeDocumentId(treeUri);

        int colonIdx = pathSegments.get(1).indexOf(':');

        if (colonIdx >= 0) {
            String tree = pathSegments.get(1).substring(0, colonIdx + 1);

            if (!documentId.equals(tree)) {
                return documentId.substring(tree.length());
            } else {
                return documentId.substring(0, colonIdx);
            }
        }

        return documentId;
    }
}
