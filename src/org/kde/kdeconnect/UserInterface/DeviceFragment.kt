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
import androidx.annotation.UiThread
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.Device.PluginsChangedListener
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.PairingHandler
import org.kde.kdeconnect.Plugins.BatteryPlugin.BatteryPlugin
import org.kde.kdeconnect.Plugins.MprisPlugin.MprisPlugin
import org.kde.kdeconnect.Plugins.Plugin
import org.kde.kdeconnect.Plugins.PresenterPlugin.PresenterPlugin
import org.kde.kdeconnect.Plugins.RunCommandPlugin.RunCommandPlugin
import org.kde.kdeconnect.UserInterface.compose.KdeTheme
import org.kde.kdeconnect_tp.R
import org.kde.kdeconnect_tp.databinding.ActivityDeviceBinding
import org.kde.kdeconnect_tp.databinding.ViewPairErrorBinding
import org.kde.kdeconnect_tp.databinding.ViewPairRequestBinding

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

    /**
     * Top-level ViewBinding for this fragment.
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

        requireErrorBinding().errorMessageContainer.setOnRefreshListener {
            this.refreshDevicesAction()
        }

        requirePairingBinding().pairVerification.text = SslHelper.getVerificationKey(SslHelper.certificate, device?.certificate)

        requirePairingBinding().pairButton.setOnClickListener {
            with(requirePairingBinding()) {
                pairButton.visibility = View.GONE
                pairMessage.text = getString(R.string.pair_requested)
                pairProgress.visibility = View.VISIBLE
            }
            device?.requestPairing()
            mActivity?.invalidateOptionsMenu()
        }
        requirePairingBinding().acceptButton.setOnClickListener {
            device?.apply {
                acceptPairing()
                requirePairingBinding().pairingButtons.visibility = View.GONE
            }
        }
        requirePairingBinding().rejectButton.setOnClickListener {
            device?.apply {
                // Remove listener so buttons don't show for an instant before changing the view
                removePluginsChangedListener(pluginsChangedListener)
                removePairingCallback(pairingCallback)
                cancelPairing()
            }
            mActivity?.onDeviceSelected(null)
        }
        setHasOptionsMenu(true)

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

    private fun refreshDevicesAction() {
        BackgroundService.ForceRefreshConnections(requireContext())
        requireErrorBinding().errorMessageContainer.isRefreshing = true
        requireErrorBinding().errorMessageContainer.postDelayed({
            errorBinding?.errorMessageContainer?.isRefreshing = false // check for null since the view might be destroyed by now
        }, 1500)
    }

    private val pluginsChangedListener = PluginsChangedListener { mActivity?.runOnUiThread { refreshUI() } }
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
                    p.startMainActivity(mActivity!!)
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
                mActivity?.onDeviceSelected(null)
                true
            }
        }
        if (device.isPairRequested) {
            menu.add(R.string.cancel_pairing).setOnMenuItemClickListener {
                device.cancelPairing()
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

    @UiThread
    private fun refreshUI() {
        val device = device ?: return
        //Once in-app, there is no point in keep displaying the notification if any
        device.hidePairingNotification()

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
            requireDeviceBinding().deviceView.visibility = View.GONE
        } else {
            if (device.isPaired) {
                requirePairingBinding().pairingButtons.visibility = View.GONE
                if (device.isReachable) {
                    requireErrorBinding().errorMessageContainer.visibility = View.GONE
                    requireDeviceBinding().deviceView.visibility = View.VISIBLE
                    requireDeviceBinding().deviceViewCompose.apply {
                        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                        setContent { KdeTheme(context) { PluginList(device) } }
                    }
                    displayBatteryInfoIfPossible()
                } else {
                    requireErrorBinding().errorMessageContainer.visibility = View.VISIBLE
                    requireDeviceBinding().deviceView.visibility = View.GONE
                }
            } else {
                requireErrorBinding().errorMessageContainer.visibility = View.GONE
                requireDeviceBinding().deviceView.visibility = View.GONE
                requirePairingBinding().pairingButtons.visibility = View.VISIBLE
            }
            mActivity?.invalidateOptionsMenu()
        }
    }

    private val pairingCallback: PairingHandler.PairingCallback = object : PairingHandler.PairingCallback {
        override fun incomingPairRequest() {
            mActivity?.runOnUiThread { refreshUI() }
        }

        override fun pairingSuccessful() {
            requirePairingBinding().pairMessage.announceForAccessibility(getString(R.string.pair_succeeded))
            mActivity?.runOnUiThread { refreshUI() }
        }

        override fun pairingFailed(error: String) {
            mActivity?.runOnUiThread {
                with(requirePairingBinding()) {
                    pairMessage.text = error
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

    @Composable
    @Preview
    fun PreviewCompose() {
        val plugins = listOf(MprisPlugin(), RunCommandPlugin(), PresenterPlugin())
        plugins.forEach { it.setContext(LocalContext.current, null) }
        PluginButtons(plugins.iterator(), 2)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun PluginButton(plugin : Plugin, modifier: Modifier) {
        Card(
            shape = MaterialTheme.shapes.medium,
            modifier = modifier.semantics { role = Role.Button },
            onClick = { plugin.startMainActivity(mActivity!!) }
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal=16.dp, vertical=10.dp)
            ) {
                Icon(
                    painter = painterResource(plugin.icon),
                    modifier = Modifier.padding(top = 12.dp),
                    contentDescription = null
                )
                Text(
                    text = plugin.actionName,
                    maxLines = 2,
                    minLines = 2,
                    fontSize = 18.sp,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    @Composable
    fun PluginButtons(plugins: Iterator<Plugin>, numColumns: Int) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            while (plugins.hasNext()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(numColumns) {
                        if (plugins.hasNext()) {
                            PluginButton(
                                plugin = plugins.next(),
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun PluginsWithoutPermissions(title : String, plugins: Collection<Plugin>, action : (plugin: Plugin) -> Unit) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).semantics { heading() }
        )
        plugins.forEach { plugin ->
            Text(
                text = plugin.displayName,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { action(plugin) }
                    .padding(start = 28.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
                    .semantics { role = Role.Button }
            )
        }
    }

    @Composable
    fun PluginList(device : Device) {

        val context = requireContext()

        val pluginsWithButtons = device.loadedPlugins.values.filter { it.displayAsButton(context) }.iterator()
        val pluginsNeedPermissions = device.pluginsWithoutPermissions.values.filter { device.isPluginEnabled(it.pluginKey) }
        val pluginsNeedOptionalPermissions = device.pluginsWithoutOptionalPermissions.values.filter { device.isPluginEnabled(it.pluginKey) }

        Surface {
            Column(modifier = Modifier.padding(top = 16.dp)) {

                val numColumns = resources.getInteger(R.integer.plugins_columns)
                PluginButtons(pluginsWithButtons, numColumns)

                Spacer(modifier = Modifier.padding(vertical=6.dp))

                if (pluginsNeedPermissions.isNotEmpty()) {
                    PluginsWithoutPermissions(
                        title = getString(R.string.plugins_need_permission),
                        plugins = pluginsNeedPermissions,
                        action = { it.permissionExplanationDialog.show(childFragmentManager,null) }
                    )
                    Spacer(modifier = Modifier.padding(vertical=2.dp))
                }

                if (pluginsNeedOptionalPermissions.isNotEmpty()) {
                    PluginsWithoutPermissions(
                        title = getString(R.string.plugins_need_optional_permission),
                        plugins = pluginsNeedOptionalPermissions,
                        action = { it.optionalPermissionExplanationDialog.show(childFragmentManager,null) }
                    )
                }
            }
        }
    }

}
