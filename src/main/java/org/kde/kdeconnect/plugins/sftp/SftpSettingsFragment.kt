/*
 * SPDX-FileCopyrightText: 2018 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.sftp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.SparseBooleanArray
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.appcompat.view.ActionMode
import androidx.core.util.set
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.ui.PluginSettingsActivity
import org.kde.kdeconnect.ui.PluginSettingsFragment
import org.kde.kdeconnect_tp.R
import java.util.Collections

class SftpSettingsFragment :
    PluginSettingsFragment(),
    StoragePreferenceDialogFragment.Callback,
    Preference.OnPreferenceChangeListener,
    StoragePreference.OnLongClickListener,
    ActionMode.Callback {

    private lateinit var storageInfoList: MutableList<SftpPlugin.StorageInfo>
    private var preferenceCategory: PreferenceCategory? = null
    private var actionMode: ActionMode? = null
    private var savedActionModeState: JSONObject? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // super.onCreate creates PreferenceManager and calls onCreatePreferences()
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        val fragmentManager = fragmentManager
        if (fragmentManager != null) {
            val fragment = fragmentManager.findFragmentByTag(KEY_STORAGE_PREFERENCE_DIALOG)
            if (fragment is StoragePreferenceDialogFragment) {
                fragment.callback = this
            }
        }

        if (savedInstanceState != null && savedInstanceState.containsKey(KEY_ACTION_MODE_STATE)) {
            try {
                savedActionModeState = JSONObject(savedInstanceState.getString(KEY_ACTION_MODE_STATE, "{}"))
            } catch (ignored: JSONException) {}
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        // The activity is finishing because its device is no longer available.
        val currentPlugin = plugin ?: return

        // Can't use try-with-resources since TypedArray's close method was only added in API 31
        val ta = requireContext().obtainStyledAttributes(intArrayOf(androidx.appcompat.R.attr.colorAccent))
        val colorAccent = ta.getColor(0, 0)
        ta.recycle()

        storageInfoList = getStorageInfoList(requireContext(), currentPlugin)

        val preferenceScreen = preferenceScreen
        val category: PreferenceCategory? = preferenceScreen.findPreference(getString(R.string.sftp_preference_key_preference_category))
        preferenceCategory = category

        if (category != null) {
            addStoragePreferences(category)
        }

        val addStoragePreference: Preference? = preferenceScreen.findPreference(getString(R.string.sftp_preference_key_add_storage))
        addStoragePreference?.icon?.setColorFilter(colorAccent, PorterDuff.Mode.SRC_IN)
    }

    private fun addStoragePreferences(preferenceCategory: PreferenceCategory) {
        /*
            https://developer.android.com/guide/topics/ui/settings/programmatic-hierarchy
            You can't just use any context to create Preferences, you have to use PreferenceManager's context
         */
        val context = preferenceManager.context

        sortStorageInfoListOnDisplayName()

        for (i in storageInfoList.indices) {
            val storageInfo = storageInfoList[i]
            val preference = StoragePreference(context).apply {
                onPreferenceChangeListener = this@SftpSettingsFragment
                setOnLongClickListener(this@SftpSettingsFragment)
                key = getString(R.string.sftp_preference_key_storage_info, i)
                setIcon(android.R.color.transparent)
                setDefaultValue(storageInfo)
                setDialogTitle(R.string.sftp_preference_edit_storage_location)
            }

            preferenceCategory.addPreference(preference)
        }
    }

    override fun onCreateAdapter(preferenceScreen: PreferenceScreen): RecyclerView.Adapter<*> {
        if (savedActionModeState != null) {
            listView?.post { restoreActionMode() }
        }

        return super.onCreateAdapter(preferenceScreen)
    }

    private fun restoreActionMode() {
        try {
            val state = savedActionModeState ?: return
            if (state.getBoolean(KEY_ACTION_MODE_ENABLED)) {
                actionMode = (requireActivity() as PluginSettingsActivity).startSupportActionMode(this)

                val currentActionMode = actionMode
                val category = preferenceCategory
                if (currentActionMode != null && category != null) {
                    val jsonArray = state.getJSONArray(KEY_ACTION_MODE_SELECTED_ITEMS)
                    val selectedItems = SparseBooleanArray()

                    for (i in 0 until jsonArray.length()) {
                        selectedItems[jsonArray.getInt(i)] = true
                    }

                    for (i in 0 until category.preferenceCount) {
                        val preference = category.getPreference(i) as StoragePreference
                        preference.inSelectionMode = true
                        preference.checkbox.isChecked = selectedItems.get(i, false)
                    }
                }
            }
        } catch (ignored: JSONException) {}
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is StoragePreference) {
            val fragment = StoragePreferenceDialogFragment.newInstance(preference.key)
            @Suppress("DEPRECATION")
            fragment.setTargetFragment(this, 0)
            fragment.callback = this
            @Suppress("DEPRECATION")
            fragment.show(requireFragmentManager(), KEY_STORAGE_PREFERENCE_DIALOG)
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        try {
            val jsonObject = JSONObject()

            jsonObject.put(KEY_ACTION_MODE_ENABLED, actionMode != null)

            val category = preferenceCategory
            if (actionMode != null && category != null) {
                val jsonArray = JSONArray()

                for (i in 0 until category.preferenceCount) {
                    val preference = category.getPreference(i) as StoragePreference
                    if (preference.checkbox.isChecked) {
                        jsonArray.put(i)
                    }
                }

                jsonObject.put(KEY_ACTION_MODE_SELECTED_ITEMS, jsonArray)
            }

            outState.putString(KEY_ACTION_MODE_STATE, jsonObject.toString())
        } catch (ignored: JSONException) {}
    }

    private fun saveStorageInfoList() {
        val currentPlugin = this.plugin ?: return
        val preferences: SharedPreferences = currentPlugin.preferences

        val jsonArray = JSONArray()

        try {
            for (storageInfo in this.storageInfoList) {
                jsonArray.put(storageInfo.toJSON())
            }
        } catch (ignored: JSONException) {}

        preferences
            .edit()
            .putString(requireContext().getString(SftpPlugin.PREFERENCE_KEY_STORAGE_INFO_LIST), jsonArray.toString())
            .apply()
    }

    private fun sortStorageInfoListOnDisplayName() {
        Collections.sort(storageInfoList) { si1, si2 -> si1.displayName.compareTo(si2.displayName, ignoreCase = true) }
    }

    override fun isDisplayNameAllowed(displayName: String): StoragePreferenceDialogFragment.CallbackResult {
        val result = StoragePreferenceDialogFragment.CallbackResult().apply {
            isAllowed = true
        }

        if (displayName.isEmpty()) {
            result.isAllowed = false
            result.errorMessage = getString(R.string.sftp_storage_preference_display_name_cannot_be_empty)
        } else {
            for (storageInfo in storageInfoList) {
                if (storageInfo.displayName == displayName) {
                    result.isAllowed = false
                    result.errorMessage = getString(R.string.sftp_storage_preference_display_name_already_used)
                    break
                }
            }
        }

        return result
    }

    override fun isUriAllowed(uri: Uri): StoragePreferenceDialogFragment.CallbackResult {
        val result = StoragePreferenceDialogFragment.CallbackResult().apply {
            isAllowed = true
        }

        for (storageInfo in storageInfoList) {
            if (storageInfo.uri == uri) {
                result.isAllowed = false
                result.errorMessage = getString(R.string.sftp_storage_preference_storage_location_already_configured)
                break
            }
        }
        return result
    }

    override fun addNewStoragePreference(storageInfo: SftpPlugin.StorageInfo, takeFlags: Int) {
        storageInfoList.add(storageInfo)

        handleChangedStorageInfoList()

        requireContext().contentResolver.takePersistableUriPermission(storageInfo.uri, takeFlags)
    }

    private fun handleChangedStorageInfoList() {
        if (actionMode != null) {
            actionMode?.finish() // In case we are in selection mode, finish it
        }

        saveStorageInfoList()

        val category = preferenceCategory
        if (category != null) {
            category.removeAll()
            addStoragePreferences(category)
        }

        device.launchBackgroundReloadPluginsFromSettings()
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        val newStorageInfo = newValue as SftpPlugin.StorageInfo

        val it = storageInfoList.listIterator()

        while (it.hasNext()) {
            val storageInfo = it.next()
            if (storageInfo.uri == newStorageInfo.uri) {
                it.set(newStorageInfo)
                break
            }
        }

        handleChangedStorageInfoList()

        return false
    }

    override fun onLongClick(storagePreference: StoragePreference) {
        if (actionMode == null) {
            actionMode = (requireActivity() as PluginSettingsActivity).startSupportActionMode(this)

            val category = preferenceCategory
            if (actionMode != null && category != null) {
                for (i in 0 until category.preferenceCount) {
                    val preference = category.getPreference(i) as StoragePreference
                    preference.inSelectionMode = true
                    if (storagePreference == preference) {
                        preference.checkbox.isChecked = true
                    }
                }
            }
        }
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        val inflater = mode.menuInflater
        inflater.inflate(R.menu.sftp_settings_action_mode, menu)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        if (item.itemId == R.id.delete) {
            val category = preferenceCategory
            if (category != null) {
                for (i in category.preferenceCount - 1 downTo 0) {
                    val preference = category.getPreference(i) as StoragePreference
                    if (preference.checkbox.isChecked) {
                        val info = storageInfoList.removeAt(i)

                        try {
                            // This throws when trying to release a URI we don't have access to
                            requireContext().contentResolver.releasePersistableUriPermission(
                                info.uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )
                        } catch (e: SecurityException) {
                            // Usually safe to ignore, but who knows?
                            Log.e("SFTP Settings", "Exception", e)
                        }
                    }
                }
            }

            handleChangedStorageInfoList()
            return true
        }
        return false
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        actionMode = null

        val category = preferenceCategory
        if (category != null) {
            for (i in 0 until category.preferenceCount) {
                val preference = category.getPreference(i) as StoragePreference
                preference.inSelectionMode = false
                preference.checkbox.isChecked = false
            }
        }
    }

    companion object {
        private const val KEY_STORAGE_PREFERENCE_DIALOG = "StoragePreferenceDialog"
        private const val KEY_ACTION_MODE_STATE = "ActionModeState"
        private const val KEY_ACTION_MODE_ENABLED = "ActionModeEnabled"
        private const val KEY_ACTION_MODE_SELECTED_ITEMS = "ActionModeSelectedItems"

        @JvmStatic
        fun newInstance(pluginKey: String, deviceId: String, layout: Int): SftpSettingsFragment {
            return SftpSettingsFragment().apply {
                setArguments(pluginKey, deviceId, layout)
            }
        }

        @JvmStatic
        fun getStorageInfoList(context: Context, plugin: Plugin): MutableList<SftpPlugin.StorageInfo> {
            val storageInfoList = ArrayList<SftpPlugin.StorageInfo>()
            val deviceSettings = plugin.preferences
            val jsonString = deviceSettings.getString(context.getString(SftpPlugin.PREFERENCE_KEY_STORAGE_INFO_LIST), "[]")

            try {
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    storageInfoList.add(SftpPlugin.StorageInfo.fromJSON(jsonArray.getJSONObject(i)))
                }
            } catch (e: JSONException) {
                Log.e("SFTPSettings", "Couldn't load storage info", e)
            }

            return storageInfoList
        }

        @JvmStatic
        fun isDisplayNameUnique(storageInfoList: List<SftpPlugin.StorageInfo>, displayName: String, displayNameReadOnly: String): Boolean {
            for (info in storageInfoList) {
                if (info.displayName == displayName || info.displayName == displayName + displayNameReadOnly) {
                    return false
                }
            }
            return true
        }

        @JvmStatic
        fun isAlreadyConfigured(storageInfoList: List<SftpPlugin.StorageInfo>, sdCardUri: Uri): Boolean {
            for (info in storageInfoList) {
                if (info.uri == sdCardUri) {
                    return true
                }
            }
            return false
        }
    }
}
