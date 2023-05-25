/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.UserInterface

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.Device.PairingCallback
import org.kde.kdeconnect.Device.PluginsChangedListener
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.Plugins.BatteryPlugin.BatteryPlugin
import org.kde.kdeconnect.Plugins.Plugin
import org.kde.kdeconnect.UserInterface.List.PluginAdapter
import org.kde.kdeconnect.UserInterface.List.PluginItem
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
    private val pluginListItems: ArrayList<PluginItem> = ArrayList()
    private val permissionListItems: ArrayList<PluginItem> = ArrayList()

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
    private var pairingBinding: ViewPairRequestBinding? = null
    private fun requirePairingBinding() = pairingBinding ?: throw IllegalStateException("binding is not set")

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
        pairingBinding = deviceBinding.pairRequest
        // ...and for when pairing doesn't (or can't) work
        errorBinding = deviceBinding.pairError

        device = KdeConnect.getInstance().getDevice(deviceId)

        requirePairingBinding().pairButton.setOnClickListener {
            with(requirePairingBinding()) {
                pairButton.visibility = View.GONE
                pairMessage.text = null
                pairVerification.visibility = View.VISIBLE
                pairVerification.text = SslHelper.getVerificationKey(SslHelper.certificate, device?.certificate)
                pairProgress.visibility = View.VISIBLE
            }
            device?.requestPairing()
        }
        requirePairingBinding().acceptButton.setOnClickListener {
            device?.apply {
                acceptPairing()
                requirePairingBinding().pairingButtons.visibility = View.GONE
            }
        }
        requirePairingBinding().rejectButton.setOnClickListener {
            device?.apply {
                //Remove listener so buttons don't show for a while before changing the view
                removePluginsChangedListener(pluginsChangedListener)
                removePairingCallback(pairingCallback)
                rejectPairing()
            }
            mActivity?.onDeviceSelected(null)
        }
        setHasOptionsMenu(true)

        requireDeviceBinding().pluginsList.layoutManager =
            GridLayoutManager(requireContext(), resources.getInteger(R.integer.plugins_columns))
        requireDeviceBinding().permissionsList.layoutManager = LinearLayoutManager(requireContext())

        device?.apply {
            mActivity?.supportActionBar?.title = name
            addPairingCallback(pairingCallback)
            addPluginsChangedListener(pluginsChangedListener)
        } ?: run { // device is null
            Log.e(TAG, "Trying to display a device fragment but the device is not present")
            mActivity?.onDeviceSelected(null)
        }

        refreshUI()

        return deviceBinding.root
    }

    private val pluginsChangedListener = PluginsChangedListener { refreshUI() }
    override fun onDestroyView() {
        device?.apply {
            removePluginsChangedListener(pluginsChangedListener)
            removePairingCallback(pairingCallback)
        }
        device = null
        pairingBinding = null
        errorBinding = null
        deviceBinding = null
        super.onDestroyView()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.clear()
        val device = device ?: return

        //Plugins button list
        val plugins: Collection<Plugin> = device.loadedPlugins.values
        for (p in plugins) {
            if (p.displayInContextMenu()) {
                menu.add(p.actionName).setOnMenuItemClickListener {
                    p.startMainActivity(mActivity)
                    true
                }
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
        with(requireView()) {
            isFocusableInTouchMode = true
            requestFocus()
            setOnKeyListener { _, keyCode, event ->
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
    }

    private fun refreshUI() {
        val device = device ?: return
        //Once in-app, there is no point in keep displaying the notification if any
        device.hidePairingNotification()
        mActivity?.runOnUiThread(object : Runnable {
            override fun run() {
                if (device.isPairRequestedByPeer) {
                    with (requirePairingBinding()) {
                        pairMessage.setText(R.string.pair_requested)
                        pairVerification.visibility = View.VISIBLE
                        pairVerification.text = SslHelper.getVerificationKey(SslHelper.certificate, device.certificate)
                        pairingButtons.visibility = View.VISIBLE
                        pairProgress.visibility = View.GONE
                        pairButton.visibility = View.GONE
                        pairRequestButtons.visibility = View.VISIBLE
                    }
                    with (requireDeviceBinding()) {
                        permissionsList.visibility = View.GONE
                        pluginsList.visibility = View.GONE
                    }
                } else {
                    val paired = device.isPaired
                    val reachable = device.isReachable
                    requirePairingBinding().pairingButtons.visibility = if (paired) View.GONE else View.VISIBLE
                    if (paired && !reachable) {
                        requireErrorBinding().errorMessageContainer.visibility = View.VISIBLE
                        requireErrorBinding().notReachableMessage.visibility = View.VISIBLE
                        requireDeviceBinding().permissionsList.visibility = View.GONE
                        requireDeviceBinding().pluginsList.visibility = View.GONE
                    } else if (paired) {
                        requireErrorBinding().errorMessageContainer.visibility = View.GONE
                        requireErrorBinding().notReachableMessage.visibility = View.GONE
                        requireDeviceBinding().permissionsList.visibility = View.VISIBLE
                        requireDeviceBinding().pluginsList.visibility = View.VISIBLE
                    } else {
                        requireDeviceBinding().permissionsList.visibility = View.GONE
                        requireDeviceBinding().pluginsList.visibility = View.GONE
                    }
                    try {
                        if (paired && reachable) {
                            //Plugins button list
                            val plugins: Collection<Plugin> = device.loadedPlugins.values

                            //TODO look for LinkedHashMap mention above
                            pluginListItems.clear()
                            permissionListItems.clear()

                            //Fill enabled plugins ArrayList
                            for (p in plugins) {
                                if (p.hasMainActivity(context) && !p.displayInContextMenu()) {
                                    pluginListItems.add(
                                        PluginItem(requireContext(), p, { p.startMainActivity(mActivity) })
                                    )
                                }
                            }

                            //Fill permissionListItems with permissions plugins
                            createPermissionsList(
                                device.pluginsWithoutPermissions,
                                R.string.plugins_need_permission
                            ) { p: Plugin ->
                                p.permissionExplanationDialog?.show(childFragmentManager, null)
                            }
                            createPermissionsList(
                                device.pluginsWithoutOptionalPermissions,
                                R.string.plugins_need_optional_permission
                            ) { p: Plugin ->
                                p.optionalPermissionExplanationDialog?.show(childFragmentManager, null)
                            }

                            requireDeviceBinding().permissionsList.adapter =
                                PluginAdapter(permissionListItems, R.layout.list_item_plugin_header)
                            requireDeviceBinding().pluginsList.adapter =
                                PluginAdapter(pluginListItems, R.layout.list_plugin_entry)

                            requireDeviceBinding().permissionsList.adapter?.notifyDataSetChanged()
                            requireDeviceBinding().pluginsList.adapter?.notifyDataSetChanged()

                            displayBatteryInfoIfPossible()
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
                with(requirePairingBinding()) {
                    pairMessage.text = error
                    pairVerification.text = null
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
                with(requirePairingBinding()) {
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
        @StringRes headerText: Int,
        action: (Plugin) -> Unit,
    ) {
        if (plugins.isEmpty()) return
        val device = device ?: return
        permissionListItems.add(
            PluginItem(
                context = requireContext(),
                header = requireContext().getString(headerText),
                textStyleRes = com.google.android.material.R.style.TextAppearance_Material3_BodyMedium,
            )
        )
        for (plugin in plugins.values) {
            if (device.isPluginEnabled(plugin.pluginKey)) {
                permissionListItems.add(
                    PluginItem(requireContext(), plugin, action, com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
                )
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
        val batteryPlugin = device?.loadedPlugins?.get(Plugin.getPluginKey(BatteryPlugin::class.java)) as BatteryPlugin?

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

    override fun onDetach() {
        super.onDetach()
        mActivity?.supportActionBar?.subtitle = null
    }
}
