/*
 * SPDX-FileCopyrightText: 2014 Ahmed I. Khalil <ahmedibrahimkhali@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Plugins.PresenterPlugin;


import static org.kde.kdeconnect.Plugins.MousePadPlugin.KeyListenerView.SpecialKeysMap;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import org.apache.commons.lang3.ArrayUtils;
import org.kde.kdeconnect.DeviceType;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect_tp.R;

import java.util.Objects;

@PluginFactory.LoadablePlugin
public class PresenterPlugin extends Plugin {

    private final static String PACKET_TYPE_PRESENTER = "kdeconnect.presenter";
    private final static String PACKET_TYPE_MOUSEPAD_REQUEST = "kdeconnect.mousepad.request";

    public boolean isPointerSupported() {
        return getDevice().supportsPacketType(PACKET_TYPE_PRESENTER);
    }

    @Override
    public @NonNull String getDisplayName() {
        return context.getString(R.string.pref_plugin_presenter);
    }

    @Override
    public boolean isCompatible() {
        return !getDevice().getDeviceType().equals(DeviceType.PHONE) && super.isCompatible();
    }

    @Override
    public @NonNull String getDescription() {
        return context.getString(R.string.pref_plugin_presenter_desc);
    }

    @Override
    public @DrawableRes int getIcon() {
        return R.drawable.ic_presenter_24dp;
    }

    @Override
    public boolean hasSettings() {
        return false;
    }

    @Override
    public boolean displayAsButton(Context context) {
        return true;
    }

    @Override
    public void startMainActivity(Activity parentActivity) {
        Intent intent = new Intent(parentActivity, PresenterActivity.class);
        intent.putExtra("deviceId", getDevice().getDeviceId());
        parentActivity.startActivity(intent);
    }

    @Override
    public @NonNull String[] getSupportedPacketTypes() {  return ArrayUtils.EMPTY_STRING_ARRAY; }

    @Override
    public @NonNull String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_MOUSEPAD_REQUEST, PACKET_TYPE_PRESENTER};
    }

    @Override
    public @NonNull String getActionName() {
        return context.getString(R.string.pref_plugin_presenter);
    }

    public void sendNext() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("specialKey", SpecialKeysMap.get(KeyEvent.KEYCODE_PAGE_DOWN));
        getDevice().sendPacket(np);
    }

    public void sendPrevious() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("specialKey", SpecialKeysMap.get(KeyEvent.KEYCODE_PAGE_UP));
        getDevice().sendPacket(np);
    }

    public void sendFullscreen() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("specialKey", SpecialKeysMap.get(KeyEvent.KEYCODE_F5));
        getDevice().sendPacket(np);
    }

    public void sendEsc() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("specialKey", SpecialKeysMap.get(KeyEvent.KEYCODE_ESCAPE));
        getDevice().sendPacket(np);
    }

    public void sendPointer(float xDelta, float yDelta) {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_PRESENTER);
        np.set("dx", xDelta);
        np.set("dy", yDelta);
        getDevice().sendPacket(np);
    }

    public void stopPointer() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_PRESENTER);
        np.set("stop", true);
        getDevice().sendPacket(np);
    }
}
