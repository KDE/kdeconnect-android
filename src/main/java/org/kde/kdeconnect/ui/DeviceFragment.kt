/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui

import android.content.Intent
import android.os.Bundle
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StringRes
import androidx.annotation.UiThread
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.MenuProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.Device.PluginsChangedListener
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.PairingHandler
import org.kde.kdeconnect.base.BaseFragment
import org.kde.kdeconnect.extensions.setupBottomPadding
import org.kde.kdeconnect.helpers.security.SslHelper
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.battery.BatteryPlugin
import org.kde.kdeconnect.ui.compose.KdeTheme
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.ui.compose.screen.device.DeviceErrorScreen
import org.kde.kdeconnect.ui.compose.screen.device.DevicePairingScreen
import org.kde.kdeconnect.ui.compose.screen.device.DeviceViewModel
import org.kde.kdeconnect.ui.compose.screen.device.PluginsScreen
import org.kde.kdeconnect_tp.R
import org.kde.kdeconnect_tp.databinding.ActivityDeviceBinding

/**
 * Main view. Displays the current device and its plugins
 */
class DeviceFragment : BaseFragment<ActivityDeviceBinding>() {

    companion object {
        private const val ARG_DEVICE_ID = "deviceId"
        private const val ARG_FROM_DEVICE_LIST = "fromDeviceList"
        private const val TAG = "KDE/DeviceFragment"
        fun newInstance(deviceId: String?, fromDeviceList: Boolean): DeviceFragment {
            val frag = DeviceFragment()
            val args = Bundle()
            args.putString(ARG_DEVICE_ID, deviceId)
            args.putBoolean(ARG_FROM_DEVICE_LIST, fromDeviceList)
            frag.arguments = args
            return frag
        }
    }

    override fun getActionBarTitle() = null

    val deviceId: String by lazy {
        arguments?.getString(ARG_DEVICE_ID)
            ?: throw RuntimeException("You must instantiate a new DeviceFragment using DeviceFragment.newInstance()")
    }

    private val device by lazy { KdeConnect.getInstance().getDevice(deviceId) }

    private val viewModel by viewModels<DeviceViewModel>()

