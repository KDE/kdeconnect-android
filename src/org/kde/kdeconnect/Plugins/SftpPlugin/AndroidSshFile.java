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
import android.net.Uri;

import org.apache.sshd.common.file.nativefs.NativeSshFile;
import org.kde.kdeconnect.Helpers.MediaStoreHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;

class AndroidSshFile extends NativeSshFile {
    private final static String TAG = AndroidSshFile.class.getSimpleName();
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
            File parentFile = file.getParentFile();
            File[] children = parentFile.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (file.equals(child)) {
                        exists = true;
                        break;
                    }
                }
            }
        }

        return exists;
    }
}
