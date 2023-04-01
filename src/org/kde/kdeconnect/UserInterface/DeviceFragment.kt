/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.UserInterface

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.Device.PairingCallback
import org.kde.kdeconnect.Device.PluginsChangedListener
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper
import org.kde.kdeconnect.Plugins.BatteryPlugin.BatteryPlugin
import org.kde.kdeconnect.Plugins.Plugin
import org.kde.kdeconnect.UserInterface.List.FailedPluginListItem
import org.kde.kdeconnect.UserInterface.List.ListAdapter
import org.kde.kdeconnect.UserInterface.List.PluginItem
import org.kde.kdeconnect.UserInterface.List.PluginListHeaderItem
import org.kde.kdeconnect_tp.R
import org.kde.kdeconnect_tp.databinding.ActivityDeviceBinding
import org.kde.kdeconnect_tp.databinding.ViewPairErrorBinding
import org.kde.kdeconnect_tp.databinding.ViewPairRequestBinding
import java.util.concurrent.ConcurrentHashMap

/**
 * Main view. Displays the current device and its plugins
 */
class DeviceFragment : Fragment() {
    val deviceId: String by lazy {
        arguments?.getString(ARG_DEVICE_ID)
            ?: throw RuntimeException("You must instantiate a new DeviceFragment using DeviceFragment.newInstance()")
    }
    private var device: Device? = null
    private val mActivity: MainActivity? by lazy { activity as MainActivity? }

    //TODO use LinkedHashMap and delete irrelevant records when plugins changed
    private val pluginListItems: ArrayList<ListAdapter.Item> = ArrayList()
    private val permissionListItems: ArrayList<ListAdapter.Item> = ArrayList()

    /**
     * Top-level ViewBinding for this fragment.
     *
     * Host for [.pluginListItems].
     */
    private var deviceBinding: ActivityDeviceBinding? = null
    private fun requireDeviceBinding() = deviceBinding ?: throw IllegalStateException("deviceBinding is not set")

    /**
     * Not-yet-paired ViewBinding.
     *
     * Used to start and retry pairing.
     */
    private var binding: ViewPairRequestBinding? = null
    private fun requireBinding() = binding ?: throw IllegalStateException("binding is not set")

    /**
     * Cannot-communicate ViewBinding.
     *
     * Used when the remote device is unreachable.
     */
    private var errorBinding: ViewPairErrorBinding? = null
    private fun requireErrorBinding() = errorBinding ?: throw IllegalStateException("errorBinding is not set")

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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        deviceBinding = ActivityDeviceBinding.inflate(inflater, container, false)
        val deviceBinding = deviceBinding ?: return null

        // Inner binding for the layout shown when we're not paired yet...
        binding = deviceBinding.pairRequest
        // ...and for when pairing doesn't (or can't) work
        errorBinding = deviceBinding.pairError

        BackgroundService.RunCommand(mActivity) {
            device = it.getDevice(deviceId)
        }

        requireBinding().pairButton.setOnClickListener {
            requireBinding().pairButton.visibility = View.GONE
            requireBinding().pairMessage.text = null
            requireBinding().pairVerification.visibility = View.VISIBLE
            requireBinding().pairVerification.text = SslHelper.getVerificationKey(SslHelper.certificate, device?.certificate)
            requireBinding().pairProgress.visibility = View.VISIBLE
            device?.requestPairing()
        }
        requireBinding().acceptButton.setOnClickListener {
            device?.apply {
                acceptPairing()
                requireBinding().pairingButtons.visibility = View.GONE
            }
        }
        requireBinding().rejectButton.setOnClickListener {
            device?.apply {
                //Remove listener so buttons don't show for a while before changing the view
                removePluginsChangedListener(pluginsChangedListener)
                removePairingCallback(pairingCallback)
                rejectPairing()
            }
            mActivity?.onDeviceSelected(null)
        }
        setHasOptionsMenu(true)
        BackgroundService.RunCommand(mActivity) { service: BackgroundService ->
            device = service.getDevice(deviceId) ?: let {
                Log.e(TAG, "Trying to display a device fragment but the device is not present")
                mActivity?.onDeviceSelected(null)
                return@RunCommand
            }
            mActivity?.supportActionBar?.title = device?.name
            device?.addPairingCallback(pairingCallback)
            device?.addPluginsChangedListener(pluginsChangedListener)
            refreshUI()
        }

