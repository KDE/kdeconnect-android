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

package org.kde.kdeconnect.async;

import java.util.concurrent.atomic.AtomicLong;

import androidx.annotation.NonNull;

public abstract class BackgroundJob<I, R> implements Runnable {
    private static AtomicLong atomicLong = new AtomicLong(0);
    protected volatile boolean canceled;
    private BackgroundJobHandler backgroundJobHandler;
    private long id;

    protected I requestInfo;
    private Callback<R> callback;

    public BackgroundJob(I requestInfo, Callback<R> callback) {
        this.id = atomicLong.incrementAndGet();
        this.requestInfo = requestInfo;
        this.callback = callback;
    }

    void setBackgroundJobHandler(BackgroundJobHandler handler) {
        this.backgroundJobHandler = handler;
    }

    public long getId() { return id; }
    public I getRequestInfo() { return requestInfo; }

    public void cancel() {
        canceled = true;
        backgroundJobHandler.cancelJob(this);
    }

    public boolean isCancelled() {
        return canceled;
    }

    public interface Callback<R> {
        void onResult(@NonNull BackgroundJob job, R result);
        void onError(@NonNull BackgroundJob job, @NonNull Throwable error);
    }

    protected void reportResult(R result) {
        backgroundJobHandler.runOnUiThread(() -> {
            callback.onResult(this, result);
            backgroundJobHandler.onFinished(this);
        });
    }

    protected void reportError(@NonNull Throwable error) {
        backgroundJobHandler.runOnUiThread(() -> {
            callback.onError(this, error);
            backgroundJobHandler.onFinished(this);
        });
    }
}
