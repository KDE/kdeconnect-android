/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.UserInterface

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
import androidx.core.content.ContextCompat
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.io.IOUtils
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.Helpers.CreateFileParams
import org.kde.kdeconnect.Helpers.CreateFileResultContract
import org.kde.kdeconnect.Helpers.DeviceHelper
import org.kde.kdeconnect.Helpers.DeviceHelper.filterInvalidCharactersFromDeviceName
import org.kde.kdeconnect.Helpers.DeviceHelper.getDeviceName
import org.kde.kdeconnect.Helpers.NotificationHelper
import org.kde.kdeconnect.UserInterface.ThemeUtil.applyTheme
import org.kde.kdeconnect.extensions.setupBottomPadding
import org.kde.kdeconnect_tp.BuildConfig
import org.kde.kdeconnect_tp.R
import java.io.InputStreamReader

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        if (activity != null) {
            (activity as? MainActivity)?.supportActionBar?.setTitle(R.string.settings)
            (activity as? MainActivity)?.supportActionBar?.subtitle = null
        }
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)

        listOf(
            deviceNamePref(context),
            themePref(context),
            persistentNotificationPref(context),
            trustedNetworkPref(context),
            devicesByIpPref(context),
            mdnsDiscoveryPref(context),
            bluetoothSupportPref(context),
            exportLogsPref(context),
            moreSettingsPref(context),
        ).forEach(screen::addPreference)

        preferenceScreen = screen
    }

    private fun mdnsDiscoveryPref(context: Context) = SwitchPreference(context).apply {
        setDefaultValue(false)
        key = KEY_MDNS_DISCOVERY_ENABLED
        setTitle(R.string.enable_mdns_broadcast)
        setSummary(R.string.enable_mdns_broadcast_explanation)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.setupBottomPadding()
    }

    private fun deviceNamePref(context: Context) = EditTextPreference(context).apply {
        key = DeviceHelper.KEY_DEVICE_NAME_PREFERENCE
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

    private fun trustedNetworkPref(context: Context) = Preference(context).apply {
        isPersistent = false
        setTitle(R.string.trusted_networks)
        setSummary(R.string.trusted_networks_desc)
        onPreferenceClickListener = Preference.OnPreferenceClickListener {
            startActivityForResult(Intent(context, TrustedNetworksActivity::class.java), REQUEST_REFRESH_NETWORKS)
            true
        }
    }

    private val REQUEST_REFRESH_DEVICES_BY_IP = 1
    private val REQUEST_REFRESH_NETWORKS = 2

    private lateinit var devicesByIpPref : Preference

    /** Opens activity to configure device by IP when clicked */
    private fun devicesByIpPref(context: Context) = Preference(context).apply {
        devicesByIpPref = this
        isPersistent = false
        setTitle(R.string.custom_device_list)
        updateDevicesByIpSummary()
        onPreferenceClickListener = Preference.OnPreferenceClickListener {
            startActivityForResult(Intent(context, CustomDevicesActivity::class.java), REQUEST_REFRESH_DEVICES_BY_IP)
            true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_REFRESH_DEVICES_BY_IP -> updateDevicesByIpSummary()
            REQUEST_REFRESH_NETWORKS -> BackgroundService.instance?.onNetworkChange(null)
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun updateDevicesByIpSummary() {
        devicesByIpPref.setSummary(getString(
            R.string.custom_devices_settings_summary,
            CustomDevicesActivity.getCustomDeviceList(context).size
        ))
    }

    private fun bluetoothSupportPref(context: Context) = SwitchPreference(context).apply {
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
        val output = uri?.let { context?.contentResolver?.openOutputStream(uri) } ?: return@registerForActivityResult
        CoroutineScope(Dispatchers.IO).launch {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d"))
            val reader = InputStreamReader(process.inputStream)
            output.use {
                it.write("KDE Connect ${BuildConfig.VERSION_NAME}\n".toByteArray(Charsets.UTF_8))
                it.write("Android ${Build.VERSION.RELEASE} (${Build.MANUFACTURER} ${Build.MODEL})\n".toByteArray(Charsets.UTF_8))
                IOUtils.copy(reader, it, Charsets.UTF_8)
            }
        }
    }

    private fun moreSettingsPref(context: Context) = Preference(context).apply {
        isPersistent = false
        isSelectable = false
        setTitle(R.string.settings_more_settings_title)
        setSummary(R.string.settings_more_settings_text)
    }

    companion object {
        const val KEY_MDNS_DISCOVERY_ENABLED = "mdns_enabled"
        const val KEY_BLUETOOTH_ENABLED: String = "bluetooth_enabled"
        const val KEY_APP_THEME: String = "theme_pref"
    }
}
