/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.text.InputFilter.LengthFilter
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.extensions.setupBottomPadding
import org.kde.kdeconnect.helpers.CreateFileParams
import org.kde.kdeconnect.helpers.CreateFileResultContract
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.helpers.DeviceHelper.filterInvalidCharactersFromDeviceName
import org.kde.kdeconnect.helpers.DeviceHelper.getDeviceName
import org.kde.kdeconnect.helpers.NotificationHelper
import org.kde.kdeconnect.ui.ThemeUtil.applyTheme
import org.kde.kdeconnect_tp.BuildConfig
import org.kde.kdeconnect_tp.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        if (activity != null) {
            val mainActivity = activity as? MainActivity
            if (mainActivity?.isTwoPaneLayout != true) {
                mainActivity?.supportActionBar?.setTitle(R.string.settings)
                mainActivity?.supportActionBar?.subtitle = null
            }
        }
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)
        if ((activity as? MainActivity)?.isTwoPaneLayout == true) {
            screen.addPreference(Preference(context).apply {
                key = SETTINGS_HEADER_KEY
                title = getString(R.string.settings)
                isSelectable = false
                setLayoutResource(R.layout.ecosystem_settings_heading)
            })
        }

        fun addCategory(title: Int, preferences: List<Preference>) {
            val category = PreferenceCategory(context).apply {
                setTitle(title)
                setLayoutResource(R.layout.ecosystem_preference_category)
            }
            // Attach the category first so PreferenceGroup has its manager before it
            // assigns IDs to the child preferences.
            screen.addPreference(category)
            preferences.forEach(category::addPreference)
        }

        addCategory(
            R.string.settings_category_device,
            listOf(deviceNamePref(context), themePref(context)),
        )
        addCategory(
            R.string.settings_category_connections,
            listOf(trustedNetworkPref(context), devicesByIpPref(context), bluetoothSupportPref(context)),
        )
        addCategory(
            R.string.settings_category_app,
            listOf(persistentNotificationPref(context), exportLogsPref(context), moreSettingsPref(context)),
        )

        addCategory(R.string.background_features_title, listOf(
            Preference(context).apply {
                setTitle(R.string.health_title)
                setSummary(R.string.health_summary)
                setLayoutResource(R.layout.ecosystem_preference_item)
                setOnPreferenceClickListener {
                    startActivity(Intent(context, org.kde.kdeconnect.ui.background.BackgroundFeaturesActivity::class.java)
                        .putExtra(org.kde.kdeconnect.ui.background.BackgroundFeaturesActivity.EXTRA_SCREEN,
                            org.kde.kdeconnect.ui.background.BackgroundFeaturesActivity.SCREEN_HEALTH))
                    true
                }
            },
            Preference(context).apply {
                setTitle(R.string.disconnect_log_title)
                setSummary(R.string.disconnect_log_summary)
                setLayoutResource(R.layout.ecosystem_preference_item)
                setOnPreferenceClickListener {
                    startActivity(Intent(context, org.kde.kdeconnect.ui.background.BackgroundFeaturesActivity::class.java)
                        .putExtra(org.kde.kdeconnect.ui.background.BackgroundFeaturesActivity.EXTRA_SCREEN,
                            org.kde.kdeconnect.ui.background.BackgroundFeaturesActivity.SCREEN_DISCONNECT_LOG))
                    true
                }
            },
            Preference(context).apply {
                setTitle(R.string.shizuku_setup_title)
                setSummary(R.string.shizuku_setup_summary)
                setLayoutResource(R.layout.ecosystem_preference_item)
                setOnPreferenceClickListener {
                    startActivity(Intent(context, org.kde.kdeconnect.ui.background.BackgroundFeaturesActivity::class.java))
                    true
                }
            },
        ))
        preferenceScreen = screen
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView = listView
        val isWideWindow = (activity as? MainActivity)?.isTwoPaneLayout == true
        val layoutManager = GridLayoutManager(requireContext(), if (isWideWindow) 2 else 1)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                val preference = (recyclerView.adapter as? PreferenceGroupAdapter)?.getItem(position)
                return if (preference is PreferenceCategory || preference?.key == SETTINGS_HEADER_KEY) {
                    layoutManager.spanCount
                } else {
                    1
                }
            }
        }
        recyclerView.layoutManager = layoutManager
        val horizontalInset = (16 * resources.displayMetrics.density).toInt()
        val verticalInset = (8 * resources.displayMetrics.density).toInt()
        recyclerView.setPadding(horizontalInset, verticalInset, horizontalInset, recyclerView.paddingBottom)
        recyclerView.clipToPadding = false
        recyclerView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.ecosystem_toolbar))
        recyclerView.addItemDecoration(PreferenceSpacingDecoration((8 * resources.displayMetrics.density).toInt()))
        recyclerView.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            val widthDp = (right - left) / resources.displayMetrics.density
            val columns = if (widthDp >= 600) 2 else 1
            if (layoutManager.spanCount != columns) layoutManager.spanCount = columns
        }
        recyclerView.post {
            val widthDp = recyclerView.width / resources.displayMetrics.density
            layoutManager.spanCount = if (widthDp >= 600) 2 else 1
        }
        listView.setupBottomPadding()
    }

    private fun deviceNamePref(context: Context) = EditTextPreference(context).apply {
        key = DeviceHelper.KEY_DEVICE_NAME_PREFERENCE
        icon = DeviceHelper.deviceType.getIcon(context)
        setLayoutResource(R.layout.ecosystem_preference_item)
        isSelectable = true
        setOnBindEditTextListener(EditText::setSingleLine)
        setOnBindEditTextListener { editText: EditText ->
            editText.filters = arrayOf(
                InputFilter { source: CharSequence, start: Int, end: Int, _: Spanned?, _: Int, _: Int ->
                    filterInvalidCharactersFromDeviceName(source.subSequence(start, end).toString())
                },
                LengthFilter(DeviceHelper.MAX_DEVICE_NAME_LENGTH),
            )
        }
        val deviceName = getDeviceName(context)
        setTitle(R.string.settings_rename)
        summary = deviceName
        setDialogTitle(R.string.device_rename_title)
        text = deviceName
        setPositiveButtonText(R.string.device_rename_confirm)
        setNegativeButtonText(R.string.cancel)
        onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, new: Any? ->
            val name = new as String?
            if (name.isNullOrBlank()) {
                if (view != null) {
                    val snackbar = Snackbar.make(requireView(), R.string.invalid_device_name, Snackbar.LENGTH_LONG)
                    val currentTheme = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    if (currentTheme != Configuration.UI_MODE_NIGHT_YES) {
                        // white color is set to the background of snackbar if dark mode is off
                        snackbar.view.setBackgroundColor(Color.WHITE)
                    }
                    snackbar.show()
                }
                false
            }
            else {
                summary = new
                true
            }
        }
    }

    private fun themePref(context: Context) = ListPreference(context).apply {
        icon = ContextCompat.getDrawable(context, R.drawable.ic_action_image_edit_24dp)
        setLayoutResource(R.layout.ecosystem_preference_item)
        key = KEY_APP_THEME
        setTitle(R.string.theme_dialog_title)
        setDialogTitle(R.string.theme_dialog_title)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setEntries(R.array.theme_list_v28) else setEntries(R.array.theme_list)
        setEntryValues(R.array.theme_list_values)
        setDefaultValue(ThemeUtil.DEFAULT_MODE)
        summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, new: Any? ->
            if (new is String) {
                applyTheme(new)
            }
            true
        }
    }

    private fun persistentNotificationPref(context: Context): Preference =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Preference(context).apply {
            icon = ContextCompat.getDrawable(context, R.drawable.ic_baseline_info_24)
            setLayoutResource(R.layout.ecosystem_preference_item)
            setTitle(R.string.setting_persistent_notification_oreo)
            setSummary(R.string.setting_persistent_notification_description)
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                val intent = Intent()
                intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS")
                intent.putExtra("android.provider.extra.APP_PACKAGE", context.packageName)
                context.startActivity(intent)
                true
            }
        }
        // Persistent notification toggle for Android Versions below Oreo
        else SwitchPreference(context).apply {
            icon = ContextCompat.getDrawable(context, R.drawable.ic_baseline_info_24)
            setLayoutResource(R.layout.ecosystem_preference_item)
            isPersistent = false
            isChecked = NotificationHelper.isPersistentNotificationEnabled(context)
            setTitle(R.string.setting_persistent_notification)
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, new: Any ->
                val isChecked = new as Boolean
                NotificationHelper.setPersistentNotificationEnabled(context, isChecked)
                BackgroundService.instance?.changePersistentNotificationVisibility(isChecked)
                NotificationHelper.setPersistentNotificationEnabled(context, isChecked)
                true
            }
        }

    val activityLauncherWithDevicesByIpRefresh = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updateDevicesByIpSummary()
    }

    private fun trustedNetworkPref(context: Context) = Preference(context).apply {
        icon = ContextCompat.getDrawable(context, R.drawable.ic_wifi)
        setLayoutResource(R.layout.ecosystem_preference_item)
        isPersistent = false
        setTitle(R.string.trusted_networks)
        setSummary(R.string.trusted_networks_desc)
        onPreferenceClickListener = Preference.OnPreferenceClickListener {
            activityLauncherWithNetworkRefresh.launch(Intent(context, TrustedNetworksActivity::class.java))
            true
        }
    }

    private lateinit var devicesByIpPref : Preference

    val activityLauncherWithNetworkRefresh = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        BackgroundService.instance?.onNetworkChange(null)
    }

    /** Opens activity to configure device by IP when clicked */
    private fun devicesByIpPref(context: Context) = Preference(context).apply {
        icon = ContextCompat.getDrawable(context, R.drawable.ic_device_desktop_32dp)
        setLayoutResource(R.layout.ecosystem_preference_item)
        devicesByIpPref = this
        isPersistent = false
        setTitle(R.string.custom_device_list)
        updateDevicesByIpSummary()
        onPreferenceClickListener = Preference.OnPreferenceClickListener {
            activityLauncherWithDevicesByIpRefresh.launch(Intent(context, CustomDevicesActivity::class.java))
            true
        }
    }

    private fun updateDevicesByIpSummary() {
        devicesByIpPref.setSummary(getString(
            R.string.custom_devices_settings_summary,
            CustomDevicesActivity.getCustomDeviceList(requireContext()).size
        ))
    }

    private fun bluetoothSupportPref(context: Context) = SwitchPreference(context).apply {
        icon = ContextCompat.getDrawable(context, R.drawable.ic_device_phone_32dp)
        setLayoutResource(R.layout.ecosystem_preference_item)
        setDefaultValue(false)
        key = KEY_BLUETOOTH_ENABLED
        setTitle(R.string.enable_bluetooth)
        onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && newValue as Boolean) {
                val permissions = arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
                val permissionsGranted = permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
                if (!permissionsGranted) {
                    PermissionsAlertDialogFragment.Builder()
                        .setTitle(R.string.location_permission_needed_title)
                        .setMessage(R.string.bluetooth_permission_needed_desc)
                        .setPermissions(permissions)
                        .setRequestCode(2)
                        .create().show(childFragmentManager, null)
                    return@OnPreferenceChangeListener false
                }
            }
            true
        }
    }

    private fun exportLogsPref(context: Context) = Preference(context).apply {
        icon = ContextCompat.getDrawable(context, R.drawable.ic_baseline_info_24)
        setLayoutResource(R.layout.ecosystem_preference_item)
        isPersistent = false
        setTitle(R.string.settings_export_logs)
        setSummary(R.string.settings_export_logs_text)
        onPreferenceClickListener = Preference.OnPreferenceClickListener {
            exportLogs.launch(CreateFileParams("text/plain", "kdeconnect-log.txt"))
            true
        }
    }

    private val exportLogs: ActivityResultLauncher<CreateFileParams> = registerForActivityResult(
        CreateFileResultContract()
    ) { uri: Uri? ->
            val output = uri?.let { context?.contentResolver?.openOutputStream(it) }
                ?: return@registerForActivityResult

            CoroutineScope(Dispatchers.IO).launch {
                val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d"))
                process.inputStream.reader(Charsets.UTF_8).use { reader ->
                    output.writer(Charsets.UTF_8).use { writer ->
                        writer.write("KDE Connect ${BuildConfig.VERSION_NAME}\n")
                        writer.write("Android ${Build.VERSION.RELEASE} (${Build.MANUFACTURER} ${Build.MODEL})\n")
                        reader.copyTo(writer)
                    }
                }
            }
        }

    private fun moreSettingsPref(context: Context) = Preference(context).apply {
        icon = ContextCompat.getDrawable(context, R.drawable.ic_settings_24dp)
        setLayoutResource(R.layout.ecosystem_preference_item)
        isPersistent = false
        isSelectable = false
        setTitle(R.string.settings_more_settings_title)
        setSummary(R.string.settings_more_settings_text)
    }

    companion object {
        private const val SETTINGS_HEADER_KEY = "ecosystem.settings.header"
        const val KEY_BLUETOOTH_ENABLED: String = "bluetooth_enabled"
        const val KEY_APP_THEME: String = "theme_pref"
    }
}

private class PreferenceSpacingDecoration(private val spacing: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: android.graphics.Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        outRect.top = spacing / 2
        outRect.bottom = spacing / 2
        val grid = parent.layoutManager as? GridLayoutManager
        val position = parent.getChildAdapterPosition(view)
        val preference = if (position != RecyclerView.NO_POSITION) {
            (parent.adapter as? PreferenceGroupAdapter)?.getItem(position)
        } else {
            null
        }
        if ((grid?.spanCount ?: 1) > 1 && preference !is PreferenceCategory && preference?.key != "ecosystem.settings.header") {
            outRect.left = spacing / 2
            outRect.right = spacing / 2
        }
    }
}
