/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
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

    /**
     * Not-yet-paired ViewBinding.
     *
     * Used to start and retry pairing.
     */
    private val pairingBinding get() = binding.pairRequest

    /**
     * Cannot-communicate ViewBinding.
     *
     * Used when the remote device is unreachable.
     */
    private val errorBinding get() = binding.pairError

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
        errorBinding.errorMessageContainer.setOnRefreshListener {
            this.refreshDevicesAction()
        }
        pairingBinding.pairButton.setOnClickListener {
            device?.requestPairing()
            refreshUI()
        }
        pairingBinding.acceptButton.setOnClickListener {
            device?.apply {
                acceptPairing()
                pairingBinding.pairingButtons.visibility = View.GONE
            }
        }
        pairingBinding.rejectButton.setOnClickListener {
            device?.apply {
                // Remove listener so buttons don't show for an instant before changing the view
                removePluginsChangedListener(pluginsChangedListener)
                removePairingCallback(pairingCallback)
                cancelPairing()
            }
            (mActivity as? MainActivity)?.onDeviceSelected(null)
        }
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
        errorBinding.errorMessageContainer.isRefreshing = true
        errorBinding.errorMessageContainer.postDelayed({
            if (isResumed && !isDetached) { // the view might be destroyed by now
                errorBinding.errorMessageContainer.isRefreshing = false
            }
        }, 1500)
    }

    override fun onPause() {
        errorBinding.errorMessageContainer.isRefreshing = false
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

        when (device.pairStatus) {
            PairingHandler.PairState.NotPaired -> {
                errorBinding.errorMessageContainer.visibility = View.GONE
                binding.deviceView.visibility = View.GONE
                pairingBinding.pairingButtons.visibility = View.VISIBLE
                pairingBinding.pairVerification.visibility = View.GONE
            }

            PairingHandler.PairState.Requested -> {
                with(pairingBinding) {
                    pairButton.visibility = View.GONE
                    pairMessage.text = getString(R.string.pair_requested)
                    pairProgress.visibility = View.VISIBLE
                    pairVerification.text = device.verificationKey
                    pairVerification.visibility = View.VISIBLE
                }
            }

            PairingHandler.PairState.RequestedByPeer -> {
                with(pairingBinding) {
                    pairMessage.setText(R.string.pair_requested)
                    pairVerification.visibility = View.VISIBLE
                    pairingButtons.visibility = View.VISIBLE
                    pairProgress.visibility = View.GONE
                    pairButton.visibility = View.GONE
                    pairRequestButtons.visibility = View.VISIBLE
                    pairVerification.text = device.verificationKey
                    pairVerification.visibility = View.VISIBLE
                }
                binding.deviceView.visibility = View.GONE
            }

            PairingHandler.PairState.Paired -> {
                pairingBinding.pairingButtons.visibility = View.GONE
                if (device.isReachable) {
                    val context = requireContext()
                    val pluginsWithButtons =
                        device.loadedPlugins.values.flatMap { it.getUiButtons() }
                    val pluginsNeedPermissions =
                        device.pluginsWithoutPermissions.values.filter { device.isPluginEnabled(it.pluginKey) }
                    val pluginsNeedOptionalPermissions =
                        device.pluginsWithoutOptionalPermissions.values.filter {
                            device.isPluginEnabled(it.pluginKey)
                        }
                    errorBinding.errorMessageContainer.visibility = View.GONE
                    binding.deviceView.visibility = View.VISIBLE
                    binding.deviceViewCompose.apply {
                        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                        setContent {
                            KdeTheme(context) {
                                PluginsScreen(
                                    pluginsWithButtons = pluginsWithButtons,
                                    pluginsNeedPermissions = pluginsNeedPermissions,
                                    pluginsNeedOptionalPermissions = pluginsNeedOptionalPermissions,
                                    onButtonClick = { button -> button.onClick(mActivity!!) },
                                    action = { plugin ->
                                        plugin.optionalPermissionExplanationDialog.show(
                                            childFragmentManager,
                                            null
                                        )
                                    }
                                )
                            }
                        }
                    }
                    displayBatteryInfoIfPossible()
                } else {
                    errorBinding.errorMessageContainer.visibility = View.VISIBLE
                    binding.deviceView.visibility = View.GONE
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
                pairingBinding.pairMessage.announceForAccessibility(getString(R.string.pair_succeeded))
                mActivity?.runOnUiThread { refreshUI() }
            }

            override fun pairingFailed(error: String) {
                mActivity?.runOnUiThread {
                    with(pairingBinding) {
                        pairMessage.text = error
                        pairProgress.visibility = View.GONE
                        pairButton.visibility = View.VISIBLE
                        pairRequestButtons.visibility = View.GONE
                    }
                    refreshUI()
                }
            }

            override fun unpaired(device: Device) {
                mActivity?.runOnUiThread {
                    with(pairingBinding) {
                        pairMessage.setText(R.string.device_not_paired)
                        pairProgress.visibility = View.GONE
                        pairButton.visibility = View.VISIBLE
                        pairRequestButtons.visibility = View.GONE
                    }
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
