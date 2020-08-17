/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Helpers;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.annotation.NonNull;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

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
                            if (mounts == null || StringUtils.containsAny(mounts, path, path2)) {
                                list.add(0, new StorageInfo(path, dir.canWrite(), true, cur_removable_number++));
                                paths.add(path);
                            }
                        }
                    }
                }
            }
        } else {
            //Legacy code for Android < 4.0 that still didn't have /storage
            List<String> entries = new ArrayList<>();
            try (FileReader fileReader = new FileReader("/proc/mounts")) {
                // The reader is buffered internally, so buffering it separately is unnecessary.
                final List<String> lines = IOUtils.readLines(fileReader).stream()
                        .filter(line -> StringUtils.containsAny(line, "vfat", "exfat", "ntfs", "/mnt"))
                        .collect(Collectors.toList());
                for (String line : lines) {
                    if (line.contains("/storage/sdcard"))
                        entries.add(0, line);
                    else
                        entries.add(line);
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
                String[] flags = tokens.nextToken().split(","); //flags
                boolean readonly = ArrayUtils.contains(flags, "ro");

                if (line.contains("/dev/block/vold") && !StringUtils.containsAny(line, "/mnt/secure",
                        "/mnt/asec", "/mnt/obb", "/dev/mapper", "tmpfs")) {
                    paths.add(mount_point);
                    list.add(new StorageInfo(mount_point, readonly, true, cur_removable_number++));
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
