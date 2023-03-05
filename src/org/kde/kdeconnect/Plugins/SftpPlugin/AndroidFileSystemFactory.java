/*
 * SPDX-FileCopyrightText: 2018 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.SftpPlugin;

import android.content.Context;

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
        return new AndroidSafFileSystemView(roots, username.getUsername(), context);
    }
}
