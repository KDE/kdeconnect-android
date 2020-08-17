/*
 * SPDX-FileCopyrightText: 2018 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
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
