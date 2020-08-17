/*
 * SPDX-FileCopyrightText: 2018 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.SftpPlugin;

import android.content.Context;

import org.apache.sshd.common.file.FileSystemView;
import org.apache.sshd.common.file.SshFile;
import org.apache.sshd.common.file.nativefs.NativeFileSystemView;
import org.apache.sshd.common.file.nativefs.NativeSshFile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class AndroidFileSystemView extends NativeFileSystemView {
    final private String userName;
    final private Context context;
    private final Map<String, String> roots;
    private final RootFile rootFile;

    AndroidFileSystemView(Map<String, String> roots, String currentRoot, final String userName, Context context) {
        super(userName, roots, currentRoot, File.separatorChar, true);
        this.roots = roots;
        this.userName = userName;
        this.context = context;
        this.rootFile = new RootFile( createFileList(), userName, true);
    }

    private List<SshFile> createFileList() {
        List<SshFile> list = new ArrayList<>();
        for (Map.Entry<String, String> entry : roots.entrySet()) {
            String displayName = entry.getKey();
            String path = entry.getValue();

            list.add(createNativeSshFile(displayName, new File(path), userName));
        }

        return list;
    }

    @Override
    public SshFile getFile(String file) {
        return getFile("/", file);
    }

    @Override
    public SshFile getFile(SshFile baseDir, String file) {
        return getFile(baseDir.getAbsolutePath(), file);
    }

    @Override
    protected SshFile getFile(String dir, String file) {
        if (!dir.endsWith("/")) {
            dir = dir + "/";
        }

        if (!file.startsWith("/")) {
            file = dir + file;
        }

        String filename = NativeSshFile.getPhysicalName("/", "/", file, false);

        if (filename.equals("/")) {
            return rootFile;
        }

        for (String root : roots.keySet()) {
            if (filename.indexOf(root) == 1) {
                String nameWithoutRoot = filename.substring(root.length() + 1);
                String path = roots.get(root);

                if (nameWithoutRoot.isEmpty()) {
                    return createNativeSshFile(filename, new File(path), userName);
                } else {
                    return createNativeSshFile(filename, new File(path, nameWithoutRoot), userName);
                }
            }
        }

        //It's a file under / but not one covered by any Tree
        return new RootFile(new ArrayList<>(0), userName, false);
    }

    // NativeFileSystemView.getFile(), NativeSshFile.getParentFile() and NativeSshFile.listSshFiles() call
    // createNativeSshFile to create new NativeSshFiles so override that instead of getFile() to always create an AndroidSshFile
    @Override
    public AndroidSshFile createNativeSshFile(String name, File file, String username) {
        return new AndroidSshFile(this, name, file, username, context);
    }

    @Override
    public FileSystemView getNormalizedView() {
        return this;
    }
}
