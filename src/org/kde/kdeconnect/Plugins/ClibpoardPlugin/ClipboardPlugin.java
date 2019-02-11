/*
 * Copyright 2014 Albert Vaca Cintora <albertvaka@gmail.com>
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

package org.kde.kdeconnect.Plugins.ClibpoardPlugin;

import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect_tp.R;

@PluginFactory.LoadablePlugin
public class ClipboardPlugin extends Plugin {

    private final static String PACKET_TYPE_CLIPBOARD = "kdeconnect.clipboard";

    @Override
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_clipboard);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_clipboard_desc);
    }

    @Override
    public boolean onPacketReceived(NetworkPacket np) {
        String content = np.getString("content");
        ClipboardListener.instance(context).setText(content);
        return true;
    }

    private final ClipboardListener.ClipboardObserver observer = content -> {
        NetworkPacket np = new NetworkPacket(ClipboardPlugin.PACKET_TYPE_CLIPBOARD);
        np.set("content", content);
        device.sendPacket(np);
    };

    @Override
    public boolean onCreate() {
        ClipboardListener.instance(context).registerObserver(observer);
        return true;
    }

    @Override
    public void onDestroy() {
        ClipboardListener.instance(context).removeObserver(observer);
    }

    @Override
    public String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_CLIPBOARD};
    }

    @Override
    public String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_CLIPBOARD};
    }


}
