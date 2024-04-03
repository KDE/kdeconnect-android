/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.FindRemoteDevicePlugin;

import android.app.Activity;

import androidx.annotation.NonNull;

import org.apache.commons.lang3.ArrayUtils;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.FindMyPhonePlugin.FindMyPhonePlugin;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect_tp.R;

@PluginFactory.LoadablePlugin
public class FindRemoteDevicePlugin extends Plugin {

    @Override
    public @NonNull String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_findremotedevice);
    }

    @Override
    public @NonNull String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_findremotedevice_desc);
    }

    @Override
    public boolean onPacketReceived(@NonNull NetworkPacket np) {
        return true;
    }

    @Override
    public @NonNull String getActionName() {
        return context.getString(R.string.ring);
    }

    @Override
    public void startMainActivity(Activity activity) {
        if (isDeviceInitialized()) {
            getDevice().sendPacket(new NetworkPacket(FindMyPhonePlugin.PACKET_TYPE_FINDMYPHONE_REQUEST));
        }
    }

    @Override
    public boolean displayInContextMenu() {
        return true;
    }

    @Override
    public @NonNull String[] getSupportedPacketTypes() {
        return ArrayUtils.EMPTY_STRING_ARRAY;
    }

    @Override
    public @NonNull String[] getOutgoingPacketTypes() {
        return new String[]{FindMyPhonePlugin.PACKET_TYPE_FINDMYPHONE_REQUEST};
    }
}
