/*
 * Copyright 2018 Erik Duisters <e.duisters1@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License or (at your option) version 3 or any later version
 * accepted by the membership of KDE e.V. (or its successor approved
 * by the membership of KDE e.V.), which shall act as a proxy
 * defined in Section 14 of version 3 of the license.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.kde.kdeconnect.Plugins.SftpPlugin;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.view.ActionMode;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Helpers.StorageHelper;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.UserInterface.PluginSettingsActivity;
import org.kde.kdeconnect.UserInterface.PluginSettingsFragment;
import org.kde.kdeconnect_tp.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

//TODO: Is it possible on API 19 to select a directory and then have write permission for everything beneath it
//TODO: Is it necessary to check if uri permissions are still in place? If it is make the user aware of the fact (red text or something)
public class SftpSettingsFragment
        extends PluginSettingsFragment
        implements StoragePreferenceDialogFragment.Callback,
                   Preference.OnPreferenceChangeListener,
                   StoragePreference.OnLongClickListener, ActionMode.Callback {
    private final static String KEY_STORAGE_PREFERENCE_DIALOG = "StoragePreferenceDialog";
    private final static String KEY_ACTION_MODE_STATE = "ActionModeState";
    private final static String KEY_ACTION_MODE_ENABLED = "ActionModeEnabled";
    private final static String KEY_ACTION_MODE_SELECTED_ITEMS = "ActionModeSelectedItems";

    private List<SftpPlugin.StorageInfo> storageInfoList;
    private PreferenceCategory preferenceCategory;
    private ActionMode actionMode;
    private JSONObject savedActionModeState;

    public static SftpSettingsFragment newInstance(@NonNull String pluginKey) {
        SftpSettingsFragment fragment = new SftpSettingsFragment();
        fragment.setArguments(pluginKey);

        return fragment;
    }

    public SftpSettingsFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // super.onCreate creates PreferenceManager and calls onCreatePreferences()
        super.onCreate(savedInstanceState);

        if (getFragmentManager() != null) {
            Fragment fragment = getFragmentManager().findFragmentByTag(KEY_STORAGE_PREFERENCE_DIALOG);
            if (fragment != null) {
                ((StoragePreferenceDialogFragment) fragment).setCallback(this);
            }
        }

        if (savedInstanceState != null && savedInstanceState.containsKey(KEY_ACTION_MODE_STATE)) {
            try {
                savedActionModeState = new JSONObject(savedInstanceState.getString(KEY_ACTION_MODE_STATE, "{}"));
            } catch (JSONException ignored) {}
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);

        TypedArray ta = requireContext().obtainStyledAttributes(new int[]{R.attr.colorAccent});
        int colorAccent = ta.getColor(0, 0);
        ta.recycle();

        int sdkInt = Build.VERSION.SDK_INT;

        storageInfoList = getStorageInfoList(requireContext(), plugin);

        PreferenceScreen preferenceScreen = getPreferenceScreen();
        preferenceCategory = preferenceScreen
                .findPreference(getString(R.string.sftp_preference_key_preference_category));

        if (sdkInt <= 19) {
            preferenceCategory.setTitle(R.string.sftp_preference_detected_sdcards);
        } else {
            preferenceCategory.setTitle(R.string.sftp_preference_configured_storage_locations);
        }

        addStoragePreferences(preferenceCategory);

        Preference addStoragePreference = preferenceScreen.findPreference(getString(R.string.sftp_preference_key_add_storage));
        addStoragePreference.getIcon().setColorFilter(colorAccent, PorterDuff.Mode.SRC_IN);

        if (sdkInt <= 19) {
            addStoragePreference.setVisible(false);
        }

        Preference addCameraShortcutPreference = preferenceScreen.findPreference(getString(R.string.sftp_preference_key_add_camera_shortcut));

        if (sdkInt > 19) {
            addCameraShortcutPreference.setVisible(false);
        }
    }

    private void addStoragePreferences(PreferenceCategory preferenceCategory) {
        /*
            https://developer.android.com/guide/topics/ui/settings/programmatic-hierarchy
            You can't just use any context to create Preferences, you have to use PreferenceManager's context
         */
        Context context = getPreferenceManager().getContext();

        sortStorageInfoListOnDisplayName();

        for (int i = 0; i < storageInfoList.size(); i++) {
            SftpPlugin.StorageInfo storageInfo = storageInfoList.get(i);
            StoragePreference preference = new StoragePreference(context);
            preference.setOnPreferenceChangeListener(this);
            if (Build.VERSION.SDK_INT >= 21) {
                preference.setOnLongClickListener(this);
            }
            preference.setKey(getString(R.string.sftp_preference_key_storage_info, i));
            preference.setIcon(android.R.color.transparent);
            preference.setDefaultValue(storageInfo);
            if (storageInfo.isFileUri()) {
                preference.setDialogTitle(R.string.sftp_preference_edit_sdcard_title);
            } else {
                preference.setDialogTitle(R.string.sftp_preference_edit_storage_location);
            }

            preferenceCategory.addPreference(preference);
        }
    }

    @Override
    protected RecyclerView.Adapter onCreateAdapter(PreferenceScreen preferenceScreen) {
        if (savedActionModeState != null) {
            getListView().post(this::restoreActionMode);
        }

        return super.onCreateAdapter(preferenceScreen);
    }

    private void restoreActionMode() {
        try {
            if (savedActionModeState.getBoolean(KEY_ACTION_MODE_ENABLED)) {
                actionMode = ((PluginSettingsActivity)requireActivity()).startSupportActionMode(this);

                if (actionMode != null) {
                    JSONArray jsonArray = savedActionModeState.getJSONArray(KEY_ACTION_MODE_SELECTED_ITEMS);
                    SparseBooleanArray selectedItems = new SparseBooleanArray();

                    for (int i = 0, count = jsonArray.length(); i < count; i++) {
                        selectedItems.put(jsonArray.getInt(i), true);
                    }

                    for (int i = 0, count = preferenceCategory.getPreferenceCount(); i < count; i++) {
                        StoragePreference preference = (StoragePreference) preferenceCategory.getPreference(i);
                        preference.setInSelectionMode(true);
                        preference.checkbox.setChecked(selectedItems.get(i, false));
                    }
                }
            }

        } catch (JSONException ignored) {}
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        if (preference instanceof StoragePreference) {
            StoragePreferenceDialogFragment fragment = StoragePreferenceDialogFragment.newInstance(preference.getKey());
            fragment.setTargetFragment(this, 0);
            fragment.setCallback(this);
            fragment.show(requireFragmentManager(), KEY_STORAGE_PREFERENCE_DIALOG);
        } else {
            super.onDisplayPreferenceDialog(preference);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        try {
            JSONObject jsonObject = new JSONObject();

            jsonObject.put(KEY_ACTION_MODE_ENABLED, actionMode != null);

            if (actionMode != null) {
                JSONArray jsonArray = new JSONArray();

                for (int i = 0, count = preferenceCategory.getPreferenceCount(); i < count; i++) {
                    StoragePreference preference = (StoragePreference) preferenceCategory.getPreference(i);
                    if (preference.checkbox.isChecked()) {
                        jsonArray.put(i);
                    }
                }

                jsonObject.put(KEY_ACTION_MODE_SELECTED_ITEMS, jsonArray);
            }

            outState.putString(KEY_ACTION_MODE_STATE, jsonObject.toString());
        } catch (JSONException ignored) {}
    }

    private void saveStorageInfoList() {
        SharedPreferences preferences = this.plugin.getPreferences();

        JSONArray jsonArray = new JSONArray();

        try {
            for (SftpPlugin.StorageInfo storageInfo : this.storageInfoList) {
                jsonArray.put(storageInfo.toJSON());
            }
        } catch (JSONException ignored) {}

        preferences
                .edit()
                .putString(requireContext().getString(SftpPlugin.PREFERENCE_KEY_STORAGE_INFO_LIST), jsonArray.toString())
                .apply();
    }

    @NonNull
    static List<SftpPlugin.StorageInfo> getStorageInfoList(@NonNull Context context, @NonNull Plugin plugin) {
        ArrayList<SftpPlugin.StorageInfo> storageInfoList = new ArrayList<>();

        SharedPreferences deviceSettings = plugin.getPreferences();

        String jsonString = deviceSettings
                .getString(context.getString(SftpPlugin.PREFERENCE_KEY_STORAGE_INFO_LIST), "[]");

        try {
            JSONArray jsonArray = new JSONArray(jsonString);

            for (int i = 0; i < jsonArray.length(); i++) {
                storageInfoList.add(SftpPlugin.StorageInfo.fromJSON(jsonArray.getJSONObject(i)));
            }
        } catch (JSONException e) {
            Log.e("SFTPSettings", "Couldn't load storage info", e);
        }

        if (Build.VERSION.SDK_INT <= 19) {
            addDetectedSDCardsToStorageInfoList(context, storageInfoList);
        }

        return storageInfoList;
    }

    private static void addDetectedSDCardsToStorageInfoList(@NonNull Context context, List<SftpPlugin.StorageInfo> storageInfoList) {
        List<StorageHelper.StorageInfo> storageHelperInfoList = StorageHelper.getStorageList();

        for (StorageHelper.StorageInfo info : storageHelperInfoList) {
            // on at least API 17 emulator Environment.isExternalStorageRemovable returns false
            if (info.removable || info.path.startsWith(Environment.getExternalStorageDirectory().getPath())) {
                StringBuilder displayNameBuilder = new StringBuilder();
                StringBuilder displayNameReadOnlyBuilder = new StringBuilder();

                Uri sdCardUri = Uri.fromFile(new File(info.path));

                if (isAlreadyConfigured(storageInfoList, sdCardUri)) {
                    continue;
                }

                int i = 1;

                do {
                    if (i == 1) {
                        displayNameBuilder.append(context.getString(R.string.sftp_sdcard));
                    } else {
                        displayNameBuilder.setLength(0);
                        displayNameBuilder.append(context.getString(R.string.sftp_sdcard_num, i));
                    }

                    displayNameReadOnlyBuilder
                            .append(displayNameBuilder)
                            .append(" ")
                            .append(context.getString(R.string.sftp_readonly));

                    i++;
                } while (!isDisplayNameUnique(storageInfoList, displayNameBuilder.toString(), displayNameReadOnlyBuilder.toString()));

                String displayName = info.readonly ?
                        displayNameReadOnlyBuilder.toString() : displayNameBuilder.toString();

                storageInfoList.add(new SftpPlugin.StorageInfo(displayName, Uri.fromFile(new File(info.path))));
            }
        }
    }

    private static boolean isDisplayNameUnique(List<SftpPlugin.StorageInfo> storageInfoList, String displayName, String displayNameReadOnly) {
        for (SftpPlugin.StorageInfo info : storageInfoList) {
            if (info.displayName.equals(displayName) || info.displayName.equals(displayName + displayNameReadOnly)) {
                return false;
            }
        }

        return true;
    }

    private static boolean isAlreadyConfigured(List<SftpPlugin.StorageInfo> storageInfoList, Uri sdCardUri) {
        for (SftpPlugin.StorageInfo info : storageInfoList) {
            if (info.uri.equals(sdCardUri)) {
                return true;
            }
        }

        return false;
    }

    private void sortStorageInfoListOnDisplayName() {
        Collections.sort(storageInfoList, (si1, si2) -> si1.displayName.compareToIgnoreCase(si2.displayName));
    }

    @NonNull
    @Override
    public StoragePreferenceDialogFragment.CallbackResult isDisplayNameAllowed(@NonNull String displayName) {
        StoragePreferenceDialogFragment.CallbackResult result = new StoragePreferenceDialogFragment.CallbackResult();
        result.isAllowed = true;

        if (displayName.isEmpty()) {
            result.isAllowed = false;
            result.errorMessage = getString(R.string.sftp_storage_preference_display_name_cannot_be_empty);
        } else {
            for (SftpPlugin.StorageInfo storageInfo : storageInfoList) {
                if (storageInfo.displayName.equals(displayName)) {
                    result.isAllowed = false;
                    result.errorMessage = getString(R.string.sftp_storage_preference_display_name_already_used);

                    break;
                }
            }
        }

        return result;
    }

    @NonNull
    @Override
    public StoragePreferenceDialogFragment.CallbackResult isUriAllowed(@NonNull Uri uri) {
        StoragePreferenceDialogFragment.CallbackResult result = new StoragePreferenceDialogFragment.CallbackResult();
        result.isAllowed = true;

        for (SftpPlugin.StorageInfo storageInfo : storageInfoList) {
            if (storageInfo.uri.equals(uri)) {
                result.isAllowed = false;
                result.errorMessage = getString(R.string.sftp_storage_preference_storage_location_already_configured);

                break;
            }
        }
        return result;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void addNewStoragePreference(@NonNull SftpPlugin.StorageInfo storageInfo, int takeFlags) {
        storageInfoList.add(storageInfo);

        handleChangedStorageInfoList();

        requireContext().getContentResolver().takePersistableUriPermission(storageInfo.uri, takeFlags);
    }

    private void handleChangedStorageInfoList() {

        if (actionMode != null) {
            actionMode.finish(); // In case we are in selection mode, finish it
        }

        saveStorageInfoList();

        preferenceCategory.removeAll();

        addStoragePreferences(preferenceCategory);

        Device device = getDeviceOrThrow();

        device.reloadPluginsFromSettings();
    }

    private Device getDeviceOrThrow() {
        Device device = BackgroundService.getInstance().getDevice(getDeviceId());

        if (device == null) {
            throw new RuntimeException("SftpSettingsFragment.getDeviceOrThrow(): No device with id: " + getDeviceId());
        }

        return device;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        SftpPlugin.StorageInfo newStorageInfo = (SftpPlugin.StorageInfo) newValue;

        ListIterator<SftpPlugin.StorageInfo> it = storageInfoList.listIterator();

        while (it.hasNext()) {
            SftpPlugin.StorageInfo storageInfo = it.next();
            if (storageInfo.uri.equals(newStorageInfo.uri)) {
                it.set(newStorageInfo);
                break;
            }
        }

        handleChangedStorageInfoList();

        return false;
    }

    @Override
    public void onLongClick(StoragePreference storagePreference) {
        if (actionMode == null) {
            actionMode = ((PluginSettingsActivity)requireActivity()).startSupportActionMode(this);

            if (actionMode != null) {
                for (int i = 0, count = preferenceCategory.getPreferenceCount(); i < count; i++) {
                    StoragePreference preference = (StoragePreference) preferenceCategory.getPreference(i);
                    preference.setInSelectionMode(true);
                    if (storagePreference.equals(preference)) {
                        preference.checkbox.setChecked(true);
                    }
                }
            }
        }
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        MenuInflater inflater = mode.getMenuInflater();
        inflater.inflate(R.menu.sftp_settings_action_mode, menu);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.delete:
                for (int count = preferenceCategory.getPreferenceCount(), i = count - 1; i >= 0; i--) {
                    StoragePreference preference = (StoragePreference) preferenceCategory.getPreference(i);
                    if (preference.checkbox.isChecked()) {
                        SftpPlugin.StorageInfo info = storageInfoList.remove(i);

                        if (Build.VERSION.SDK_INT >= 21) {
                            try {
                                // This throws when trying to release a URI we don't have access to
                                requireContext().getContentResolver().releasePersistableUriPermission(info.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                            } catch (SecurityException e) {
                                // Usually safe to ignore, but who knows?
                                Log.e("SFTP Settings", "Exception", e);
                            }
                        }
                    }
                }

                handleChangedStorageInfoList();
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        actionMode = null;

        for (int i = 0, count = preferenceCategory.getPreferenceCount(); i < count; i++) {
            StoragePreference preference = (StoragePreference) preferenceCategory.getPreference(i);
            preference.setInSelectionMode(false);
            preference.checkbox.setChecked(false);
        }
    }
}
