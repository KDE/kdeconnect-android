package org.kde.kdeconnect.UserInterface;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;
import androidx.preference.TwoStatePreference;
import androidx.preference.ListPreference;

import com.google.android.material.snackbar.Snackbar;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Helpers.DeviceHelper;
import org.kde.kdeconnect.Helpers.NotificationHelper;
import org.kde.kdeconnect_tp.R;

public class SettingsFragment extends PreferenceFragmentCompat {

    private EditTextPreference renameDevice;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        Context context = getPreferenceManager().getContext();
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);

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
                    int currentTheme = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                    if (currentTheme != Configuration.UI_MODE_NIGHT_YES) {
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

        // Theme Selector
        ListPreference themeSelector = new ListPreference(context);
        themeSelector.setKey("theme_pref");
        themeSelector.setTitle(R.string.theme_dialog_title);
        themeSelector.setDialogTitle(R.string.theme_dialog_title);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            themeSelector.setEntries(R.array.theme_list_v28);
        } else {
            themeSelector.setEntries(R.array.theme_list);
        }
        themeSelector.setEntryValues(R.array.theme_list_values);
        themeSelector.setDefaultValue(ThemeUtil.DEFAULT_MODE);
        themeSelector.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        themeSelector.setOnPreferenceChangeListener((preference, newValue) -> {
            String themeValue = (String) newValue;
            ThemeUtil.applyTheme(themeValue);
            return true;
        });
        screen.addPreference(themeSelector);

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
