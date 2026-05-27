/*
 * SPDX-FileCopyrightText: 2026 Albert Vaca Cintora <wolff@julianwolff.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins.notifications

import android.app.PendingIntent
import android.app.RemoteInput
import java.util.UUID

internal data class RepliableNotification(
    val id: String = UUID.randomUUID().toString(),
    var pendingIntent: PendingIntent,
    val remoteInputs: List<RemoteInput>,
    var packageName: String,
)