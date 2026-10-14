/*
 * SPDX-FileCopyrightText: 2026 Ecosystem contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.share

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.DragAndDropPermissions
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.edit
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.helpers.IntentHelper
import org.kde.kdeconnect.helpers.ThreadHelper
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.compose.screen.share.ShareScreen
import org.kde.kdeconnect_tp.R

class ShareFragment : Fragment() {

    private var targetDeviceId: String? = null
    private var targetDeviceName by mutableStateOf<String?>(null)
    private var targetDeviceConnected by mutableStateOf(false)
    private var isDropActive by mutableStateOf(false)
    private var transfers by mutableStateOf(emptyList<ShareTransferRecord>())
    private var pendingShareIntent: Intent? = null

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) sendFiles(uris)
    }

    private val transferHistoryListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == ShareTransferHistory.PREFERENCE_KEY && isAdded) {
            activity?.runOnUiThread {
                if (isAdded) transfers = ShareTransferHistory.get(requireContext())
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                KdeTheme(requireContext()) {
                    ShareScreen(
                        targetDeviceName = targetDeviceName,
                        targetDeviceConnected = targetDeviceConnected,
                        transfers = transfers,
                        isDropActive = isDropActive,
                        onChooseFiles = { filePicker.launch(arrayOf("*/*")) },
                        onFilesDropped = ::sendFiles,
                        onDropActiveChanged = { isDropActive = it },
                    )
                }
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState == null) {
            pendingShareIntent = arguments?.let { bundle ->
                BundleCompat.getParcelable(bundle, ARG_SHARE_INTENT, Intent::class.java)
            }
        }
        refreshTargetDevice()
    }

    override fun onStart() {
        super.onStart()
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        preferences.registerOnSharedPreferenceChangeListener(transferHistoryListener)
        transfers = ShareTransferHistory.get(requireContext())
        BackgroundService.Start(requireContext().applicationContext)
        KdeConnect.getInstance().addDeviceListChangedCallback(CALLBACK_ID) {
            activity?.runOnUiThread { refreshTargetDevice() }
        }
        refreshTargetDevice()
    }

    override fun onStop() {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .unregisterOnSharedPreferenceChangeListener(transferHistoryListener)
        KdeConnect.getInstance().removeDeviceListChangedCallback(CALLBACK_ID)
        super.onStop()
    }

    private fun refreshTargetDevice() {
        if (!isAdded) return
        targetDeviceId = arguments?.getString(ARG_DEVICE_ID)
            ?: MainActivity.getSelectedDeviceId(requireContext())
        val device = targetDeviceId?.let { id -> KdeConnect.getInstance().getDevice(id) }
        targetDeviceName = device?.name
        targetDeviceConnected = device?.isPaired == true && device.isReachable
        val incomingIntent = pendingShareIntent
        if (device != null && incomingIntent != null) {
            pendingShareIntent = null
            sendIncomingShare(incomingIntent, device)
        }
    }

    private fun sendFiles(uris: List<Uri>, dragAndDropPermissions: DragAndDropPermissions? = null) {
        val deviceId = targetDeviceId
        if (deviceId == null || !targetDeviceConnected) {
            dragAndDropPermissions?.release()
            Toast.makeText(requireContext(), R.string.share_choose_device_first, Toast.LENGTH_SHORT).show()
            return
        }

        ThreadHelper.execute {
            try {
                val plugin = KdeConnect.getInstance().getDevicePlugin<SharePlugin>(deviceId)
                if (plugin == null) {
                    activity?.runOnUiThread { showToast(R.string.share_transfer_unavailable) }
                } else {
                    plugin.sendFiles(uris)
                }
            } catch (_: Exception) {
                activity?.runOnUiThread { showToast(R.string.share_transfer_failed) }
            } finally {
                dragAndDropPermissions?.release()
            }
        }
    }

    private fun sendIncomingShare(intent: Intent, device: Device) {
        if (!device.isReachable) {
            val urls = IntentHelper.parseIntentUrls(intent)
            if (urls.isNotEmpty()) {
                storeUrlsForLater(device, urls.toSet())
                Toast.makeText(requireContext(), R.string.unreachable_share_toast, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), R.string.share_device_offline_error, Toast.LENGTH_LONG).show()
            }
            return
        }

        ThreadHelper.execute {
            val plugin = KdeConnect.getInstance().getDevicePlugin<SharePlugin>(device.deviceId)
            if (plugin == null) {
                activity?.runOnUiThread { showToast(R.string.share_transfer_unavailable) }
            } else {
                plugin.share(intent)
            }
        }
    }

    private fun showToast(messageRes: Int) {
        if (isAdded) Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun storeUrlsForLater(device: Device, urls: Set<String>) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val key = SharePlugin.KEY_UNREACHABLE_URL_LIST + device.deviceId
        val existingUrls = preferences.getStringSet(key, null) ?: emptySet()
        preferences.edit { putStringSet(key, urls + existingUrls) }
    }

    companion object {
        private const val ARG_DEVICE_ID = "share_device_id"
        private const val ARG_SHARE_INTENT = "share_intent"
        private const val CALLBACK_ID = "ShareFragment"

        fun newInstance(deviceId: String?, shareIntent: Intent? = null) = ShareFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_DEVICE_ID, deviceId)
                shareIntent?.let { putParcelable(ARG_SHARE_INTENT, it) }
            }
        }
    }
}
