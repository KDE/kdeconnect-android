/*
 * SPDX-FileCopyrightText: 2018 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
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
