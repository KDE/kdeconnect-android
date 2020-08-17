/*
 * SPDX-FileCopyrightText: 2018 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.SharePlugin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.kde.kdeconnect.BackgroundService;

public class ShareBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        switch (intent.getAction()) {
            case SharePlugin.ACTION_CANCEL_SHARE:
                cancelShare(context, intent);
                break;
            default:
                Log.d("ShareBroadcastReceiver", "Unhandled Action received: " + intent.getAction());
        }
    }

    private void cancelShare(Context context, Intent intent) {
        if (!intent.hasExtra(SharePlugin.CANCEL_SHARE_BACKGROUND_JOB_ID_EXTRA) ||
            !intent.hasExtra(SharePlugin.CANCEL_SHARE_DEVICE_ID_EXTRA)) {
            Log.e("ShareBroadcastReceiver", "cancelShare() - not all expected extra's are present. Ignoring this cancel intent");
            return;
        }

        long jobId = intent.getLongExtra(SharePlugin.CANCEL_SHARE_BACKGROUND_JOB_ID_EXTRA, -1);
        String deviceId = intent.getStringExtra(SharePlugin.CANCEL_SHARE_DEVICE_ID_EXTRA);

        BackgroundService.RunWithPlugin(context, deviceId, SharePlugin.class, plugin -> plugin.cancelJob(jobId));
    }
}
