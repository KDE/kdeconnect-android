/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.UserInterface;

import android.os.Bundle;
import android.view.MenuItem;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect_tp.R;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

public class PluginSettingsActivity
        extends AppCompatActivity
        implements PluginPreference.PluginPreferenceCallback {

    public static final String EXTRA_DEVICE_ID = "deviceId";
    public static final String EXTRA_PLUGIN_KEY = "pluginKey";

    //TODO: Save/restore state
    static private String deviceId; //Static because if we get here by using the back button in the action bar, the extra deviceId will not be set.

    @Override
    public void onCreate(Bundle savedInstanceState) {
        ThemeUtil.setUserPreferredTheme(this);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_plugin_settings);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDefaultDisplayHomeAsUpEnabled(true);
        }

        String pluginKey = null;

        if (getIntent().hasExtra(EXTRA_DEVICE_ID)) {
            deviceId = getIntent().getStringExtra(EXTRA_DEVICE_ID);

            if (getIntent().hasExtra(EXTRA_PLUGIN_KEY)) {
                pluginKey = getIntent().getStringExtra(EXTRA_PLUGIN_KEY);
            }
        } else if (deviceId == null) {
            throw new RuntimeException("You must start DeviceSettingActivity using an intent that has a " + EXTRA_DEVICE_ID + " extra");
        }

        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragmentPlaceHolder);
        if (fragment == null) {
            if (pluginKey != null) {
                Device device = BackgroundService.getInstance().getDevice(deviceId);
                if (device != null) {
                    Plugin plugin = device.getPlugin(pluginKey);
                    if (plugin != null) {
                        fragment = plugin.getSettingsFragment(this);
                    }
                }
            }
            if (fragment == null) {
                fragment = PluginSettingsListFragment.newInstance(deviceId);
            }

            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.fragmentPlaceHolder, fragment)
                    .commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            FragmentManager fm = getSupportFragmentManager();

            if (fm.getBackStackEntryCount() > 0) {
                fm.popBackStack();
                return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onStartPluginSettingsFragment(Plugin plugin) {
        setTitle(getString(R.string.plugin_settings_with_name, plugin.getDisplayName()));

        PluginSettingsFragment fragment = plugin.getSettingsFragment(this);

        //TODO: Remove when NotificationFilterActivity has been turned into a PluginSettingsFragment
        if (fragment == null) {
            return;
        }

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentPlaceHolder, fragment)
                .addToBackStack(null)
                .commit();

    }

    @Override
    public void onFinish() {
        finish();
    }

    public String getDeviceId() {
        return deviceId;
    }
}
