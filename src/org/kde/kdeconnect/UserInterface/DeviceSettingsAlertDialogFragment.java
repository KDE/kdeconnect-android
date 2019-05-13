/*
 * Copyright 2019 Erik Duisters <e.duisters1@gmail.com>
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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.kde.kdeconnect.UserInterface;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

public class DeviceSettingsAlertDialogFragment extends AlertDialogFragment {
    private static final String KEY_PLUGIN_KEY = "PluginKey";
    private static final String KEY_DEVICE_ID = "DeviceId";

    private String pluginKey;
    private String deviceId;

    public DeviceSettingsAlertDialogFragment() {}

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();

        if (args == null || !args.containsKey(KEY_PLUGIN_KEY)) {
            throw new RuntimeException("You must call Builder.setPluginKey() to set the plugin");
        }
        if (!args.containsKey(KEY_DEVICE_ID)) {
            throw new RuntimeException("You must call Builder.setDeviceId() to set the device");
        }

        pluginKey = args.getString(KEY_PLUGIN_KEY);
        deviceId = args.getString(KEY_DEVICE_ID);

        setCallback(new Callback() {
            @Override
            public void onPositiveButtonClicked() {
                Intent intent = new Intent(requireActivity(), PluginSettingsActivity.class);

                intent.putExtra(PluginSettingsActivity.EXTRA_DEVICE_ID, deviceId);
                intent.putExtra(PluginSettingsActivity.EXTRA_PLUGIN_KEY, pluginKey);
                requireActivity().startActivity(intent);
            }
        });
    }

    public static class Builder extends AbstractBuilder<DeviceSettingsAlertDialogFragment.Builder, DeviceSettingsAlertDialogFragment> {
        @Override
        public DeviceSettingsAlertDialogFragment.Builder getThis() {
            return this;
        }

        public DeviceSettingsAlertDialogFragment.Builder setPluginKey(String pluginKey) {
            args.putString(KEY_PLUGIN_KEY, pluginKey);
            return getThis();
        }

        public DeviceSettingsAlertDialogFragment.Builder setDeviceId(String deviceId) {
            args.putString(KEY_DEVICE_ID, deviceId);
            return getThis();
        }

        @Override
        protected DeviceSettingsAlertDialogFragment createFragment() {
            return new DeviceSettingsAlertDialogFragment();
        }
    }
}
