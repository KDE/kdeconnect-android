/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.BackgroundService.Companion.ForceRefreshConnections
import org.kde.kdeconnect.BackgroundService.Companion.instance
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.base.BaseFragment
import org.kde.kdeconnect.helpers.TrustedNetworkHelper.Companion.isTrustedNetwork
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.compose.screen.pairing.PairingScreen
import org.kde.kdeconnect.ui.compose.screen.pairing.PairingViewModel
import org.kde.kdeconnect_tp.R
import org.kde.kdeconnect_tp.databinding.DevicesListBinding
import androidx.core.content.edit

/**
 * The view that the user will see when there are no devices paired, or when you choose "add a new device" from the sidebar.
 */
class PairingFragment : BaseFragment<DevicesListBinding>() {

    private val viewModel by viewModels<PairingViewModel>()

    private var listRefreshCalledThisFrame = false

    private val menuProvider = object : MenuProvider {

        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.pairing, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            return when (menuItem.itemId) {
                R.id.menu_refresh -> {
                    viewModel.onRefresh()
                    true
                }

                R.id.menu_custom_device_list -> {
                    startActivity(Intent(mActivity, CustomDevicesActivity::class.java))
                    true
                }

                R.id.menu_trusted_networks -> {
                    startActivity(Intent(mActivity, TrustedNetworksActivity::class.java))
                    true
                }

                else -> false
            }
        }
    }

    override fun getActionBarTitle() = getString(R.string.pairing_title)

    override fun onInflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): DevicesListBinding = DevicesListBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mActivity?.addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)

        createComposeView()
    }

    fun hasRequestedPermission(permission: String): Boolean {
        return context
            ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.getBoolean(permission, false)
            ?: false
    }

    fun markPermissionRequested(permission: String) {
        context
            ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit {
                putBoolean(permission, true)
            }
    }

    private fun createComposeView() {
        binding.composeView.apply {
            setContent {
                KdeTheme(context) {
                    val state by viewModel.pairingUiState.collectAsStateWithLifecycle()

                    PairingScreen(
                        uiState = state,
                        onClick = { deviceId ->
                            viewModel.getDeviceById(deviceId)?.let { device ->
                                deviceClicked(device)
                            }
                        },
                        onWifiSettingsClick = {
                            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                        },
                        onNotificationSettingsClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val hasRequestedBefore = hasRequestedPermission(Manifest.permission.POST_NOTIFICATIONS)
                                val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), Manifest.permission.POST_NOTIFICATIONS)
                                if (hasRequestedBefore && !shouldShowRationale) {
                                    openAppDetailsSettings()
                                } else {
                                    markPermissionRequested(Manifest.permission.POST_NOTIFICATIONS)
                                    ActivityCompat.requestPermissions(
                                        requireActivity(),
                                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                                        MainActivity.RESULT_NOTIFICATIONS_ENABLED
                                    )
                                }
                            } else {
                                openAppDetailsSettings()
                            }
                        },
                        onLocalNetworkPermissionClick = @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN) {
                            val hasRequestedBefore = hasRequestedPermission(Manifest.permission.ACCESS_LOCAL_NETWORK)
                            val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), Manifest.permission.ACCESS_LOCAL_NETWORK)
                            if (hasRequestedBefore && !shouldShowRationale) {
                                openAppDetailsSettings()
                            } else {
                                markPermissionRequested(Manifest.permission.ACCESS_LOCAL_NETWORK)
                                ActivityCompat.requestPermissions(
                                    requireActivity(),
                                    arrayOf(Manifest.permission.ACCESS_LOCAL_NETWORK),
                                    MainActivity.RESULT_NOTIFICATIONS_ENABLED
                                )
                            }
                        },
                        onRefresh = { viewModel.onRefresh() }
                    )
                }
            }
        }
    }


    private val appSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updatePermissionsHeader()
        }

    private fun openAppDetailsSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", requireContext().packageName, null)
        )
        appSettingsLauncher.launch(intent)
    }

    private fun updateDeviceList() {
        if (!isAdded) {
            //Fragment is not attached to an activity. We will crash if we try to do anything here.
            return
        }

        if (listRefreshCalledThisFrame) {
            // This makes sure we don't try to call list.getFirstVisiblePosition()
            // twice per frame, because the second time the list hasn't been drawn
            // yet and it would always return 0.
            return
        }
        listRefreshCalledThisFrame = true

        //Check if we're on Wi-Fi/Local network. If we still see a device, don't do anything special
        val service = instance
        if (service == null) {
            updateConnectivityInfoHeader(isConnectedToNonCellularNetwork = true)
        } else {
            service.isConnectedToNonCellularNetwork.observe(
                viewLifecycleOwner,
                ::updateConnectivityInfoHeader
            )
        }

        updatePermissionsHeader()

        try {
            val allDevices = KdeConnect.getInstance().devices.values.filter {
                it.isReachable || it.isPaired
            }

            viewModel.updateDevices(devices = allDevices)
        } catch (_: IllegalStateException) {
            // Ignore: The activity was closed while we were trying to update it
        } finally {
            listRefreshCalledThisFrame = false
        }
    }

    private fun updateConnectivityInfoHeader(isConnectedToNonCellularNetwork: Boolean) {
        viewModel.updateConnectivity(
            isWifiAvailable = isConnectedToNonCellularNetwork,
            isTrustedNetwork = isTrustedNetwork(requireContext())
        )
    }

    private fun updatePermissionsHeader() {
        val hasNotificationsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PERMISSION_GRANTED

        val hasLocalNetworkPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN
                || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_LOCAL_NETWORK) == PERMISSION_GRANTED

        viewModel.updatePermissions(
            hasNotificationsPermission = hasNotificationsPermission,
            hasLocalNetworkPermission = hasLocalNetworkPermission,
        )
    }

    override fun onStart() {
        super.onStart()
        KdeConnect.getInstance().addDeviceListChangedCallback("PairingFragment") {
            mActivity?.runOnUiThread { this.updateDeviceList() }
        }
        ForceRefreshConnections(requireContext()) // force a network re-discover
        updateDeviceList()
    }

    override fun onStop() {
        KdeConnect.getInstance().removeDeviceListChangedCallback("PairingFragment")
        super.onStop()
    }

    fun deviceClicked(device: Device) {
        (mActivity as? MainActivity)?.onDeviceSelected(
            device.deviceId,
            !device.isPaired || !device.isReachable
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            RESULT_PAIRING_SUCCESFUL -> if (resultCode == 1) {
                val deviceId = data?.getStringExtra("deviceId")
                (mActivity as? MainActivity)?.onDeviceSelected(deviceId)
            }

            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    companion object {
        private const val RESULT_PAIRING_SUCCESFUL = Activity.RESULT_FIRST_USER

        private const val PREFS_NAME = "permission_prefs"
    }
}
