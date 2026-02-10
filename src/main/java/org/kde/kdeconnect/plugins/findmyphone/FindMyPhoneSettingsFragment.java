/*
 * SPDX-FileCopyrightText: 2018 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.findmyphone;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.IntentCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreference;

import org.kde.kdeconnect.ui.PluginSettingsFragment;
import org.kde.kdeconnect_tp.R;

public class FindMyPhoneSettingsFragment extends PluginSettingsFragment {
    private static final int REQUEST_CODE_SELECT_RINGTONE = 1000;

    private String preferenceKeyRingtone;
    private String preferenceKeyFlashlight;
    private SharedPreferences sharedPreferences;
    private Preference ringtonePreference;
    private SwitchPreference flashlightPreference;

    private final ActivityResultLauncher<String> requestCameraPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    setFlashlightEnabled(true);
                    return;
                }
                if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.fromParts("package", requireContext().getPackageName(), null));
                    Toast.makeText(requireContext(), R.string.findmyphone_open_settings_for_camera, Toast.LENGTH_LONG).show();
                    startActivity(intent);
                    return;
                }
                Toast.makeText(requireContext(), R.string.findmyphone_camera_explanation, Toast.LENGTH_SHORT).show();
            });

    public static FindMyPhoneSettingsFragment newInstance(@NonNull String pluginKey, int layout) {
        FindMyPhoneSettingsFragment fragment = new FindMyPhoneSettingsFragment();
        fragment.setArguments(pluginKey, layout);

        return fragment;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);

        preferenceKeyRingtone = getString(R.string.findmyphone_preference_key_ringtone);
        preferenceKeyFlashlight = getString(R.string.findmyphone_preference_key_flashlight);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext());

        ringtonePreference = getPreferenceScreen().findPreference(preferenceKeyRingtone);
        flashlightPreference = getPreferenceScreen().findPreference(preferenceKeyFlashlight);

        setRingtoneSummary();

        if (flashlightPreference != null) {
            flashlightPreference.setOnPreferenceChangeListener((pref, newValue) -> {
                if (Boolean.TRUE.equals(newValue)) {
                    if (hasCameraPermission()) {
                        return true;
                    }
                    requestCameraPermission.launch(Manifest.permission.CAMERA);
                    return false;
                }
                return true;
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        syncFlashlightPreferenceWithPermission();
    }

    private void syncFlashlightPreferenceWithPermission() {
        boolean preferenceOn = sharedPreferences.getBoolean(preferenceKeyFlashlight, false);
        if (!hasCameraPermission() && preferenceOn) {
            setFlashlightEnabled(false);
        }
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private void setFlashlightEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(preferenceKeyFlashlight, enabled).apply();
        flashlightPreference.setChecked(enabled);
    }

    private void setRingtoneSummary() {
        String ringtone = sharedPreferences.getString(preferenceKeyRingtone, Settings.System.DEFAULT_RINGTONE_URI.toString());

        Uri ringtoneUri = Uri.parse(ringtone);

        ringtonePreference.setSummary(RingtoneManager.getRingtone(requireContext(), ringtoneUri).getTitle(requireContext()));
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        /*
         * There is no RingtonePreference in support library nor androidx, this is the workaround proposed here:
         * https://issuetracker.google.com/issues/37057453
         */

        if (preference.hasKey() && preference.getKey().equals(preferenceKeyRingtone)) {
            Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_NOTIFICATION_URI);

            String existingValue = sharedPreferences.getString(preferenceKeyRingtone, null);
            if (existingValue != null) {
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(existingValue));
            } else {
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Settings.System.DEFAULT_RINGTONE_URI);
            }

            startActivityForResult(intent, REQUEST_CODE_SELECT_RINGTONE);
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_SELECT_RINGTONE && resultCode == Activity.RESULT_OK) {
            Uri uri = IntentCompat.getParcelableExtra(data, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri.class);

            if (uri != null) {
                sharedPreferences.edit()
                        .putString(preferenceKeyRingtone, uri.toString())
                        .apply();

                setRingtoneSummary();
            }
        }
    }
}
