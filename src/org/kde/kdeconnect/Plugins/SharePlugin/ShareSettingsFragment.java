/*
 * SPDX-FileCopyrightText: 2016 Richard Wagler <riwag@posteo.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.SharePlugin;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import org.kde.kdeconnect.UserInterface.PluginSettingsFragment;

import java.io.File;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

public class ShareSettingsFragment extends PluginSettingsFragment {

    private final static String PREFERENCE_CUSTOMIZE_DESTINATION = "share_destination_custom";
    private final static String PREFERENCE_DESTINATION = "share_destination_folder_uri";

    private static final int RESULT_PICKER = Activity.RESULT_FIRST_USER;

    private Preference filePicker;

    public static ShareSettingsFragment newInstance(@NonNull String pluginKey) {
        ShareSettingsFragment fragment = new ShareSettingsFragment();
        fragment.setArguments(pluginKey);

        return fragment;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);

        PreferenceScreen preferenceScreen = getPreferenceScreen();
        final CheckBoxPreference customDownloads = (CheckBoxPreference) preferenceScreen.findPreference("share_destination_custom");
        filePicker = preferenceScreen.findPreference("share_destination_folder_preference");

        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)) {
            customDownloads.setOnPreferenceChangeListener((preference, newValue) -> {
                updateFilePickerStatus((Boolean) newValue);
                return true;
            });
            filePicker.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                startActivityForResult(intent, RESULT_PICKER);
                return true;
            });
        } else {
            customDownloads.setEnabled(false);
            filePicker.setEnabled(false);
        }

        boolean customized = PreferenceManager
                .getDefaultSharedPreferences(requireContext())
                .getBoolean(PREFERENCE_CUSTOMIZE_DESTINATION, false);

        updateFilePickerStatus(customized);
    }

    private void updateFilePickerStatus(boolean enabled) {
        filePicker.setEnabled(enabled);
        String path = PreferenceManager
                .getDefaultSharedPreferences(requireContext())
                .getString(PREFERENCE_DESTINATION, null);

        if (enabled && path != null) {
            filePicker.setSummary(Uri.parse(path).getPath());
        } else {
            filePicker.setSummary(getDefaultDestinationDirectory().getAbsolutePath());
        }
    }

    public static File getDefaultDestinationDirectory() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    }

    public static boolean isCustomDestinationEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREFERENCE_CUSTOMIZE_DESTINATION, false);
    }

    //Will return the appropriate directory, whether it is customized or not
    public static DocumentFile getDestinationDirectory(Context context) {
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREFERENCE_CUSTOMIZE_DESTINATION, false)) {
            String path = PreferenceManager.getDefaultSharedPreferences(context).getString(PREFERENCE_DESTINATION, null);
            if (path != null) {
                //There should be no way to enter here on api level < kitkat
                DocumentFile treeDocumentFile = DocumentFile.fromTreeUri(context, Uri.parse(path));
                if (treeDocumentFile.canWrite()) { //Checks for FLAG_DIR_SUPPORTS_CREATE on directories
                    return treeDocumentFile;
                } else {
                    //Maybe permission was revoked
                    Log.w("SharePlugin", "Share destination is not writable, falling back to default path.");
                }
            }
        }
        try {
            getDefaultDestinationDirectory().mkdirs();
        } catch (Exception e) {
            Log.e("KDEConnect", "Exception", e);
        }
        return DocumentFile.fromFile(getDefaultDestinationDirectory());
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == RESULT_PICKER
                && resultCode == Activity.RESULT_OK
                && resultData != null) {
            Uri uri = resultData.getData();
            saveStorageLocationPreference(requireContext(), uri);

            Preference filePicker = findPreference("share_destination_folder_preference");
            filePicker.setSummary(uri.getPath());
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static void saveStorageLocationPreference(Context context, Uri uri) {
        context.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putString(PREFERENCE_DESTINATION, uri.toString()).apply();
        prefs.edit().putBoolean(PREFERENCE_CUSTOMIZE_DESTINATION, true).apply();
    }
}
