/*
 * SPDX-FileCopyrightText: 2017 Nicolas Fella <nicolas.fella@gmx.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Plugins.SharePlugin;

import android.content.ComponentName;
import android.content.IntentFilter;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.service.chooser.ChooserTarget;
import android.service.chooser.ChooserTargetService;
import android.util.Log;

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.KdeConnect;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;
import java.util.List;

public class ShareChooserTargetService extends ChooserTargetService {
    @Override
    public List<ChooserTarget> onGetChooserTargets(ComponentName targetActivityName, IntentFilter matchedFilter) {
        Log.d("DirectShare", "invoked");
        final List<ChooserTarget> targets = new ArrayList<>();
        for (Device d : KdeConnect.getInstance().getDevices().values()) {
            if (d.isReachable() && d.isPaired()) {
                Log.d("DirectShare", d.getName());
                final String targetName = d.getName();
                final Icon targetIcon = Icon.createWithResource(this, R.drawable.icon);
                final float targetRanking = 1;
                final ComponentName targetComponentName = new ComponentName(getPackageName(),
                        ShareActivity.class.getCanonicalName());
                final Bundle targetExtras = new Bundle();
                targetExtras.putString("deviceId", d.getDeviceId());
                targets.add(new ChooserTarget(
                        targetName, targetIcon, targetRanking, targetComponentName, targetExtras
                ));
            }
        }

        return targets;
    }
}