    override fun onInflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): ActivityDeviceBinding {
        val fromDeviceList = requireArguments().getBoolean(ARG_FROM_DEVICE_LIST, false)
        if (fromDeviceList) {
            val callback = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Handle back button, so we go to the list of devices in case we came from there
                    (mActivity as? MainActivity)?.onDeviceSelected(null)
                }
            }
            requireActivity().onBackPressedDispatcher.addCallback(getViewLifecycleOwner(), callback)
        }
        return ActivityDeviceBinding.inflate(inflater, container, false)
    }

    private val menuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menu.clear()
            val device = device ?: return

            //Plugins button list
            val menuEntries: Collection<Plugin.PluginUiMenuEntry> =
                device.loadedPlugins.values.flatMap { it.getUiMenuEntries() }
            for (p in menuEntries) {
                menu.add(p.name).setOnMenuItemClickListener {
                    p.onClick(mActivity!!)
                    true
                }
            }
            val intent = Intent(mActivity, PluginSettingsActivity::class.java)
            intent.putExtra("deviceId", deviceId)
            menu.add(R.string.device_menu_plugins).setOnMenuItemClickListener {
                startActivity(intent)
                true
            }
            if (device.isReachable) {
                val builder = MaterialAlertDialogBuilder(requireContext())
                builder.setTitle(requireContext().resources.getString(R.string.encryption_info_title))
                builder.setPositiveButton(requireContext().resources.getString(R.string.ok)) { dialog, _ ->
                    dialog.dismiss()
                }
                builder.setMessage(
                    "${
                        requireContext().resources.getString(R.string.my_device_fingerprint)
                    } \n ${
                        SslHelper.getCertificateHash(SslHelper.certificate)
                    } \n\n ${
                        requireContext().resources.getString(R.string.remote_device_fingerprint)
                    } \n ${
                        SslHelper.getCertificateHash(device.certificate)
                    } \n\n ${
                        requireContext().resources.getString(R.string.protocol_version)
                    } ${
                        device.protocolVersion
                    }"
                )
                menu.add(R.string.encryption_info_title).setOnMenuItemClickListener {
                    builder.show()
                    true
                }
            }
            if (device.isPaired) {
                menu.add(R.string.device_menu_unpair).setOnMenuItemClickListener {
                    device.apply {
                        // Remove listener so buttons don't show for an instant before changing the view
                        removePairingCallback(pairingCallback)
                        removePluginsChangedListener(pluginsChangedListener)
                        unpair()
                    }
                    (mActivity as? MainActivity)?.onDeviceSelected(null)
                    true
                }
            }
            if (device.pairStatus == PairingHandler.PairState.Requested) {
                menu.add(R.string.cancel_pairing).setOnMenuItemClickListener {
                    device.cancelPairing()
                    true
                }
            }
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            return true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.deviceView.setupBottomPadding()
        device?.apply {
            mActivity?.supportActionBar?.title = name
            removePairingCallback(pairingCallback)
            addPairingCallback(pairingCallback)
            removePluginsChangedListener(pluginsChangedListener)
            addPluginsChangedListener(pluginsChangedListener)
        } ?: run { // device is null
            Log.e(TAG, "Trying to display a device fragment but the device is not present")
            (mActivity as? MainActivity)?.onDeviceSelected(null)
        }
        mActivity?.addMenuProvider(menuProvider, viewLifecycleOwner)
        refreshUI()
    }

    private fun refreshDevicesAction() {
        BackgroundService.ForceRefreshConnections(requireContext())
        viewModel.setRefreshing(refreshing = true)
        binding.deviceView.postDelayed({
            if (isResumed && !isDetached) { // the view might be destroyed by now
                viewModel.setRefreshing(refreshing = false)
            }
        }, 1500)
    }

    override fun onPause() {
        viewModel.setRefreshing(refreshing = false)
        super.onPause()
    }

    private val pluginsChangedListener =
        PluginsChangedListener { mActivity?.runOnUiThread { refreshUI() } }

    override fun onDestroyView() {
        device?.apply {
            removePluginsChangedListener(pluginsChangedListener)
            removePairingCallback(pairingCallback)
        }
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        with(requireView()) {
            isFocusableInTouchMode = true
            requestFocus()
        }
    }

    @UiThread
    private fun refreshUI() {
        if (!hasBinding) return // in case onDestroyView has already been called
        val device = device ?: return

        //Once in-app, there is no point in keep displaying the notification if any
        device.hidePairingNotification()

        binding.deviceView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                KdeTheme(context) {
                    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
                    val pairingErrorMessage by viewModel.pairingErrorMessage.collectAsStateWithLifecycle()

                    when (device.pairStatus) {
                        PairingHandler.PairState.Paired -> {
                            if (device.isReachable) {
                                val pluginsWithButtons =
                                    device.loadedPlugins.values.flatMap { plugin -> plugin.getUiButtons() }
                                val pluginsNeedPermissions =
                                    device.pluginsWithoutPermissions.values.filter { plugin ->
                                        device.isPluginEnabled(plugin.pluginKey)
                                    }
                                val pluginsNeedOptionalPermissions =
                                    device.pluginsWithoutOptionalPermissions.values.filter { plugin ->
                                        device.isPluginEnabled(plugin.pluginKey)
                                    }
                                PluginsScreen(
                                    pluginsWithButtons = pluginsWithButtons,
                                    pluginsNeedPermissions = pluginsNeedPermissions,
                                    pluginsNeedOptionalPermissions = pluginsNeedOptionalPermissions,
                                    onButtonClick = { button -> button.onClick(mActivity!!) },
                                    actionNeedPermissions = { plugin ->
                                        plugin.permissionExplanationDialog.show(
                                            childFragmentManager,
                                            null
                                        )
                                    },
                                    actionNeedOptionalPermissions = { plugin ->
                                        plugin.optionalPermissionExplanationDialog.show(
                                            childFragmentManager,
                                            null
                                        )
                                    }
                                )
                                displayBatteryInfoIfPossible()
                            } else {
                                DeviceErrorScreen(
                                    isRefreshing = isRefreshing,
                                    onRefresh = { refreshDevicesAction() }
                                )
                            }
                        }

                        else -> {
                            DevicePairingScreen(
                                pairStatus = device.pairStatus,
                                verificationKey = device.verificationKey ?: "",
                                pairMessage = pairingErrorMessage,
                                onRequestPairing = {
                                    viewModel.setPairingErrorMessage(message =null)
                                    device.requestPairing()
                                    refreshUI()
                                },
                                onAcceptPairing = {
                                    device.acceptPairing()
                                    refreshUI()
                                },
                                onRejectPairing = {
                                    device.apply {
                                        // Remove listener so buttons don't show for an instant before changing the view
                                        removePluginsChangedListener(pluginsChangedListener)
                                        removePairingCallback(pairingCallback)
                                        cancelPairing()
                                    }
                                    (mActivity as? MainActivity)?.onDeviceSelected(null)
                                }
                            )
                        }
                    }
                }
            }
        }
        mActivity?.invalidateOptionsMenu()
    }

    private val pairingCallback: PairingHandler.PairingCallback =
        object : PairingHandler.PairingCallback {
            override fun incomingPairRequest() {
                mActivity?.runOnUiThread { refreshUI() }
            }

            override fun pairingSuccessful() {
                val accessibilityManager =
                    requireContext().getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                if (accessibilityManager?.isEnabled == true) {
                    @Suppress("DEPRECATION")
                    val event = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        AccessibilityEvent(AccessibilityEvent.TYPE_ANNOUNCEMENT)
                    } else {
                        AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT)
                    }
                    event.text.add(getString(R.string.pair_succeeded))
                    accessibilityManager.sendAccessibilityEvent(event)
                }
                mActivity?.runOnUiThread { refreshUI() }
            }

            override fun pairingFailed(error: String) {
                mActivity?.runOnUiThread {
                    viewModel.setPairingErrorMessage(message =error)
                    refreshUI()
                }
            }

            override fun unpaired(device: Device) {
                mActivity?.runOnUiThread {
                    viewModel.setPairingErrorMessage(message =null)
                    refreshUI()
                }
            }
        }


    /**
     * This method tries to display battery info for the remote device. Includes
     *
     *  * Current charge as a percentage
     *  * Whether the remote device is low on power
     *  * Whether the remote device is currently charging
     *
     */
    private fun displayBatteryInfoIfPossible() {
        val batteryPlugin = device?.getPlugin(BatteryPlugin::class.java)

        val info = batteryPlugin?.remoteBatteryInfo
        if (info != null) {

            @StringRes
            val resId = when {
                info.isCharging -> R.string.battery_status_charging_format
                BatteryPlugin.isLowBattery(info) -> R.string.battery_status_low_format
                else -> R.string.battery_status_format
            }

            mActivity?.supportActionBar?.subtitle = mActivity?.getString(resId, info.currentCharge)
        } else {
            mActivity?.supportActionBar?.subtitle = null
        }
    }
}
