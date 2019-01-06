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

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Helpers.FilesHelper;
import org.kde.kdeconnect.Helpers.MediaStoreHelper;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect_tp.R;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;

public class CompositeReceiveFileRunnable implements Runnable {
    interface CallBack {
        void onSuccess(CompositeReceiveFileRunnable runnable);
        void onError(CompositeReceiveFileRunnable runnable, Throwable error);
    }

    private final Device device;
    private final ShareNotification shareNotification;
    private NetworkPacket currentNetworkPacket;
    private String currentFileName;
    private int currentFileNum;
    private long totalReceived;
    private long lastProgressTimeMillis;
    private long prevProgressPercentage;

    private final CallBack callBack;
    private final Handler handler;

    private final Object lock;                              //Use to protect concurrent access to the variables below
    private final List<NetworkPacket> networkPacketList;
    private int totalNumFiles;
    private long totalPayloadSize;

    CompositeReceiveFileRunnable(Device device, CallBack callBack) {
        this.device = device;
        this.callBack = callBack;

        lock = new Object();
        networkPacketList = new ArrayList<>();
        shareNotification = new ShareNotification(device);
        currentFileNum = 0;
        totalNumFiles = 0;
        totalPayloadSize = 0;
        totalReceived = 0;
        lastProgressTimeMillis = 0;
        prevProgressPercentage = 0;
        handler = new Handler(Looper.getMainLooper());
    }

    void addNetworkPacket(NetworkPacket networkPacket) {
        if (!networkPacketList.contains(networkPacket)) {
            synchronized (lock) {
                networkPacketList.add(networkPacket);

                totalNumFiles = networkPacket.getInt(SharePlugin.KEY_NUMBER_OF_FILES, 1);
                totalPayloadSize = networkPacket.getLong(SharePlugin.KEY_TOTAL_PAYLOAD_SIZE);

                shareNotification.setTitle(device.getContext().getResources()
                        .getQuantityString(R.plurals.incoming_file_title, totalNumFiles, totalNumFiles, device.getName()));
            }
        }
    }

