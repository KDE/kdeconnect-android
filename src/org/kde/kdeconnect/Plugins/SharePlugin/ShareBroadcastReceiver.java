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
