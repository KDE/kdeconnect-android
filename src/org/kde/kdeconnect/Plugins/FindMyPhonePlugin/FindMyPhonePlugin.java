/*
 * Copyright 2015 David Edmundson <david@davidedmundson.co.uk>
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

package org.kde.kdeconnect.Plugins.FindMyPhonePlugin;

import android.app.Activity;
import android.content.Intent;

import org.kde.kdeconnect.Helpers.DeviceHelper;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect.UserInterface.PluginSettingsFragment;
import org.kde.kdeconnect_tp.R;

@PluginFactory.LoadablePlugin
public class FindMyPhonePlugin extends Plugin {

    public final static String PACKET_TYPE_FINDMYPHONE_REQUEST = "kdeconnect.findmyphone.request";

    @Override
    public String getDisplayName() {
        switch (DeviceHelper.getDeviceType(context)) {
            case Tv:
                return context.getString(R.string.findmyphone_title_tv);
            case Tablet:
                return context.getString(R.string.findmyphone_title_tablet);
            case Phone:
                return context.getString(R.string.findmyphone_title);
            default:
                return context.getString(R.string.findmyphone_title);
        }
    }

    @Override
    public String getDescription() {
        return context.getString(R.string.findmyphone_description);
    }

    @Override
    public boolean onPacketReceived(NetworkPacket np) {

        Intent intent = new Intent(context, FindMyPhoneActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return true;

    }

    @Override
    public String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_FINDMYPHONE_REQUEST};
    }

    @Override
    public String[] getOutgoingPacketTypes() {
        return new String[0];
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public PluginSettingsFragment getSettingsFragment(Activity activity) {
        return FindMyPhoneSettingsFragment.newInstance(getPluginKey());
    }
}
