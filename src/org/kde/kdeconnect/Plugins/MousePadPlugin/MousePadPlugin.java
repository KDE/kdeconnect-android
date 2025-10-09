/*
 * SPDX-FileCopyrightText: 2014 Ahmed I. Khalil <ahmedibrahimkhali@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.MousePadPlugin;

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
import org.kde.kdeconnect.UserInterface.PluginSettingsFragment;
import org.kde.kdeconnect_tp.R;

@PluginFactory.LoadablePlugin
public class MousePadPlugin extends Plugin {

    public final static String PACKET_TYPE_MOUSEPAD_REQUEST = "kdeconnect.mousepad.request";
    private final static String PACKET_TYPE_MOUSEPAD_KEYBOARDSTATE = "kdeconnect.mousepad.keyboardstate";

    private boolean keyboardEnabled = true;

    @Override
    public boolean onPacketReceived(@NonNull NetworkPacket np) {

        keyboardEnabled = np.getBoolean("state", true);

        return true;
    }

    @Override
    public @NonNull String getDisplayName() {
        return context.getString(R.string.pref_plugin_mousepad);
    }

    @Override
    public @NonNull String getDescription() {
        return context.getString(R.string.pref_plugin_mousepad_desc_nontv);
    }

    @Override
    public @DrawableRes int getIcon() {
        return R.drawable.touchpad_plugin_action_24dp;
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public PluginSettingsFragment getSettingsFragment(Activity activity) {
        if (device.getDeviceType() == DeviceType.TV) {
            return PluginSettingsFragment.newInstance(getPluginKey(), R.xml.mousepadplugin_preferences, R.xml.mousepadplugin_preferences_tv);
        } else {
            return PluginSettingsFragment.newInstance(getPluginKey(), R.xml.mousepadplugin_preferences);
        }
    }

    @Override
    public boolean displayAsButton(Context context) {
        return true;
    }

    @Override
    public void startMainActivity(Activity parentActivity) {
        if (device.getDeviceType() == DeviceType.TV) {
            Intent intent = new Intent(parentActivity, BigscreenActivity.class);
            intent.putExtra("deviceId", device.getDeviceId());
            parentActivity.startActivity(intent);
        } else {
            Intent intent = new Intent(parentActivity, MousePadActivity.class);
            intent.putExtra("deviceId", device.getDeviceId());
            parentActivity.startActivity(intent);
        }
    }

    @Override
    public @NonNull String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_MOUSEPAD_KEYBOARDSTATE};
    }

    @Override
    public @NonNull String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_MOUSEPAD_REQUEST};
    }

    @Override
    public @NonNull String getActionName() {
        return context.getString(R.string.open_mousepad);
    }

    public void sendMouseDelta(float dx, float dy) {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("dx", dx);
        np.set("dy", dy);
        sendPacket(np);
    }

    public Boolean hasMicPermission() {
        return isPermissionGranted(Manifest.permission.RECORD_AUDIO);
    }

    public void sendLeftClick() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("singleclick", true);
        sendPacket(np);
    }

    public void sendDoubleClick() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("doubleclick", true);
        sendPacket(np);
    }

    public void sendMiddleClick() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("middleclick", true);
        sendPacket(np);
    }

    public void sendRightClick() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("rightclick", true);
        sendPacket(np);
    }

    public void sendSingleHold() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("singlehold", true);
        sendPacket(np);
    }

    public void sendSingleRelease() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("singlerelease", true);
        sendPacket(np);
    }

    public void sendScroll(float dx, float dy) {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("scroll", true);
        np.set("dx", dx);
        np.set("dy", dy);
        sendPacket(np);
    }

    public void sendLeft() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("specialKey", SpecialKeysMap.get(KeyEvent.KEYCODE_DPAD_LEFT));
        sendPacket(np);
    }

    public void sendRight() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("specialKey", SpecialKeysMap.get(KeyEvent.KEYCODE_DPAD_RIGHT));
        sendPacket(np);
    }

    public void sendUp() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("specialKey", SpecialKeysMap.get(KeyEvent.KEYCODE_DPAD_UP));
        sendPacket(np);
    }

    public void sendDown() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("specialKey", SpecialKeysMap.get(KeyEvent.KEYCODE_DPAD_DOWN));
        sendPacket(np);
    }

    public void sendSelect() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("specialKey", SpecialKeysMap.get(KeyEvent.KEYCODE_ENTER));
        sendPacket(np);
    }

    public void sendHome() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("alt", true);
        np.set("specialKey", SpecialKeysMap.get(KeyEvent.KEYCODE_F4));
        getDevice().sendPacket(np);
    }

    public void sendBack() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("specialKey", SpecialKeysMap.get(KeyEvent.KEYCODE_ESCAPE));
        getDevice().sendPacket(np);
    }

    public void sendText(String content) {
        NetworkPacket np = new NetworkPacket(MousePadPlugin.PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("key", content);
        sendPacket(np);
    }

    void sendPacket(NetworkPacket np) {
        device.sendPacket(np);
    }

    boolean isKeyboardEnabled() {
        return keyboardEnabled;
    }

}
