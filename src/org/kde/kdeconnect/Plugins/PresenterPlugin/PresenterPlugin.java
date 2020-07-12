/*
 * Copyright 2014 Ahmed I. Khalil <ahmedibrahimkhali@gmail.com>
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

package org.kde.kdeconnect.Plugins.PresenterPlugin;


import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.KeyEvent;

import androidx.core.content.ContextCompat;

import org.apache.commons.lang3.ArrayUtils;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect_tp.R;

import static org.kde.kdeconnect.Plugins.MousePadPlugin.KeyListenerView.SpecialKeysMap;

@PluginFactory.LoadablePlugin
public class PresenterPlugin extends Plugin {

    private final static String PACKET_TYPE_PRESENTER = "kdeconnect.presenter";
    private final static String PACKET_TYPE_MOUSEPAD_REQUEST = "kdeconnect.mousepad.request";

    public boolean isPointerSupported() {
        return device.supportsPacketType(PACKET_TYPE_PRESENTER);
    }

    @Override
    public String getDisplayName() {
        return context.getString(R.string.pref_plugin_presenter);
    }

    @Override
    public String getDescription() {
        return context.getString(R.string.pref_plugin_presenter_desc);
    }

    @Override
    public Drawable getIcon() {
        return ContextCompat.getDrawable(context, R.drawable.ic_presenter_24dp);
    }

    @Override
    public boolean hasSettings() {
        return false;
    }

    @Override
    public boolean hasMainActivity() {
        return true;
    }

    @Override
    public void startMainActivity(Activity parentActivity) {
        Intent intent = new Intent(parentActivity, PresenterActivity.class);
        intent.putExtra("deviceId", device.getDeviceId());
        parentActivity.startActivity(intent);
    }

    @Override
    public String[] getSupportedPacketTypes() {  return ArrayUtils.EMPTY_STRING_ARRAY; }

    @Override
    public String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_MOUSEPAD_REQUEST, PACKET_TYPE_PRESENTER};
    }

    @Override
    public String getActionName() {
        return context.getString(R.string.pref_plugin_presenter);
    }

    public void sendNext() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("specialKey", SpecialKeysMap.get(KeyEvent.KEYCODE_PAGE_DOWN));
        device.sendPacket(np);
    }

    public void sendPrevious() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("specialKey", SpecialKeysMap.get(KeyEvent.KEYCODE_PAGE_UP));
        device.sendPacket(np);
    }

    public void sendFullscreen() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("specialKey", SpecialKeysMap.get(KeyEvent.KEYCODE_F5));
        device.sendPacket(np);
    }

    public void sendEsc() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("specialKey", SpecialKeysMap.get(KeyEvent.KEYCODE_ESCAPE));
        device.sendPacket(np);
    }

    public void sendPointer(float xDelta, float yDelta) {
        NetworkPacket np = device.getAndRemoveUnsentPacket(NetworkPacket.PACKET_REPLACEID_PRESENTERPOINTER);
        if (np == null) {
            np = new NetworkPacket(PACKET_TYPE_PRESENTER);
        } else {
            xDelta += np.getInt("dx");
            yDelta += np.getInt("dy");
        }
        np.set("dx", xDelta);
        np.set("dy", yDelta);
        device.sendPacket(np, NetworkPacket.PACKET_REPLACEID_PRESENTERPOINTER);
    }

    public void stopPointer() {
        device.getAndRemoveUnsentPacket(NetworkPacket.PACKET_REPLACEID_PRESENTERPOINTER);
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_PRESENTER);
        np.set("stop", true);
        device.sendPacket(np);
    }
}
