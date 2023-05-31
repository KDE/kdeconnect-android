/*
 * SPDX-FileCopyrightText: 2014 Ahmed I. Khalil <ahmedibrahimkhali@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.MousePadPlugin;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect.UserInterface.PluginSettingsFragment;
import org.kde.kdeconnect_tp.R;

@PluginFactory.LoadablePlugin
public class MousePadPlugin extends Plugin {

    //public final static String PACKET_TYPE_MOUSEPAD = "kdeconnect.mousepad";
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
        return context.getString(R.string.pref_plugin_mousepad_desc);
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
        return PluginSettingsFragment.newInstance(getPluginKey(), R.xml.mousepadplugin_preferences);
    }

    @Override
    public boolean displayAsButton(Context context) {
        return true;
    }

    @Override
    public void startMainActivity(Activity parentActivity) {
        Intent intent = new Intent(parentActivity, MousePadActivity.class);
        intent.putExtra("deviceId", device.getDeviceId());
        parentActivity.startActivity(intent);
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
        NetworkPacket np = device.getAndRemoveUnsentPacket(NetworkPacket.PACKET_REPLACEID_MOUSEMOVE);
        if (np == null) {
            np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        } else {
            // TODO: In my tests we never get here. Decide if it's worth keeping the logic to replace unsent packets.
            dx += np.getInt("dx");
            dy += np.getInt("dx");
        }

        np.set("dx", dx);
        np.set("dy", dy);

        device.sendPacket(np, NetworkPacket.PACKET_REPLACEID_MOUSEMOVE);
    }

    public void sendLeftClick() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("singleclick", true);
        device.sendPacket(np);
    }

    public void sendDoubleClick() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("doubleclick", true);
        device.sendPacket(np);
    }

    public void sendMiddleClick() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("middleclick", true);
        device.sendPacket(np);
    }

    public void sendRightClick() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("rightclick", true);
        device.sendPacket(np);
    }

    public void sendSingleHold() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("singlehold", true);
        device.sendPacket(np);
    }

    public void sendScroll(float dx, float dy) {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST);
        np.set("scroll", true);
        np.set("dx", dx);
        np.set("dy", dy);
        device.sendPacket(np);
    }

    public void sendKeyboardPacket(NetworkPacket np) {
        device.sendPacket(np);
    }

    boolean isKeyboardEnabled() {
        return keyboardEnabled;
    }

}
