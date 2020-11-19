package org.kde.kdeconnect.Plugins.MousePadPlugin;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import org.apache.commons.lang3.StringUtils;
import org.kde.kdeconnect.UserInterface.PluginSettingsFragment;
import org.kde.kdeconnect_tp.R;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class MousePadSettingsFragment extends PluginSettingsFragment {

    public static MousePadSettingsFragment newInstance(@NonNull String pluginKey) {
        MousePadSettingsFragment fragment = new MousePadSettingsFragment();
        fragment.setArguments(pluginKey);
        return fragment;
    }

    public MousePadSettingsFragment() {
    }


    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);

        PreferenceScreen preferenceScreen = getPreferenceScreen();
        PreferenceCategory categoryTrustedApps = preferenceScreen
                .findPreference(getString(R.string.sendkeystrokes_pref_category_trusted_apps));

        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        String prefsTrustedAppsKey = getString(R.string.sendkeystrokes_pref_trusted_apps);
        Set<String> trustedApps = prefs.getStringSet(prefsTrustedAppsKey, Collections.emptySet());

        if (trustedApps.size() == 0) {
            categoryTrustedApps.setVisible(false);
        } else {
            for (String trustedApp : trustedApps) {
                SwitchPreference switchPreference = new SwitchPreference(getPreferenceManager().getContext());
                String appName = StringUtils.substringAfterLast(trustedApp, ".");
                switchPreference.setTitle(appName);
                switchPreference.setSummary(trustedApp);
                switchPreference.setKey(trustedApp);
                switchPreference.setPersistent(false); // we save it on our own
                switchPreference.setChecked(true);  // if its in the list, its true; if the user sets it to false, it will disappear from the list on next setting
                categoryTrustedApps.addPreference(switchPreference);


                // all the trusted apps are stored in one StringSet Preference -> combine all checked
                // apps and store it in the setting when one is changed
                switchPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                    HashSet<String> save = new HashSet<>(trustedApps.size());
                    for (String key : trustedApps) {
                        SwitchPreference pref = preferenceScreen.findPreference(key);

                        // this event is called before the preference has its new value - use the event based value for the affected one
                        // but use the preference for the other ones
                        boolean checked = key.equals(preference.getKey()) ? (boolean) newValue : pref.isChecked();
                        if (checked) {
                            save.add(key);
                        }
                    }
                    prefs.edit().putStringSet(prefsTrustedAppsKey, save).apply();
                    return true;
                });
            }
        }
    }


}
