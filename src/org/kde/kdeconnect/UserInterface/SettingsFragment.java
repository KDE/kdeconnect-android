package org.kde.kdeconnect.UserInterface;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.TwoStatePreference;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Helpers.DeviceHelper;
import org.kde.kdeconnect.Helpers.NotificationHelper;
import org.kde.kdeconnect_tp.R;

public class SettingsFragment extends PreferenceFragmentCompat implements MainActivity.NameChangeCallback {

    MainActivity mainActivity;
    private Preference renameDevice;

    @Override
    public void onDestroy() {
        mainActivity.removeNameChangeCallback(this);
        super.onDestroy();
    }

    TwoStatePreference createTwoStatePreferenceCompat(Context context) {
        //The switch didn't work on Android 6, not sure about 7
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new SwitchPreference(context);
        } else {
            return new CheckBoxPreference(context);
        }
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
        renameDevice.setTitle(R.string.settings_rename);
        renameDevice.setSummary(deviceName);
        screen.addPreference(renameDevice);


        //TODO: Trusted wifi networks settings should go here


        // Dark mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            final TwoStatePreference darkThemeSwitch = createTwoStatePreferenceCompat(context);
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

        // Persistent notification toggle
        final TwoStatePreference notificationSwitch = createTwoStatePreferenceCompat(context);
        notificationSwitch.setPersistent(false);
        notificationSwitch.setChecked(NotificationHelper.isPersistentNotificationEnabled(context));
        notificationSwitch.setTitle(R.string.setting_persistent_notification);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationSwitch.setSummary(R.string.setting_persistent_notification_pie_description);
            notificationSwitch.setEnabled(false);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationSwitch.setSummary(R.string.setting_persistent_notification_oreo_description);
            notificationSwitch.setEnabled(false);
        }
        notificationSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
            final boolean isChecked = (Boolean)newValue;
            NotificationHelper.setPersistentNotificationEnabled(context, isChecked);
            BackgroundService.RunCommand(context, service -> {
                service.changePersistentNotificationVisibility(isChecked);
            });

            return true;
        });
        screen.addPreference(notificationSwitch);

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
    public void onNameChanged(String newName) {
        renameDevice.setSummary(newName);
    }

}
