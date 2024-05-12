/*
 * SPDX-FileCopyrightText: 2014 The Android Open Source Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.kde.kdeconnect.Plugins.ConnectivityReportPlugin;

import android.os.Build;
import android.telephony.CellInfo;
import android.telephony.SignalStrength;

public class ASUUtils {

    /**
     * Get cdma as level 0..4
     * Adapted from CellSignalStrengthCdma.java
     */
    private static int getCdmaLevel(int cdmaDbm, int cdmaEcio) {
        int levelDbm;
        int levelEcio;

        if (cdmaDbm == CellInfo.UNAVAILABLE) levelDbm = 0;
        else if (cdmaDbm >= -75) levelDbm = 4;
        else if (cdmaDbm >= -85) levelDbm = 3;
        else if (cdmaDbm >= -95) levelDbm = 2;
        else if (cdmaDbm >= -100) levelDbm = 1;
        else levelDbm = 0;

        // Ec/Io are in dB*10
        if (cdmaEcio == CellInfo.UNAVAILABLE) levelEcio = 0;
        else if (cdmaEcio >= -90) levelEcio = 4;
        else if (cdmaEcio >= -110) levelEcio = 3;
        else if (cdmaEcio >= -130) levelEcio = 2;
        else if (cdmaEcio >= -150) levelEcio = 1;
        else levelEcio = 0;

        return Math.min(levelDbm, levelEcio);
    }

    /**
     * Get Evdo as level 0..4
     * Adapted from CellSignalStrengthCdma.java
     */
    private static int getEvdoLevel(int evdoDbm, int evdoSnr) {
        int levelEvdoDbm;
        int levelEvdoSnr;

        if (evdoDbm == CellInfo.UNAVAILABLE) levelEvdoDbm = 0;
        else if (evdoDbm >= -65) levelEvdoDbm = 4;
        else if (evdoDbm >= -75) levelEvdoDbm = 3;
        else if (evdoDbm >= -90) levelEvdoDbm = 2;
        else if (evdoDbm >= -105) levelEvdoDbm = 1;
        else levelEvdoDbm = 0;

        if (evdoSnr == CellInfo.UNAVAILABLE) levelEvdoSnr = 0;
        else if (evdoSnr >= 7) levelEvdoSnr = 4;
        else if (evdoSnr >= 5) levelEvdoSnr = 3;
        else if (evdoSnr >= 3) levelEvdoSnr = 2;
        else if (evdoSnr >= 1) levelEvdoSnr = 1;
        else levelEvdoSnr = 0;

        return Math.min(levelEvdoDbm, levelEvdoSnr);
    }
}
