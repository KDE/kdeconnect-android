/*
 * Copyright 2019 Nicolas Fella <nicolas.fella@gmx.de>
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

package org.kde.kdeconnect.Plugins.PhotoPlugin;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import org.kde.kdeconnect.Helpers.FilesHelper;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect_tp.R;

import androidx.core.content.ContextCompat;

@PluginFactory.LoadablePlugin
public class PhotoPlugin extends Plugin {

    private final static String PACKET_TYPE_PHOTO = "kdeconnect.photo";
    private final static String PACKET_TYPE_PHOTO_REQUEST = "kdeconnect.photo.request";

    @Override
    public String getDisplayName() {
        return context.getResources().getString(R.string.take_picture);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(R.string.plugin_photo_desc);
    }

    @Override
    public boolean onPacketReceived(NetworkPacket np) {
        Intent intent = new Intent(context, PhotoActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("deviceId", device.getDeviceId());
        context.startActivity(intent);
        return true;
    }

    void sendPhoto(Uri uri) {
        NetworkPacket np = FilesHelper.uriToNetworkPacket(context, uri, PACKET_TYPE_PHOTO);
        if (np != null) {
            device.sendPacket(np);
        }
    }

    @Override
    public boolean hasMainActivity() {
        return false;
    }

    @Override
    public boolean displayInContextMenu() {
        return false;
    }

    @Override
    public String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_PHOTO_REQUEST};
    }

    @Override
    public String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_PHOTO};
    }

    @Override
    public Drawable getIcon() {
        return ContextCompat.getDrawable(context, R.drawable.ic_camera);
    }

    void sendCancel() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_PHOTO);
        np.set("cancel", true);
        device.sendPacket(np);
    }
}
