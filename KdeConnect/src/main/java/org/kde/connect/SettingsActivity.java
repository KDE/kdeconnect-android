package org.kde.connect;

import android.app.ListActivity;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;

import org.kde.kdeconnect.R;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Set;

public class SettingsActivity extends ListActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

/*
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME
                | ActionBar.DISPLAY_SHOW_TITLE);
        actionBar.setDisplayHomeAsUpEnabled(true);*/

        getListView().setItemsCanFocus(true);
        getListView().setFocusable(false);
        getListView().setEnabled(true);
        getListView().setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
        getListView().setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        getListView().setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);

        final String deviceId = getIntent().getStringExtra("deviceId");
        BackgroundService.RunCommand(getApplicationContext(), new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {

                final Device device = service.getDevice(deviceId);
                Set<String> plugins = PluginFactory.getAvailablePlugins();

                ArrayList<Preference> preferences = new ArrayList<Preference>();
                for (final String pluginName : plugins) {
                    Log.e("SettingsActivity", pluginName);
                    CheckBoxPreference pref = new CheckBoxPreference(getBaseContext());
                    PluginFactory.PluginInfo info = PluginFactory.getPluginInfo(getBaseContext(), pluginName);
                    pref.setKey(pluginName);
                    pref.setTitle(info.getDisplayName());
                    pref.setSummary(info.getDescription());
                    pref.setSelectable(true);
                    pref.setEnabled(true);
                    pref.setChecked(device.isPluginEnabled(pluginName));
                    pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                        @Override
                        public boolean onPreferenceClick(Preference preference) {
                            Log.e("CLICK","CLICK");
                            return false;
                        }
                    });
                    pref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                        @Override
                        public boolean onPreferenceChange(Preference preference, Object newValue) {
                            device.setPluginEnabled(pluginName, (Boolean)newValue);
                            return true;
                        }
                    });
                    preferences.add(pref);
                }

                setListAdapter(new PreferenceListAdapter(preferences));
            }
        });



    }


}