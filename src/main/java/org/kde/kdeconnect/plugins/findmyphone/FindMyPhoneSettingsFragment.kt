/*
 * SPDX-FileCopyrightText: 2018 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.findmyphone

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreference
import org.kde.kdeconnect.ui.PluginSettingsFragment
import org.kde.kdeconnect_tp.R

class FindMyPhoneSettingsFragment : PluginSettingsFragment() {

    private lateinit var preferenceKeyRingtone: String
    private lateinit var preferenceKeyFlashlight: String
    private lateinit var sharedPreferences: SharedPreferences
    private var ringtonePreference: Preference? = null
    private var flashlightPreference: SwitchPreference? = null

    private val requestCameraPermission: ActivityResultLauncher<String> = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            setFlashlightEnabled(true)
            return@registerForActivityResult
        }
        if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", requireContext().packageName, null)
            }
            Toast.makeText(requireContext(), R.string.findmyphone_open_settings_for_camera, Toast.LENGTH_LONG).show()
            startActivity(intent)
            return@registerForActivityResult
        }
        Toast.makeText(requireContext(), R.string.findmyphone_camera_explanation, Toast.LENGTH_SHORT).show()
    }

    private val selectRingtoneLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val uri = IntentCompat.getParcelableExtra(
                result.data!!,
                RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                Uri::class.java
            )
            if (uri != null) {
                sharedPreferences.edit {
                    putString(preferenceKeyRingtone, uri.toString())
                }
                setRingtoneSummary()
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        preferenceKeyRingtone = getString(R.string.findmyphone_preference_key_ringtone)
        preferenceKeyFlashlight = getString(R.string.findmyphone_preference_key_flashlight)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        ringtonePreference = preferenceScreen.findPreference(preferenceKeyRingtone)
        flashlightPreference = preferenceScreen.findPreference(preferenceKeyFlashlight)

        setRingtoneSummary()

        flashlightPreference?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue == true) {
                if (hasCameraPermission()) {
                    return@setOnPreferenceChangeListener true
                }
                requestCameraPermission.launch(Manifest.permission.CAMERA)
                return@setOnPreferenceChangeListener false
            }
            true
        }
    }

    override fun onResume() {
        super.onResume()
        syncFlashlightPreferenceWithPermission()
    }

    private fun syncFlashlightPreferenceWithPermission() {
        val preferenceOn = sharedPreferences.getBoolean(preferenceKeyFlashlight, false)
        if (!hasCameraPermission() && preferenceOn) {
            setFlashlightEnabled(false)
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun setFlashlightEnabled(enabled: Boolean) {
        sharedPreferences.edit {
            putBoolean(preferenceKeyFlashlight, enabled)
        }
        flashlightPreference?.isChecked = enabled
    }

    private fun setRingtoneSummary() {
        val ringtone = sharedPreferences.getString(
            preferenceKeyRingtone,
            Settings.System.DEFAULT_RINGTONE_URI.toString()
        )
        val ringtoneUri = ringtone?.toUri() ?: Settings.System.DEFAULT_RINGTONE_URI
        ringtonePreference?.summary = RingtoneManager.getRingtone(requireContext(), ringtoneUri).getTitle(requireContext())
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.hasKey() && preference.key == preferenceKeyRingtone) {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_NOTIFICATION_URI)

                val existingValue = sharedPreferences.getString(preferenceKeyRingtone, null)
                if (existingValue != null) {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existingValue.toUri())
                } else {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Settings.System.DEFAULT_RINGTONE_URI)
                }
            }

            selectRingtoneLauncher.launch(intent)
            return true
        }
        return super.onPreferenceTreeClick(preference)
    }

    companion object {
        @JvmStatic
        fun newInstance(pluginKey: String, deviceId: String, layout: Int): FindMyPhoneSettingsFragment {
            return FindMyPhoneSettingsFragment().apply {
                setArguments(pluginKey, deviceId, layout)
            }
        }
    }
}
