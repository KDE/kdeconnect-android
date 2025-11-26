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
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StringRes
import androidx.annotation.UiThread
import androidx.appcompat.app.ActionBar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.MenuProvider
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
import org.kde.kdeconnect.base.BaseFragment
import org.kde.kdeconnect.extensions.setupBottomPadding
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
            val plugins: Collection<Plugin> = device.loadedPlugins.values
            for (p in plugins) {
                if (p.displayInContextMenu()) {
                    menu.add(p.actionName).setOnMenuItemClickListener {
                        mActivity?.let { p.startMainActivity(it) }
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
            addPairingCallback(pairingCallback)
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

    private val pluginsChangedListener = PluginsChangedListener { mActivity?.runOnUiThread { refreshUI() } }

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
                with (pairingBinding) {
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
                    val pluginsWithButtons = device.loadedPlugins.values.filter { it.displayAsButton(context) }
                    val pluginsNeedPermissions = device.pluginsWithoutPermissions.values.filter { device.isPluginEnabled(it.pluginKey) }
                    val pluginsNeedOptionalPermissions = device.pluginsWithoutOptionalPermissions.values.filter { device.isPluginEnabled(it.pluginKey) }
                    errorBinding.errorMessageContainer.visibility = View.GONE
                    binding.deviceView.visibility = View.VISIBLE
                    binding.deviceViewCompose.apply {
                        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                        setContent { KdeTheme(context) { PluginList(pluginsWithButtons, pluginsNeedPermissions, pluginsNeedOptionalPermissions) } }
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

    private val pairingCallback: PairingHandler.PairingCallback = object : PairingHandler.PairingCallback {
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

    @Composable
    @Preview
    fun PreviewCompose() {
        val plugins = listOf(MprisPlugin(), RunCommandPlugin(), PresenterPlugin())
        plugins.forEach { it.setContext(LocalContext.current, null) }
        PluginButtons(plugins, 2)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun PluginButton(plugin : Plugin, modifier: Modifier) {
        Card(
            shape = MaterialTheme.shapes.medium,
            modifier = modifier.semantics { role = Role.Button },
            onClick = { mActivity?.let { plugin.startMainActivity(it) } }
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
    fun PluginButtons(plugins: List<Plugin>, numColumns: Int) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            val pluginIter = plugins.iterator()
            while (pluginIter.hasNext()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(numColumns) {
                        if (pluginIter.hasNext()) {
                            PluginButton(
                                plugin = pluginIter.next(),
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
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .semantics { heading() }
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
    fun PluginList(
        pluginsWithButtons: List<Plugin>,
        pluginsNeedPermissions: List<Plugin>,
        pluginsNeedOptionalPermissions: List<Plugin>
    ) {
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
