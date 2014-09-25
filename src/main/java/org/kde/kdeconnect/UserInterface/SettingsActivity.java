package org.kde.kdeconnect.UserInterface;

import android.content.Intent;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.view.View;
import android.widget.AdapterView;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;
import java.util.Set;

public class SettingsActivity extends PreferenceActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final PreferenceScreen preferenceScreen = getPreferenceManager().createPreferenceScreen(this);
        setPreferenceScreen(preferenceScreen);

        final String deviceId = getIntent().getStringExtra("deviceId");
        BackgroundService.RunCommand(getApplicationContext(), new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {

                final Device device = service.getDevice(deviceId);
                Set<String> plugins = PluginFactory.getAvailablePlugins();

                final ArrayList<Preference> preferences = new ArrayList<Preference>();
                for (final String pluginName : plugins) {
                    final CheckBoxPreference pref = new CheckBoxPreference(getBaseContext());

                    PluginFactory.PluginInfo info = PluginFactory.getPluginInfo(getBaseContext(), pluginName);
                    pref.setKey(pluginName);
                    pref.setTitle(info.getDisplayName());
                    pref.setSummary(info.getDescription());
                    pref.setChecked(device.isPluginEnabled(pluginName));
                    preferences.add(pref);
                    preferenceScreen.addPreference(pref);

                    if (info.hasSettings()) {
                        final Preference pluginPreference = new Preference(getBaseContext());
                        pluginPreference.setKey(pluginName + getString(R.string.plugin_settings_key));
                        pluginPreference.setTitle(info.getDisplayName());
                        pluginPreference.setSummary(R.string.plugin_settings);
                        preferences.add(pluginPreference);
                        preferenceScreen.addPreference(pluginPreference);
                        pluginPreference.setDependency(pref.getKey());
                    }
                }

                getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                        Preference pref = preferences.get(i);
                        if (pref.getDependency() == null) { //Is a plugin check
                            CheckBoxPreference check = (CheckBoxPreference)pref;
                            boolean enabled = device.isPluginEnabled(pref.getKey());
                            device.setPluginEnabled(pref.getKey(), !enabled);
                            check.setChecked(!enabled);
                        } else { //Is a plugin suboption
                            if (pref.isEnabled()) {
                                Intent intent = new Intent(SettingsActivity.this, PluginSettingsActivity.class);
                                intent.putExtra(Intent.EXTRA_INTENT, pref.getKey());
                                startActivity(intent);
                            }
                        }
                    }
                });
            }
        });



    }
}