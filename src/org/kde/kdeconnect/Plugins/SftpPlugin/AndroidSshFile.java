/*
 * SPDX-FileCopyrightText: 2018 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.SftpPlugin;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.apache.commons.io.FileUtils;
import org.apache.sshd.common.file.nativefs.NativeSshFile;
import org.kde.kdeconnect.Helpers.MediaStoreHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;

class AndroidSshFile extends NativeSshFile {
    private static final String TAG = AndroidSshFile.class.getSimpleName();
    final private Context context;
    final private File file;

    AndroidSshFile(final AndroidFileSystemView view, String name, final File file, final String userName, Context context) {
        super(view, name, file, userName);
        this.context = context;
        this.file = file;
    }

    @Override
    public OutputStream createOutputStream(long offset) throws IOException {
        if (!isWritable()) {
            throw new IOException("No write permission : " + file.getName());
        }

        final RandomAccessFile raf = new RandomAccessFile(file, "rw");
        try {
            if (offset < raf.length()) {
                throw new IOException("Your SSHFS is bugged"); //SSHFS 3.0 and 3.2 cause data corruption, abort the transfer if this happens
            }
            raf.setLength(offset);
            raf.seek(offset);

            return new FileOutputStream(raf.getFD()) {
                public void close() throws IOException {
                    super.close();
                    raf.close();
                }
            };
        } catch (IOException e) {
            raf.close();
            throw e;
        }
    }

    @Override
    public boolean delete() {
        boolean ret = super.delete();
        if (ret) {
            MediaStoreHelper.indexFile(context, Uri.fromFile(file));
        }
        return ret;

    }

    @Override
    public boolean create() throws IOException {
        boolean ret = super.create();
        if (ret) {
            MediaStoreHelper.indexFile(context, Uri.fromFile(file));
        }
        return ret;

    }

    // Based on https://github.com/wolpi/prim-ftpd/blob/master/primitiveFTPd/src/org/primftpd/filesystem/FsFile.java
    @Override
    public boolean doesExist() {
        boolean exists = file.exists();

        if (!exists) {
            // file.exists() returns false when we don't have read permission
            // try to figure out if it really does not exist
            try {
                exists = FileUtils.directoryContains(file.getParentFile(), file);
            } catch (IOException | IllegalArgumentException e) {
                // An IllegalArgumentException is thrown if the parent is null or not a directory.
                Log.d(TAG, "Exception: ", e);
            }
        }

        return exists;
    }
}
