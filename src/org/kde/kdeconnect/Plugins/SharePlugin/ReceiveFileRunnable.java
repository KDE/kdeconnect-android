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

import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.io.InputStream;

public class ReceiveFileRunnable implements Runnable {
    interface CallBack {
        void onProgress(ShareInfo info, int progress);
        void onSuccess(ShareInfo info);
        void onError(ShareInfo info, Throwable error);
    }

    private final ShareInfo info;
    private final CallBack callBack;
    private final Handler handler;

    ReceiveFileRunnable(ShareInfo info, CallBack callBack) {
        this.info = info;
        this.callBack = callBack;
        this.handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void run() {
        try {
            byte data[] = new byte[4096];
            long received = 0, prevProgressPercentage = 0;
            int count;

            callBack.onProgress(info, 0);

            InputStream inputStream = info.payload.getInputStream();

            while ((count = inputStream.read(data)) >= 0) {
                received += count;

                if (received > info.fileSize) {
                    break;
                }

                info.outputStream.write(data, 0, count);
                if (info.fileSize > 0) {
                    long progressPercentage = (received * 100 / info.fileSize);
                    if (progressPercentage != prevProgressPercentage) {
                        prevProgressPercentage = progressPercentage;
                        handler.post(() -> callBack.onProgress(info, (int)progressPercentage));
                    }
                }
                //else Log.e("SharePlugin", "Infinite loop? :D");
            }

            info.outputStream.flush();

            if (received != info.fileSize) {
                throw new RuntimeException("Received:" + received + " bytes, expected: " + info.fileSize + " bytes");
            }

            handler.post(() -> callBack.onSuccess(info));
        } catch (IOException e) {
            handler.post(() -> callBack.onError(info, e));
        } finally {
            info.payload.close();

            try {
                info.outputStream.close();
            } catch (IOException ignored) {}
        }
    }
}
