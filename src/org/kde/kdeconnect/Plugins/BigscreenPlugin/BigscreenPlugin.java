/*
 * SPDX-FileCopyrightText: 2014 Ahmed I. Khalil <ahmedibrahimkhali@gmail.com>
 * SPDX-FileCopyrightText: 2020 Sylvia van Os <sylvia@hackerchick.me>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Plugins.BigscreenPlugin;


import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.KeyEvent;

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect_tp.R;

import androidx.core.content.ContextCompat;

import static org.kde.kdeconnect.Plugins.MousePadPlugin.KeyListenerView.SpecialKeysMap;

@PluginFactory.LoadablePlugin
public class BigscreenPlugin extends Plugin {

    private final static String PACKET_TYPE_MOUSEPAD_REQUEST = "kdeconnect.mousepad.request";
    private final static String PACKET_TYPE_BIGSCREEN_STT = "kdeconnect.bigscreen.stt";

    @Override
    public boolean isIncompatible() {
        return !device.getDeviceType().equals(Device.DeviceType.Tv) || super.isIncompatible();
    }

    @Override
    public boolean onCreate() {
        optionalPermissionExplanation = R.string.bigscreen_optional_permission_explanation;
        return true;
    }

    @Override
    public String getDisplayName() {
        return context.getString(R.string.pref_plugin_bigscreen);
    }

    @Override
    public String getDescription() {
        return context.getString(R.string.pref_plugin_bigscreen_desc);
    }

    @Override
    public Drawable getIcon() {
        return ContextCompat.getDrawable(context, R.drawable.ic_presenter_24dp);
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
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
        Intent intent = new Intent(parentActivity, BigscreenActivity.class);
        intent.putExtra("deviceId", device.getDeviceId());
        parentActivity.startActivity(intent);
    }

    @Override
    public String[] getSupportedPacketTypes() {  return new String[]{PACKET_TYPE_BIGSCREEN_STT}; }

    @Override
    public String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_MOUSEPAD_REQUEST, PACKET_TYPE_BIGSCREEN_STT};
    }

    @Override
    public String getActionName() {
        return context.getString(R.string.pref_plugin_bigscreen);
    }

    public String[] getOptionalPermissions() {
        return new String[]{Manifest.permission.RECORD_AUDIO};
    }

    public Boolean hasMicPermission() {
        return isPermissionGranted(Manifest.permission.RECORD_AUDIO);
    }


    public void sendLeft() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("specialKey", SpecialKeysMap.get(KeyEvent.KEYCODE_DPAD_LEFT));
        device.sendPacket(np);
    }

    public void sendRight() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("specialKey", SpecialKeysMap.get(KeyEvent.KEYCODE_DPAD_RIGHT));
        device.sendPacket(np);
    }

    public void sendUp() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("specialKey", SpecialKeysMap.get(KeyEvent.KEYCODE_DPAD_UP));
        device.sendPacket(np);
    }

    public void sendDown() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("specialKey", SpecialKeysMap.get(KeyEvent.KEYCODE_DPAD_DOWN));
        device.sendPacket(np);
    }

    public void sendSelect() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("specialKey", SpecialKeysMap.get(KeyEvent.KEYCODE_ENTER));
        device.sendPacket(np);
    }

    public void sendHome() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("alt", true);
        np.set("specialKey", SpecialKeysMap.get(KeyEvent.KEYCODE_F4));
        device.sendPacket(np);
    }

    public void sendSTT(String content) {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_BIGSCREEN_STT);
        np.set("type", "stt");
        np.set("content", content);
        device.sendPacket(np);
    }
}
