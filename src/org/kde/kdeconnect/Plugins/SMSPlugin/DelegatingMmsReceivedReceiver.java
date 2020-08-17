/*
 * SPDX-FileCopyrightText: 2020 Aniket Kumar <anikketkumar786@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.SMSPlugin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * A small BroadcastReceiver wrapper for MMSReceivedReceiver to load user preferences
 */
public class DelegatingMmsReceivedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        MmsReceivedReceiver delegate = new MmsReceivedReceiver();

        delegate.getPreferredApn(context, intent);
        delegate.onReceive(context, intent);
    }
}
