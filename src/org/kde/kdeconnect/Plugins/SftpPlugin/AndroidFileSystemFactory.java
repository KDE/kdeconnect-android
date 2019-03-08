/*
 * Copyright 2018 Erik Duisters <e.duisters1@gmail.com>
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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.kde.kdeconnect.Plugins.SftpPlugin;

import android.content.Context;
import android.os.Build;

import org.apache.sshd.common.Session;
import org.apache.sshd.common.file.FileSystemFactory;
import org.apache.sshd.common.file.FileSystemView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class AndroidFileSystemFactory implements FileSystemFactory {
    final private Context context;
    final Map<String, String> roots;

    AndroidFileSystemFactory(Context context) {
        this.context = context;
        this.roots = new HashMap<>();
    }

    void initRoots(List<SftpPlugin.StorageInfo> storageInfoList) {
        for (SftpPlugin.StorageInfo curStorageInfo : storageInfoList) {
            if (curStorageInfo.isFileUri()) {
                if (curStorageInfo.uri.getPath() != null){
                    roots.put(curStorageInfo.displayName, curStorageInfo.uri.getPath());
                }
            } else if (curStorageInfo.isContentUri()){
                roots.put(curStorageInfo.displayName, curStorageInfo.uri.toString());
            }
        }
    }

    @Override
    public FileSystemView createFileSystemView(final Session username) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            if (roots.size() == 0) {
                throw new RuntimeException("roots cannot be empty");
            }

            String[] rootsAsString = new String[roots.size()];
            roots.keySet().toArray(rootsAsString);

            return new AndroidFileSystemView(roots, rootsAsString[0], username.getUsername(), context);
        } else {
            return new AndroidSafFileSystemView(roots, username.getUsername(), context);
        }
    }
}
