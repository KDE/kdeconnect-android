package org.kde.kdeconnect.UserInterface;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceScreen;
import android.util.Log;

import org.kde.kdeconnect.Helpers.DeviceHelper;
import org.kde.kdeconnect_tp.R;

public class SettingsFragment extends PreferenceFragmentCompat implements MainActivity.NameChangeCallback {

    MainActivity mainActivity;
    private Preference renameDevice;

    @Override
    public void onDestroy() {
        mainActivity.removeNameChangeCallback(this);
        super.onDestroy();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        mainActivity = (MainActivity)getActivity();
        Context context = mainActivity;

        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        // Rename device
        mainActivity.addNameChangeCallback(this);
        renameDevice = new Preference(context);
        renameDevice.setPersistent(false);
        renameDevice.setSelectable(true);
        renameDevice.setOnPreferenceClickListener(preference -> {
            mainActivity.openRenameDeviceDialog();
            return true;
        });
        String deviceName = DeviceHelper.getDeviceName(context);
        renameDevice.setTitle(R.string.device_rename_title);
        renameDevice.setSummary(deviceName);
        screen.addPreference(renameDevice);


        //TODO: Trusted wifi networks settings should go here


        // Dark mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            final SwitchPreference darkThemeSwitch = new SwitchPreference(context);
            darkThemeSwitch.setPersistent(false);
            darkThemeSwitch.setChecked(ThemeUtil.shouldUseDarkTheme(context));
            darkThemeSwitch.setTitle(R.string.settings_dark_mode);
            darkThemeSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean isChecked = (Boolean)newValue;
                    boolean isDarkAlready = prefs.getBoolean("darkTheme", false);
                    if (isDarkAlready != isChecked) {
                        prefs.edit().putBoolean("darkTheme", isChecked).apply();
                        if (mainActivity != null) {
                            mainActivity.recreate();
                        }
                    }
                    return true;
            });
            screen.addPreference(darkThemeSwitch);
        }


        //TODO: Persistent notification toggle for pre-oreo?


        // More settings text
        Preference moreSettingsText = new Preference(context);
        moreSettingsText.setPersistent(false);
        moreSettingsText.setSelectable(false);
        moreSettingsText.setTitle(R.string.settings_more_settings_title);
        moreSettingsText.setSummary(R.string.settings_more_settings_text);
        screen.addPreference(moreSettingsText);

        setPreferenceScreen(screen);


    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.e("AAAAAAAAAA","CHANGEEEED");
    }


    @Override
    public void onNameChanged(String newName) {
        renameDevice.setSummary(newName);
    }
}
