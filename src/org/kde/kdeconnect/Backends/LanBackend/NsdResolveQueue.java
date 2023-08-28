/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Backends.LanBackend;

import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.LinkedList;

public class NsdResolveQueue {

    static final String LOG_TAG = "NsdResolveQueue";

    final @NonNull NsdManager mNsdManager;

    private final Object mLock = new Object();
    private final LinkedList<PendingResolve> mResolveRequests = new LinkedList<>();

    public NsdResolveQueue(NsdManager nsdManager) {
        this.mNsdManager = nsdManager;
    }

    private static class PendingResolve {
        final @NonNull NsdServiceInfo serviceInfo;
        final @NonNull NsdManager.ResolveListener listener;

        private PendingResolve(@NonNull NsdServiceInfo serviceInfo, @NonNull NsdManager.ResolveListener listener) {
            this.serviceInfo = serviceInfo;
            this.listener = listener;
        }
    }

    public void resolveOrEnqueue(@NonNull NsdServiceInfo serviceInfo, @NonNull NsdManager.ResolveListener listener) {
        synchronized (mLock) {
            for (PendingResolve existing : mResolveRequests) {
                if (serviceInfo.getServiceName().equals(existing.serviceInfo.getServiceName())) {
                    Log.i(LOG_TAG, "Not enqueuing a new resolve request for the same service: " + serviceInfo.getServiceName());
                    return;
                }
            }
            mResolveRequests.addLast(new PendingResolve(serviceInfo, new ListenerWrapper(listener)));

            if (mResolveRequests.size() == 1) {
                resolveNextRequest();
            }
        }
    }

    private class ListenerWrapper implements NsdManager.ResolveListener {
        private final @NonNull NsdManager.ResolveListener mListener;

        private ListenerWrapper(@NonNull NsdManager.ResolveListener listener) {
            mListener = listener;
        }

        @Override
        public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
            mListener.onResolveFailed(serviceInfo, errorCode);

            synchronized (mLock) {
                mResolveRequests.pop();
                resolveNextRequest();
            }
        }

        @Override
        public void onServiceResolved(NsdServiceInfo serviceInfo) {
            mListener.onServiceResolved(serviceInfo);

            synchronized (mLock) {
                mResolveRequests.pop();
                resolveNextRequest();
            }
        }
    }

    private void resolveNextRequest() {
        if (!mResolveRequests.isEmpty()) {
            PendingResolve request = mResolveRequests.getFirst();
            mNsdManager.resolveService(request.serviceInfo, request.listener);
        }
    }

}
