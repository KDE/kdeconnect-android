/*
 * SPDX-FileCopyrightText: 2017 Julian Wolff <wolff@julianwolff.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.plugins.notifications;

import android.app.PendingIntent;

import java.util.ArrayList;
import java.util.UUID;

class RepliableNotification {
    final String id = UUID.randomUUID().toString();
    PendingIntent pendingIntent;
    final ArrayList<android.app.RemoteInput> remoteInputs = new ArrayList<>();
    String packageName;
    String tag;
}
