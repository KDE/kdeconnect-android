/*
 * SPDX-FileCopyrightText: 2020 Aniket Kumar <anikketkumar786@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.SMSPlugin;

import android.content.Intent;
import android.app.Service;
import android.os.IBinder;

/**
 * Service for sending messages to a conversation without a UI present. These messages could come
 * from something like Phone, needed to make default sms app
 */
public class HeadlessSmsSendService extends Service {

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}