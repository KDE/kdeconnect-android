/*
 * Copyright 2017 Nicolas Fella <nicolas.fella@gmx.de>
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

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.IntentFilter;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.service.chooser.ChooserTarget;
import android.service.chooser.ChooserTargetService;
import android.util.Log;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;
import java.util.List;

@TargetApi(23)
public class ShareChooserTargetService extends ChooserTargetService {
    @Override
    public List<ChooserTarget> onGetChooserTargets(ComponentName targetActivityName, IntentFilter matchedFilter) {
        Log.d("DirectShare", "invoked");
        final List<ChooserTarget> targets = new ArrayList<>();
        for (Device d : BackgroundService.getInstance().getDevices().values()) {
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