        return deviceBinding.root
    }

    private val pluginsChangedListener = PluginsChangedListener { refreshUI() }
    override fun onDestroyView() {
        BackgroundService.RunCommand(mActivity) { service: BackgroundService ->
            val device = service.getDevice(deviceId) ?: return@RunCommand
            device.removePluginsChangedListener(pluginsChangedListener)
            device.removePairingCallback(pairingCallback)
        }
        super.onDestroyView()
        binding = null
        errorBinding = null
        deviceBinding = null
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.clear()
        val device = device ?: return

        //Plugins button list
        val plugins: Collection<Plugin> = device.loadedPlugins.values
        for (p in plugins) {
            if (!p.displayInContextMenu()) {
                continue
            }
            menu.add(p.actionName).setOnMenuItemClickListener {
                p.startMainActivity(mActivity)
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
            if (device.certificate == null) {
                builder.setMessage(R.string.encryption_info_msg_no_ssl)
            } else {
                builder.setMessage(
                    "${
                        requireContext().resources.getString(R.string.my_device_fingerprint)
                    } \n ${
                        SslHelper.getCertificateHash(SslHelper.certificate)
                    } \n\n ${
                        requireContext().resources.getString(R.string.remote_device_fingerprint)
                    } \n ${
                        SslHelper.getCertificateHash(device.certificate)
                    }"
                )
            }
            menu.add(R.string.encryption_info_title).setOnMenuItemClickListener {
                builder.show()
                true
            }
        }
        if (device.isPaired) {
            menu.add(R.string.device_menu_unpair).setOnMenuItemClickListener {
                //Remove listener so buttons don't show for a while before changing the view
                device.removePluginsChangedListener(pluginsChangedListener)
                device.removePairingCallback(pairingCallback)
                device.unpair()
                mActivity?.onDeviceSelected(null)
                true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requireView().isFocusableInTouchMode = true
        requireView().requestFocus()
        requireView().setOnKeyListener { view, keyCode, event ->
            if (event.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
                val fromDeviceList = requireArguments().getBoolean(ARG_FROM_DEVICE_LIST, false)
                // Handle back button, so we go to the list of devices in case we came from there
                if (fromDeviceList) {
                    mActivity?.onDeviceSelected(null)
                    return@setOnKeyListener true
                }
            }
            false
        }
    }

    private fun refreshUI() {
        val device = device ?: return
        //Once in-app, there is no point in keep displaying the notification if any
        device.hidePairingNotification()
        mActivity?.runOnUiThread(object : Runnable {
            override fun run() {
                if (device.isPairRequestedByPeer) {
                    requireBinding().pairMessage.setText(R.string.pair_requested)
                    requireBinding().pairVerification.visibility = View.VISIBLE
                    requireBinding().pairVerification.text =
                        SslHelper.getVerificationKey(SslHelper.certificate, device.certificate)
                    requireBinding().pairingButtons.visibility = View.VISIBLE
                    requireBinding().pairProgress.visibility = View.GONE
                    requireBinding().pairButton.visibility = View.GONE
                    requireBinding().pairRequestButtons.visibility = View.VISIBLE
                } else {
                    val paired = device.isPaired
                    val reachable = device.isReachable
                    requireBinding().pairingButtons.visibility = if (paired) View.GONE else View.VISIBLE
                    if (paired && !reachable) {
                        requireErrorBinding().errorMessageContainer.visibility = View.VISIBLE
                        requireErrorBinding().notReachableMessage.visibility = View.VISIBLE
                    } else {
                        requireErrorBinding().errorMessageContainer.visibility = View.GONE
                        requireErrorBinding().notReachableMessage.visibility = View.GONE
                    }
                    try {
                        if (paired && reachable) {
                            //Plugins button list
                            val plugins: Collection<Plugin> = device.loadedPlugins.values
                            pluginListItems.clear()
                            permissionListItems.clear()
                            for (p in plugins) {
                                if (!p.hasMainActivity(context) || p.displayInContextMenu()) continue
                                pluginListItems.add(PluginItem(p) { p.startMainActivity(mActivity) })
                            }
                            createPermissionsList(
                                device.pluginsWithoutPermissions,
                                R.string.plugins_need_permission
                            ) { plugin: Plugin ->
                                val dialog = plugin.permissionExplanationDialog
                                dialog?.show(childFragmentManager, null)
                            }
                            createPermissionsList(
                                device.pluginsWithoutOptionalPermissions,
                                R.string.plugins_need_optional_permission
                            ) { plugin: Plugin ->
                                val dialog: DialogFragment? = plugin.optionalPermissionExplanationDialog
                                dialog?.show(childFragmentManager, null)
                            }

                            displayBatteryInfoIfPossible()
                        }
                        requireDeviceBinding().pluginsList.adapter = ListAdapter(mActivity, pluginListItems)
                        //don't do unnecessary work when all permissions granted and remove view for landscape orientation
                        if (permissionListItems.isEmpty()) {
                            requireDeviceBinding().buttonsList.visibility = View.GONE
                        } else {
                            requireDeviceBinding().buttonsList.adapter = ListAdapter(mActivity, permissionListItems)
                            requireDeviceBinding().buttonsList.visibility = View.VISIBLE
                        }
                        mActivity?.invalidateOptionsMenu()
                    } catch (e: IllegalStateException) {
                        //Ignore: The activity was closed while we were trying to update it
                    } catch (e: ConcurrentModificationException) {
                        Log.e(TAG, "ConcurrentModificationException")
                        this.run() //Try again
                    }
                }
            }
        })
    }

    private val pairingCallback: PairingCallback = object : PairingCallback {
        override fun incomingRequest() {
            refreshUI()
        }

        override fun pairingSuccessful() {
            refreshUI()
        }

        override fun pairingFailed(error: String) {
            mActivity?.runOnUiThread {
                with(requireBinding()) {
                    pairMessage.text = error
                    pairVerification.text = ""
                    pairVerification.visibility = View.GONE
                    pairProgress.visibility = View.GONE
                    pairButton.visibility = View.VISIBLE
                    pairRequestButtons.visibility = View.GONE
                }
                refreshUI()
            }
        }

        override fun unpaired() {
            mActivity?.runOnUiThread {
                with(requireBinding()) {
                    pairMessage.setText(R.string.device_not_paired)
                    pairVerification.visibility = View.GONE
                    pairProgress.visibility = View.GONE
                    pairButton.visibility = View.VISIBLE
                    pairRequestButtons.visibility = View.GONE
                }
                refreshUI()
            }
        }
    }

    private fun createPermissionsList(
        plugins: ConcurrentHashMap<String, Plugin>,
        headerText: Int,
        action: FailedPluginListItem.Action
    ) {
        if (plugins.isEmpty()) return
        val device = device ?: return
        permissionListItems.add(PluginListHeaderItem(headerText))
        for (plugin in plugins.values) {
            if (!device.isPluginEnabled(plugin.pluginKey)) {
                continue
            }
            permissionListItems.add(FailedPluginListItem(plugin, action))
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
        val batteryPlugin = device?.loadedPlugins?.get(Plugin.getPluginKey(BatteryPlugin::class.java)) as BatteryPlugin?

        val info = batteryPlugin?.remoteBatteryInfo
        if (info != null) {

            @StringRes
            val resId: Int = if (info.isCharging) {
                R.string.battery_status_charging_format
            } else if (BatteryPlugin.isLowBattery(info)) {
                R.string.battery_status_low_format
            } else {
                R.string.battery_status_format
            }

            mActivity?.supportActionBar?.subtitle = mActivity?.getString(resId, info.currentCharge)
        } else {
            mActivity?.supportActionBar?.subtitle = null
        }
    }

}
