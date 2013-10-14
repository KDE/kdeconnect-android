package org.kde.kdeconnect.UserInterface;

import android.app.ListActivity;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Plugins.PluginFactory;

import java.util.ArrayList;
import java.util.Set;

public class SettingsActivity extends ListActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final String deviceId = getIntent().getStringExtra("deviceId");
        BackgroundService.RunCommand(getApplicationContext(), new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {

                final Device device = service.getDevice(deviceId);
                Set<String> plugins = PluginFactory.getAvailablePlugins();

                final ArrayList<Preference> preferences = new ArrayList<Preference>();
                for (final String pluginName : plugins) {
                    CheckBoxPreference pref = new CheckBoxPreference(getBaseContext());
                    PluginFactory.PluginInfo info = PluginFactory.getPluginInfo(getBaseContext(), pluginName);
                    pref.setKey(pluginName);
                    pref.setTitle(info.getDisplayName());
                    pref.setSummary(info.getDescription());
                    pref.setChecked(device.isPluginEnabled(pluginName));
                    preferences.add(pref);
                }

                setListAdapter(new PreferenceListAdapter(SettingsActivity.this, preferences));
                getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

                        CheckBoxPreference pref = (CheckBoxPreference)preferences.get(i);

                        boolean enabled = device.isPluginEnabled(pref.getKey());
                        device.setPluginEnabled(pref.getKey(), !enabled);

                        pref.setChecked(!enabled);

                        getListAdapter().getView(i, view, null); //This will refresh the view (yes, this is the way to do it)

                    }
                });

                getListView().setPadding(16,16,16,16);

            }
        });



    }


}