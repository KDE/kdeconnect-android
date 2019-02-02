/*
 * Copyright 2019 Ondřej Hruška <ondra@ondrovo.com>
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

package org.kde.kdeconnect.Plugins.TextInputPlugin;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;

import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect_tp.R;

import androidx.core.content.ContextCompat;

public class TextInputPlugin extends Plugin {

    // re-use the mousepad events, so we don't have to change the desktop software
    public final static String PACKET_TYPE_TEXTINPUT_REQUEST = "kdeconnect.mousepad.request";

    @Override
    public String getDisplayName() {
        return context.getString(R.string.pref_plugin_textinput);
    }

    @Override
    public String getDescription() {
        return context.getString(R.string.pref_plugin_textinput_desc);
    }

    @Override
    public Drawable getIcon() {
        return ContextCompat.getDrawable(context, R.drawable.ic_action_image_edit);
    }

    @Override
    public boolean hasMainActivity() {
        return true;
    }

    @Override
    public void startMainActivity(Activity parentActivity) {
        Intent intent = new Intent(parentActivity, TextInputActivity.class);
        intent.putExtra("deviceId", device.getDeviceId());
        parentActivity.startActivity(intent);
    }

    @Override
    public String[] getSupportedPacketTypes() {
        return new String[0];
    }

    @Override
    public String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_TEXTINPUT_REQUEST};
    }

    @Override
    public String getActionName() {
        return context.getString(R.string.open_textinput);
    }

    public void sendKeyboardPacket(NetworkPacket np) {
        device.sendPacket(np);
    }

}
