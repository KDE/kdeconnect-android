/*
 * SPDX-FileCopyrightText: 2021 David Shlemayev <david.shlemayev@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Plugins.ConnectivityReportPlugin;

public class SubscriptionState {
    final int subId;
    int signalStrength = 0;
    String networkType = "Unknown";

    public SubscriptionState(int subId) {
        this.subId = subId;
    }
}
