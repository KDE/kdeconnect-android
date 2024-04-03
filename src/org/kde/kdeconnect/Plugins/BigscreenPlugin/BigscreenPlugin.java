/*
 * SPDX-FileCopyrightText: 2014 Ahmed I. Khalil <ahmedibrahimkhali@gmail.com>
 * SPDX-FileCopyrightText: 2020 Sylvia van Os <sylvia@hackerchick.me>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Plugins.BigscreenPlugin;


import static org.kde.kdeconnect.Plugins.MousePadPlugin.KeyListenerView.SpecialKeysMap;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import org.kde.kdeconnect.DeviceType;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect_tp.R;

import java.util.Objects;

@PluginFactory.LoadablePlugin
public class BigscreenPlugin extends Plugin {

    private final static String PACKET_TYPE_MOUSEPAD_REQUEST = "kdeconnect.mousepad.request";
    private final static String PACKET_TYPE_BIGSCREEN_STT = "kdeconnect.bigscreen.stt";

    @Override
    public boolean isCompatible() {
        return getDevice().getDeviceType().equals(DeviceType.TV) && super.isCompatible();
    }

    @Override
    protected int getOptionalPermissionExplanation() {
        return R.string.bigscreen_optional_permission_explanation;
    }

    @Override
    public @NonNull String getDisplayName() {
        return context.getString(R.string.pref_plugin_bigscreen);
    }

    @Override
    public @NonNull String getDescription() {
        return context.getString(R.string.pref_plugin_bigscreen_desc);
    }

    @Override
    public @DrawableRes int getIcon() {
        return R.drawable.ic_presenter_24dp;
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
    public boolean displayAsButton(Context context) {
        return true;
    }

    @Override
    public void startMainActivity(Activity parentActivity) {
        Intent intent = new Intent(parentActivity, BigscreenActivity.class);
        intent.putExtra("deviceId", getDevice().getDeviceId());
        parentActivity.startActivity(intent);
    }

    @Override
    public @NonNull String[] getSupportedPacketTypes() {  return new String[]{PACKET_TYPE_BIGSCREEN_STT}; }

    @Override
    public @NonNull String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_MOUSEPAD_REQUEST, PACKET_TYPE_BIGSCREEN_STT};
    }

    @Override
    public @NonNull String getActionName() {
        return context.getString(R.string.pref_plugin_bigscreen);
    }

    public @NonNull String[] getOptionalPermissions() {
        return new String[]{Manifest.permission.RECORD_AUDIO};
    }

    public Boolean hasMicPermission() {
        return isPermissionGranted(Manifest.permission.RECORD_AUDIO);
    }


    public void sendLeft() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("specialKey", SpecialKeysMap.get(KeyEvent.KEYCODE_DPAD_LEFT));
        getDevice().sendPacket(np);
    }

    public void sendRight() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("specialKey", SpecialKeysMap.get(KeyEvent.KEYCODE_DPAD_RIGHT));
        getDevice().sendPacket(np);
    }

    public void sendUp() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("specialKey", SpecialKeysMap.get(KeyEvent.KEYCODE_DPAD_UP));
        getDevice().sendPacket(np);
    }

    public void sendDown() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("specialKey", SpecialKeysMap.get(KeyEvent.KEYCODE_DPAD_DOWN));
        getDevice().sendPacket(np);
    }

    public void sendSelect() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("specialKey", SpecialKeysMap.get(KeyEvent.KEYCODE_ENTER));
        getDevice().sendPacket(np);
    }

    public void sendHome() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("alt", true);
        np.set("specialKey", SpecialKeysMap.get(KeyEvent.KEYCODE_F4));
        getDevice().sendPacket(np);
    }

    public void sendSTT(String content) {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_BIGSCREEN_STT);
        np.set("type", "stt");
        np.set("content", content);
        getDevice().sendPacket(np);
    }
}
