package org.kde.kdeconnect.UserInterface;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;
import androidx.preference.TwoStatePreference;

import com.google.android.material.snackbar.Snackbar;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Helpers.DeviceHelper;
import org.kde.kdeconnect.Helpers.NotificationHelper;
import org.kde.kdeconnect_tp.R;

public class SettingsFragment extends PreferenceFragmentCompat {

    private MainActivity mainActivity;
    private EditTextPreference renameDevice;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        mainActivity = (MainActivity)getActivity();
        Context context = getPreferenceManager().getContext();

        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        // Rename device
        renameDevice = new EditTextPreference(context);
        renameDevice.setKey(DeviceHelper.KEY_DEVICE_NAME_PREFERENCE);
        renameDevice.setSelectable(true);
        String deviceName = DeviceHelper.getDeviceName(context);
        renameDevice.setTitle(R.string.settings_rename);
        renameDevice.setSummary(deviceName);
        renameDevice.setDialogTitle(R.string.device_rename_title);
        renameDevice.setText(deviceName);
        renameDevice.setPositiveButtonText(R.string.device_rename_confirm);
        renameDevice.setNegativeButtonText(R.string.cancel);
        renameDevice.setOnPreferenceChangeListener((preference, newValue) -> {
            String name = (String) newValue;

            if (TextUtils.isEmpty(name)) {
                if (getView() != null) {
                    Snackbar snackbar = Snackbar.make(getView(), R.string.invalid_device_name, Snackbar.LENGTH_LONG);
                    if (!prefs.getBoolean("darkTheme", false)) {
                        // white color is set to the background of snackbar if dark mode is off
                        snackbar.getView().setBackgroundColor(Color.WHITE);
                    }
                    snackbar.show();
                }
                return false;
            }

            renameDevice.setSummary((String)newValue);
            return true;
        });

        screen.addPreference(renameDevice);

        // Dark mode
        final TwoStatePreference darkThemeSwitch = new SwitchPreferenceCompat(context);
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            Preference persistentNotif = new Preference(context);
            persistentNotif.setTitle(R.string.setting_persistent_notification_oreo);
            persistentNotif.setSummary(R.string.setting_persistent_notification_description);

            persistentNotif.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent();
                intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
                intent.putExtra("android.provider.extra.APP_PACKAGE", context.getPackageName());
                context.startActivity(intent);
                return true;
            });
            screen.addPreference(persistentNotif);
        } else {
            // Persistent notification toggle for Android Versions below Oreo
            final TwoStatePreference notificationSwitch = new SwitchPreferenceCompat(context);
            notificationSwitch.setPersistent(false);
            notificationSwitch.setChecked(NotificationHelper.isPersistentNotificationEnabled(context));
            notificationSwitch.setTitle(R.string.setting_persistent_notification);
            notificationSwitch.setOnPreferenceChangeListener((preference, newValue) -> {

                final boolean isChecked = (Boolean) newValue;

                NotificationHelper.setPersistentNotificationEnabled(context, isChecked);
                BackgroundService.RunCommand(context,
                        service -> service.changePersistentNotificationVisibility(isChecked));

                NotificationHelper.setPersistentNotificationEnabled(context, isChecked);

                return true;
            });
            screen.addPreference(notificationSwitch);
        }


        // Trusted Networks
        Preference trustedNetworkPref = new Preference(context);
        trustedNetworkPref.setPersistent(false);
        trustedNetworkPref.setTitle(R.string.trusted_networks);
        trustedNetworkPref.setSummary(R.string.trusted_networks_desc);
        screen.addPreference(trustedNetworkPref);
        trustedNetworkPref.setOnPreferenceClickListener(preference -> {
            startActivity(new Intent(context, TrustedNetworksActivity.class));
            return true;
        });

        // Add device by IP
        Preference devicesByIpPreference = new Preference(context);
        devicesByIpPreference.setPersistent(false);
        devicesByIpPreference.setTitle(R.string.custom_device_list);
        screen.addPreference(devicesByIpPreference);
        devicesByIpPreference.setOnPreferenceClickListener(preference -> {

            startActivity(new Intent(context, CustomDevicesActivity.class));
            return true;
        });


        // More settings text
        Preference moreSettingsText = new Preference(context);
        moreSettingsText.setPersistent(false);
        moreSettingsText.setSelectable(false);
        moreSettingsText.setTitle(R.string.settings_more_settings_title);
        moreSettingsText.setSummary(R.string.settings_more_settings_text);
        screen.addPreference(moreSettingsText);

        setPreferenceScreen(screen);


    }
}
