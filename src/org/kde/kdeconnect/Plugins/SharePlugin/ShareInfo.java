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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kde.kdeconnect.Plugins.SharePlugin;

import org.kde.kdeconnect.NetworkPacket;

import java.io.OutputStream;

import androidx.documentfile.provider.DocumentFile;

class ShareInfo {
    String fileName;
    long fileSize;
    int currentFileNumber;
    DocumentFile fileDocument;
    NetworkPacket.Payload payload;
    OutputStream outputStream;
    boolean shouldOpen;

    private final Object lock = new Object();   // To protect access to numberOfFiles and totalTransferSize
    private int numberOfFiles;
    private long totalTransferSize;

    int numberOfFiles() {
        synchronized (lock) {
            return numberOfFiles;
        }
    }

    void setNumberOfFiles(int numberOfFiles) {
        synchronized (lock) {
            this.numberOfFiles = numberOfFiles;
        }
    }

    long totalTransferSize() {
        synchronized (lock) {
            return totalTransferSize;
        }
    }

    void setTotalTransferSize(long totalTransferSize) {
        synchronized (lock) {
            this.totalTransferSize = totalTransferSize;
        }
    }
}
