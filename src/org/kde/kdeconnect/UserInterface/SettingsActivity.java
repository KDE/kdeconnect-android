/*
 * Copyright 2014 Albert Vaca Cintora <albertvaka@gmail.com>
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

package org.kde.kdeconnect.UserInterface;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

public class SettingsActivity extends AppCompatPreferenceActivity {

    static private String deviceId; //Static because if we get here by using the back button in the action bar, the extra deviceId will not be set.

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final PreferenceScreen preferenceScreen = getPreferenceManager().createPreferenceScreen(this);
        setPreferenceScreen(preferenceScreen);

        if (getIntent().hasExtra("deviceId")) {
            deviceId = getIntent().getStringExtra("deviceId");
        }

        BackgroundService.RunCommand(getApplicationContext(), new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {

                final Device device = service.getDevice(deviceId);
                Set<String> plugins = PluginFactory.getAvailablePlugins();

                final ArrayList<Preference> preferences = new ArrayList<>();
                for (final String pluginKey : plugins) {

                    PluginFactory.PluginInfo info = PluginFactory.getPluginInfo(getBaseContext(), pluginKey);

                    CheckBoxPreference pref = new CheckBoxPreference(preferenceScreen.getContext());
                    pref.setKey(pluginKey);
                    pref.setTitle(info.getDisplayName());
                    pref.setSummary(info.getDescription());
                    pref.setChecked(device.isPluginEnabled(pluginKey));
                    preferences.add(pref);
                    preferenceScreen.addPreference(pref);

                    if (info.hasSettings()) {
                        Preference pluginPreference = new Preference(preferenceScreen.getContext());
                        pluginPreference.setKey(pluginKey.toLowerCase(Locale.ENGLISH) + "_preferences");
                        pluginPreference.setSummary(getString(R.string.plugin_settings_with_name, info.getDisplayName()));
                        preferences.add(pluginPreference);
                        preferenceScreen.addPreference(pluginPreference);
                        pluginPreference.setDependency(pref.getKey());
                    }
                }

                getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                        Preference pref = preferences.get(i);
                        if (pref.getDependency() == null) { //Is a check to enable/disable a plugin
                            CheckBoxPreference check = (CheckBoxPreference)pref;
                            boolean enabled = device.isPluginEnabled(pref.getKey());
                            device.setPluginEnabled(pref.getKey(), !enabled);
                            check.setChecked(!enabled);
                        } else { //Is a plugin suboption
                            if (pref.isEnabled()) {
                                String pluginKey = pref.getDependency(); //The parent pref will be named like the plugin
                                Plugin plugin = device.getPlugin(pluginKey, true);
                                if (plugin != null) {
                                    plugin.startPreferencesActivity(SettingsActivity.this);
                                } else { //Could happen if the device is not connected anymore
                                    finish(); //End this activity so we go to the "device not reachable" screen
                                }
                            }
                        }
                    }
                });
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        //ActionBar's back button
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }
}