    @Override
    public void run() {
        boolean done;
        OutputStream outputStream = null;

        synchronized (lock) {
            done = networkPacketList.isEmpty();
        }

        try {
            DocumentFile fileDocument = null;

            while (!done) {
                synchronized (lock) {
                    currentNetworkPacket = networkPacketList.get(0);
                }
                currentFileName = currentNetworkPacket.getString("filename", Long.toString(System.currentTimeMillis()));
                currentFileNum++;

                setProgress((int)prevProgressPercentage);

                fileDocument = getDocumentFileFor(currentFileName, currentNetworkPacket.getBoolean("open"));

                if (currentNetworkPacket.hasPayload()) {
                    outputStream = new BufferedOutputStream(device.getContext().getContentResolver().openOutputStream(fileDocument.getUri()));
                    InputStream inputStream = currentNetworkPacket.getPayload().getInputStream();

                    long received = receiveFile(inputStream, outputStream);

                    currentNetworkPacket.getPayload().close();

                    if ( received != currentNetworkPacket.getPayloadSize()) {
                        fileDocument.delete();
                        throw new RuntimeException("Failed to receive: " + currentFileName + " received:" + received + " bytes, expected: " + currentNetworkPacket.getPayloadSize() + " bytes");
                    } else {
                        publishFile(fileDocument, received);
                    }
                } else {
                    setProgress(100);
                    publishFile(fileDocument, 0);
                }

                boolean listIsEmpty;

                synchronized (lock) {
                    networkPacketList.remove(0);
                    listIsEmpty = networkPacketList.isEmpty();
                }

                if (listIsEmpty) {
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException ignored) {}

                    synchronized (lock) {
                        if (currentFileNum < totalNumFiles && networkPacketList.isEmpty()) {
                            throw new RuntimeException("Failed to receive " + (totalNumFiles - currentFileNum + 1) + " files");
                        }
                    }
                }

                synchronized (lock) {
                    done = networkPacketList.isEmpty();
                }
            }

            int numFiles;
            synchronized (lock) {
                numFiles = totalNumFiles;
            }

            if (numFiles == 1 && currentNetworkPacket.has("open")) {
                shareNotification.cancel();
                openFile(fileDocument);
            } else {
                //Update the notification and allow to open the file from it
                shareNotification.setFinished(device.getContext().getResources().getQuantityString(R.plurals.received_files_title, numFiles, device.getName(), numFiles));

                if (totalNumFiles == 1 && fileDocument != null) {
                    shareNotification.setURI(fileDocument.getUri(), fileDocument.getType(), fileDocument.getName());
                }

                shareNotification.show();
            }
            handler.post(() -> callBack.onSuccess(this));
        } catch (Exception e) {
            int failedFiles;
            synchronized (lock) {
                failedFiles = (totalNumFiles - currentFileNum + 1);
            }
            shareNotification.setFinished(device.getContext().getResources().getQuantityString(R.plurals.received_files_fail_title, failedFiles, device.getName(), failedFiles, totalNumFiles));
            shareNotification.show();
            handler.post(() -> callBack.onError(this, e));
        } finally {
            closeAllInputStreams();
            networkPacketList.clear();
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException ignored) {}
            }
        }
    }

    private DocumentFile getDocumentFileFor(final String filename, final boolean open) throws RuntimeException {
        final DocumentFile destinationFolderDocument;

        String filenameToUse = filename;

        //We need to check for already existing files only when storing in the default path.
        //User-defined paths use the new Storage Access Framework that already handles this.
        //If the file should be opened immediately store it in the standard location to avoid the FileProvider trouble (See ShareNotification::setURI)
        if (open || !ShareSettingsFragment.isCustomDestinationEnabled(device.getContext())) {
            final String defaultPath = ShareSettingsFragment.getDefaultDestinationDirectory().getAbsolutePath();
            filenameToUse = FilesHelper.findNonExistingNameForNewFile(defaultPath, filenameToUse);
            destinationFolderDocument = DocumentFile.fromFile(new File(defaultPath));
        } else {
            destinationFolderDocument = ShareSettingsFragment.getDestinationDirectory(device.getContext());
        }
        String displayName = FilesHelper.getFileNameWithoutExt(filenameToUse);
        String mimeType = FilesHelper.getMimeTypeFromFile(filenameToUse);

        if ("*/*".equals(mimeType)) {
            displayName = filenameToUse;
        }

        DocumentFile fileDocument = destinationFolderDocument.createFile(mimeType, displayName);

        if (fileDocument == null) {
            throw new RuntimeException(device.getContext().getString(R.string.cannot_create_file, filenameToUse));
        }

        return fileDocument;
    }

    private long receiveFile(InputStream input, OutputStream output) throws IOException {
        byte data[] = new byte[4096];
        int count;
        long received = 0;

        while ((count = input.read(data)) >= 0) {
            received += count;
            totalReceived += count;

            output.write(data, 0, count);

            long progressPercentage;
            synchronized (lock) {
                progressPercentage = (totalReceived * 100 / totalPayloadSize);
            }
            long curTimeMillis = System.currentTimeMillis();

            if (progressPercentage != prevProgressPercentage &&
                    (progressPercentage == 100 || curTimeMillis - lastProgressTimeMillis >= 500)) {
                prevProgressPercentage = progressPercentage;
                lastProgressTimeMillis = curTimeMillis;
                setProgress((int)progressPercentage);
            }
        }

        output.flush();

        return received;
    }

    private void closeAllInputStreams() {
        for (NetworkPacket np : networkPacketList) {
            np.getPayload().close();
        }
    }

    private void setProgress(int progress) {
        synchronized (lock) {
            shareNotification.setProgress(progress, device.getContext().getResources()
                    .getQuantityString(R.plurals.incoming_files_text, totalNumFiles, currentFileName, currentFileNum, totalNumFiles));
        }
        shareNotification.show();
    }

    private void publishFile(DocumentFile fileDocument, long size) {
        if (!ShareSettingsFragment.isCustomDestinationEnabled(device.getContext())) {
            Log.i("SharePlugin", "Adding to downloads");
            DownloadManager manager = (DownloadManager) device.getContext().getSystemService(Context.DOWNLOAD_SERVICE);
            manager.addCompletedDownload(fileDocument.getUri().getLastPathSegment(), device.getName(), true, fileDocument.getType(), fileDocument.getUri().getPath(), size, false);
        } else {
            //Make sure it is added to the Android Gallery anyway
            Log.i("SharePlugin", "Adding to gallery");
            MediaStoreHelper.indexFile(device.getContext(), fileDocument.getUri());
        }
    }

    private void openFile(DocumentFile fileDocument) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        if (Build.VERSION.SDK_INT >= 24) {
            //Nougat and later require "content://" uris instead of "file://" uris
            File file = new File(fileDocument.getUri().getPath());
            Uri contentUri = FileProvider.getUriForFile(device.getContext(), "org.kde.kdeconnect_tp.fileprovider", file);
            intent.setDataAndType(contentUri, fileDocument.getType());
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            intent.setDataAndType(fileDocument.getUri(), fileDocument.getType());
        }

        device.getContext().startActivity(intent);
    }
}
