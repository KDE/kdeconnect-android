/*
 * SPDX-FileCopyrightText: 2018 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.async;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import androidx.annotation.Nullable;

/**
 * Scheduler for {@link BackgroundJob} objects.
 * <p>
 *     We use an internal {@link ThreadPoolExecutor} to catch Exceptions and
 *     pass them along to {@link #handleUncaughtException(Future, Throwable)}.
 * </p>
 */
public class BackgroundJobHandler {
    private static final String TAG = BackgroundJobHandler.class.getSimpleName();

    private final Map<BackgroundJob, Future<?>> jobMap = new HashMap<>();
    private final Object jobMapLock = new Object();

    private class MyThreadPoolExecutor extends ThreadPoolExecutor {
        MyThreadPoolExecutor(int corePoolSize, int maxPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
            super(corePoolSize, maxPoolSize, keepAliveTime, unit, workQueue);
        }

        @Override
        protected void afterExecute(Runnable r, Throwable t) {
            super.afterExecute(r, t);

            if (!(r instanceof Future)) {
                return;
            }

            Future<?> future = (Future<?>) r;

            if (t == null) {
                try {
                    future.get();
                } catch (CancellationException ce) {
                    Log.d(TAG,"afterExecute got a CancellationException");
                } catch (ExecutionException ee) {
                    t = ee;
                } catch (InterruptedException ie) {
                    Log.d(TAG, "afterExecute got an InterruptedException");
                    Thread.currentThread().interrupt();    // ignore/reset
                }
            }

            if (t != null) {
                BackgroundJobHandler.this.handleUncaughtException(future, t);
            }
        }
    }

    private final ThreadPoolExecutor threadPoolExecutor;
    private Handler handler;

    private BackgroundJobHandler(int corePoolSize, int maxPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        this.handler = new Handler(Looper.getMainLooper());
        this.threadPoolExecutor = new MyThreadPoolExecutor(corePoolSize, maxPoolSize, keepAliveTime, unit, workQueue);
    }

    public void runJob(BackgroundJob bgJob) {
        Future<?> f;

        bgJob.setBackgroundJobHandler(this);

        try {
            synchronized (jobMapLock) {
                f = threadPoolExecutor.submit(bgJob);
                jobMap.put(bgJob, f);
            }
        } catch (RejectedExecutionException e) {
            Log.d(TAG,"threadPoolExecutor.submit rejected a background job: " + e.getMessage());

            bgJob.reportError(e);
        }
    }

    public boolean isRunning(long jobId) {
        synchronized (jobMapLock) {
            for (BackgroundJob job : jobMap.keySet()) {
                if (job.getId() == jobId) {
                    return true;
                }
            }
        }

        return false;
    }

    @Nullable
    public BackgroundJob getJob(long jobId) {
        synchronized (jobMapLock) {
            for (BackgroundJob job : jobMap.keySet()) {
                if (job.getId() == jobId) {
                    return job;
                }
            }
        }

        return null;
    }

    void cancelJob(BackgroundJob job) {
        synchronized (jobMapLock) {
            if (jobMap.containsKey(job)) {
                Future<?> f = jobMap.get(job);

                if (f.cancel(true)) {
                    threadPoolExecutor.purge();
                }

                jobMap.remove(job);
            }
        }
    }

    private void handleUncaughtException(Future<?> ft, Throwable t) {
        synchronized (jobMapLock) {
            for (Map.Entry<BackgroundJob, Future<?>> pairs : jobMap.entrySet()) {
                Future<?> future = pairs.getValue();

                if (future == ft) {
                    pairs.getKey().reportError(t);
                    break;
                }
            }
        }
    }

    void onFinished(BackgroundJob job) {
        synchronized (jobMapLock) {
            jobMap.remove(job);
        }
    }

    void runOnUiThread(Runnable runnable) {
        handler.post(runnable);
    }

    public static BackgroundJobHandler newFixedThreadPoolBackgroundJobHander(int numThreads) {
        return new BackgroundJobHandler(numThreads, numThreads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    }
}
