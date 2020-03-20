/*
 * Copyright 2018 Erik Duisters <e.duisters1@gmail.com>
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

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect_tp.R;

import java.util.Locale;

public class PluginSettingsFragment extends PreferenceFragmentCompat {
    private static final String ARG_PLUGIN_KEY = "plugin_key";

    private String pluginKey;
    protected Device device;
    protected Plugin plugin;

    public static PluginSettingsFragment newInstance(@NonNull String pluginKey) {
        PluginSettingsFragment fragment = new PluginSettingsFragment();
        fragment.setArguments(pluginKey);

        return fragment;
    }

    public PluginSettingsFragment() {}

    protected Bundle setArguments(@NonNull String pluginKey) {
        Bundle args = new Bundle();
        args.putString(ARG_PLUGIN_KEY, pluginKey);

        setArguments(args);

        return args;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (getArguments() == null || !getArguments().containsKey(ARG_PLUGIN_KEY)) {
            throw new RuntimeException("You must provide a pluginKey by calling setArguments(@NonNull String pluginKey)");
        }

        pluginKey = getArguments().getString(ARG_PLUGIN_KEY);
        this.device = getDeviceOrThrow(getDeviceId());
        this.plugin = device.getPlugin(pluginKey);

        super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        if (this.plugin != null && this.plugin.supportsDeviceSpecificSettings()) {
            PreferenceManager prefsManager = getPreferenceManager();
            prefsManager.setSharedPreferencesName(this.plugin.getSharedPreferencesName());
            prefsManager.setSharedPreferencesMode(Context.MODE_PRIVATE);
        }

        int resFile = getResources().getIdentifier(pluginKey.toLowerCase(Locale.ENGLISH) + "_preferences", "xml",
                requireContext().getPackageName());
        addPreferencesFromResource(resFile);
    }

    @Override
    public void onResume() {
        super.onResume();

        PluginFactory.PluginInfo info = PluginFactory.getPluginInfo(pluginKey);
        requireActivity().setTitle(getString(R.string.plugin_settings_with_name, info.getDisplayName()));
    }

    public String getDeviceId() {
        return ((PluginSettingsActivity)requireActivity()).getDeviceId();
    }

    private Device getDeviceOrThrow(String deviceId) {
        Device device = BackgroundService.getInstance().getDevice(deviceId);

        if (device == null) {
            throw new RuntimeException("PluginSettingsFragment.onCreatePreferences() - No device with id " + getDeviceId());
        }

        return device;
    }
}
