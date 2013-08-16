package org.kde.connect;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

import org.kde.kdeconnect.R;

public class SettingsActivity extends PreferenceActivity {

    Device device = null;
    SharedPreferences preferences;

    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, final String key) {
            final boolean value = sharedPreferences.getBoolean(key,true);
            BackgroundService.RunCommand(getApplicationContext(), new BackgroundService.InstanceCallback() {
                @Override
                public void onServiceStart(BackgroundService service) {
                device.setPluginEnabled(key,value);
                }
            });

        }
    };


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

/*
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME
                | ActionBar.DISPLAY_SHOW_TITLE);
        actionBar.setDisplayHomeAsUpEnabled(true);*/

        preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        final String deviceId = getIntent().getStringExtra("deviceId");
        BackgroundService.RunCommand(getApplicationContext(), new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                device = service.getDevice(deviceId);

                //This activity displays the DefaultSharedPreferences, so let's update them from our device
                device.readPluginPreferences(preferences);
                addPreferencesFromResource(R.xml.settings);

                if (Build.VERSION.SDK_INT < 11 || Build.VERSION.SDK_INT == 18) {
                    CheckBoxPreference p = (CheckBoxPreference)findPreference("plugin_clipboard");
                    p.setEnabled(false);
                    p.setChecked(false);
                    p.setSelectable(false);
                    p.setSummary(R.string.plugin_not_available);
                }

                preferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener);
            }
        });

    }

    @Override
    public void onResume() {
        super.onResume();
        if (preferences != null) preferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener);

    }

    @Override
    public void onPause() {
        if (preferences != null) preferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
        super.onPause();
    }

}