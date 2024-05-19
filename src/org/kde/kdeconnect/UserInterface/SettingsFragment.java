/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.UserInterface;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;
import androidx.preference.TwoStatePreference;

import com.google.android.material.snackbar.Snackbar;

import org.apache.commons.lang3.StringUtils;
import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Helpers.DeviceHelper;
import org.kde.kdeconnect.Helpers.NotificationHelper;
import org.kde.kdeconnect_tp.R;

public class SettingsFragment extends PreferenceFragmentCompat {

    public static final String KEY_UDP_BROADCAST_ENABLED = "udp_broadcast_enabled";
    public static final String KEY_BLUETOOTH_ENABLED = "bluetooth_enabled";
    public static final String KEY_APP_THEME = "theme_pref";

    private EditTextPreference renameDevice;

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (getActivity() != null) {
            ((MainActivity) requireActivity()).getSupportActionBar().setTitle(R.string.settings);
        }
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        Context context = getPreferenceManager().getContext();
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);

        // Rename device
        renameDevice = new EditTextPreference(context);
        renameDevice.setKey(DeviceHelper.KEY_DEVICE_NAME_PREFERENCE);
        renameDevice.setSelectable(true);
        renameDevice.setOnBindEditTextListener(TextView::setSingleLine);
        renameDevice.setOnBindEditTextListener(editText -> editText.setFilters(new InputFilter[] {
                (source, start, end, dest, dstart, dend) -> DeviceHelper.filterName(source.subSequence(start, end).toString()),
                new InputFilter.LengthFilter(DeviceHelper.MAX_DEVICE_NAME_LENGTH),
        }));
        String deviceName = DeviceHelper.getDeviceName(context);
        renameDevice.setTitle(R.string.settings_rename);
        renameDevice.setSummary(deviceName);
        renameDevice.setDialogTitle(R.string.device_rename_title);
        renameDevice.setText(deviceName);
        renameDevice.setPositiveButtonText(R.string.device_rename_confirm);
        renameDevice.setNegativeButtonText(R.string.cancel);
        renameDevice.setOnPreferenceChangeListener((preference, newValue) -> {
            String name = (String) newValue;

            if (StringUtils.isBlank(name)) {
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
        themeSelector.setKey(KEY_APP_THEME);
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
            final TwoStatePreference notificationSwitch = new SwitchPreference(context);
            notificationSwitch.setPersistent(false);
            notificationSwitch.setChecked(NotificationHelper.isPersistentNotificationEnabled(context));
            notificationSwitch.setTitle(R.string.setting_persistent_notification);
            notificationSwitch.setOnPreferenceChangeListener((preference, newValue) -> {

                final boolean isChecked = (Boolean) newValue;

                NotificationHelper.setPersistentNotificationEnabled(context, isChecked);
                BackgroundService service = BackgroundService.getInstance();
                if (service != null) {
                    service.changePersistentNotificationVisibility(isChecked);
                }

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

        // UDP broadcast toggle
        final TwoStatePreference udpBroadcastDiscovery = new SwitchPreference(context);
        udpBroadcastDiscovery.setDefaultValue(true);
        udpBroadcastDiscovery.setKey(KEY_UDP_BROADCAST_ENABLED);
        udpBroadcastDiscovery.setTitle(R.string.enable_udp_broadcast);
        screen.addPreference(udpBroadcastDiscovery);

        final TwoStatePreference enableBluetoothSupport = new SwitchPreference(context);
        enableBluetoothSupport.setDefaultValue(false);
        enableBluetoothSupport.setKey(KEY_BLUETOOTH_ENABLED);
        enableBluetoothSupport.setTitle("Enable bluetooth (beta)");
        enableBluetoothSupport.setOnPreferenceChangeListener((preference, newValue) -> {
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (boolean)newValue) {
                    ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN}, 2);
                }
                return true;
        });
        screen.addPreference(enableBluetoothSupport);

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
