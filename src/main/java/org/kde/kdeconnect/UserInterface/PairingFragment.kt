/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.UserInterface

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import org.kde.kdeconnect.BackgroundService.Companion.ForceRefreshConnections
import org.kde.kdeconnect.BackgroundService.Companion.instance
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.Helpers.TrustedNetworkHelper.Companion.isTrustedNetwork
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.UserInterface.List.ListAdapter
import org.kde.kdeconnect.UserInterface.List.PairingDeviceItem
import org.kde.kdeconnect.UserInterface.List.SectionItem
import org.kde.kdeconnect.base.BaseFragment
import org.kde.kdeconnect.extensions.setupBottomPadding
import org.kde.kdeconnect_tp.R
import org.kde.kdeconnect_tp.databinding.DevicesListBinding
import org.kde.kdeconnect_tp.databinding.PairingExplanationDuplicateNamesBinding
import org.kde.kdeconnect_tp.databinding.PairingExplanationNotTrustedBinding
import org.kde.kdeconnect_tp.databinding.PairingExplanationTextBinding
import org.kde.kdeconnect_tp.databinding.PairingExplanationTextNoNotificationsBinding
import org.kde.kdeconnect_tp.databinding.PairingExplanationTextNoWifiBinding

/**
 * The view that the user will see when there are no devices paired, or when you choose "add a new device" from the sidebar.
 */
class PairingFragment : BaseFragment<DevicesListBinding>() {

    private var _textBinding: PairingExplanationTextBinding? = null
    private var _duplicateNamesBinding: PairingExplanationDuplicateNamesBinding? = null
    private var _textNoWifiBinding: PairingExplanationTextNoWifiBinding? = null
    private var _textNoNotificationsBinding: PairingExplanationTextNoNotificationsBinding? = null
    private var _textNotTrustedBinding: PairingExplanationNotTrustedBinding? = null

    private val headerText: TextView get() = _textBinding!!.root
    private val noWifiHeader: TextView get() = _textNoWifiBinding!!.root
    private val duplicateNamesHeader: TextView get() = _duplicateNamesBinding!!.root
    private val noNotificationsHeader: TextView get() = _textNoNotificationsBinding!!.root
    private val notTrustedText: TextView get() = _textNotTrustedBinding!!.root

    private var listRefreshCalledThisFrame = false

    private val menuProvider = object : MenuProvider {

        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.pairing, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            return when (menuItem.itemId) {
                R.id.menu_refresh -> {
                    refreshDevicesAction()
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
    ): DevicesListBinding {
        _textBinding = PairingExplanationTextBinding.inflate(inflater)
        _duplicateNamesBinding = PairingExplanationDuplicateNamesBinding.inflate(inflater)
        _textNoWifiBinding = PairingExplanationTextNoWifiBinding.inflate(inflater)
        _textNoNotificationsBinding = PairingExplanationTextNoNotificationsBinding.inflate(inflater)
        _textNotTrustedBinding = PairingExplanationNotTrustedBinding.inflate(inflater)
        return DevicesListBinding.inflate(inflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Configure focus order for Accessibility, for touchpads, and for TV remotes
        // (allow focus of items in the device list)
        binding.devicesList.itemsCanFocus = true
        binding.devicesList.setupBottomPadding()

        mActivity?.addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)

        notTrustedText.setOnClickListener(null)
        notTrustedText.setOnLongClickListener(null)

        headerText.setOnClickListener(null)
        headerText.setOnLongClickListener(null)


        noWifiHeader.setOnClickListener {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }

        noNotificationsHeader.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    MainActivity.RESULT_NOTIFICATIONS_ENABLED
                )
            }
        }
        noNotificationsHeader.setOnLongClickListener {
            val intent = Intent()
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", requireContext().packageName, null)
            intent.setData(uri)
            startActivity(intent)
            true
        }

        binding.devicesList.addHeaderView(headerText)
        binding.refreshListLayout.setOnRefreshListener { this.refreshDevicesAction() }
    }

    override fun onDestroyView() {
        binding.devicesList.adapter = null
        _textBinding = null
        _textNoWifiBinding = null
        _textNoNotificationsBinding = null
        _textNotTrustedBinding = null
        _duplicateNamesBinding = null
        super.onDestroyView()
    }


    private fun refreshDevicesAction() {
        ForceRefreshConnections(requireContext())
        binding.refreshListLayout.isRefreshing = true
        binding.refreshListLayout.postDelayed({
            if (isResumed && !isDetached) { // the view might be destroyed by now
                binding.refreshListLayout.isRefreshing = false
            }
        }, 1500)
    }

    override fun onPause() {
        binding.refreshListLayout.isRefreshing = false
        super.onPause()
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

        binding.devicesList.removeHeaderView(duplicateNamesHeader)

        //Check if we're on Wi-Fi/Local network. If we still see a device, don't do anything special
        val service = instance
        if (service == null) {
            updateConnectivityInfoHeader(true)
        } else {
            service.isConnectedToNonCellularNetwork.observe(viewLifecycleOwner, ::updateConnectivityInfoHeader)
        }

        try {
            val items = ArrayList<ListAdapter.Item>()

            val connectedSection: SectionItem
            val res = resources

            val allDevices = KdeConnect.getInstance().devices.values.filter {
                // Since we don't delete unpaired devices after they disconnect, we need to filter them out here
                it.isReachable || it.isPaired
            }

            val seenNames = hashSetOf<String>()
            for (device in allDevices) {
                if (seenNames.contains(device.name)) {
                    binding.devicesList.addHeaderView(duplicateNamesHeader)
                    Log.w("PairingFragment", "Duplicate device name detected: ${device.name}")
                    Log.w("PairingFragment", "Devices:" + allDevices.toList().toString())
                    break
                }
                seenNames.add(device.name)
            }

            connectedSection = SectionItem(res.getString(R.string.category_connected_devices))
            items.add(connectedSection)

            for (device in allDevices) {
                if (device.isReachable && device.isPaired) {
                    items.add(PairingDeviceItem(device, ::deviceClicked))
                    connectedSection.isEmpty = false
                }
            }
            if (connectedSection.isEmpty) {
                items.removeAt(items.size - 1) //Remove connected devices section if empty
            }

            val availableSection = SectionItem(res.getString(R.string.category_not_paired_devices))
            items.add(availableSection)
            for (device in allDevices) {
                if (device.isReachable && !device.isPaired) {
                    items.add(PairingDeviceItem(device, ::deviceClicked))
                    availableSection.isEmpty = false
                }
            }
            if (availableSection.isEmpty && !connectedSection.isEmpty) {
                items.removeAt(items.size - 1) //Remove remembered devices section if empty
            }

            val rememberedSection = SectionItem(res.getString(R.string.category_remembered_devices))
            items.add(rememberedSection)
            for (device in allDevices) {
                if (!device.isReachable && device.isPaired) {
                    items.add(PairingDeviceItem(device, ::deviceClicked))
                    rememberedSection.isEmpty = false
                }
            }
            if (rememberedSection.isEmpty) {
                items.removeAt(items.size - 1) //Remove remembered devices section if empty
            }

            //Store current scroll
            val index = binding.devicesList.firstVisiblePosition
            val v = binding.devicesList.getChildAt(0)
            val top = if ((v == null)) 0 else (v.top - binding.devicesList.paddingTop)

            binding.devicesList.adapter = ListAdapter(requireContext(), items)

            //Restore scroll
            binding.devicesList.setSelectionFromTop(index, top)
        } catch (e: IllegalStateException) {
            //Ignore: The activity was closed while we were trying to update it
        } finally {
            listRefreshCalledThisFrame = false
        }
    }

    private fun updateConnectivityInfoHeader(isConnectedToNonCellularNetwork: Boolean) {
        val devices: Collection<Device> = KdeConnect.getInstance().devices.values
        var someDevicesReachable = false
        for (device in devices) {
            if (device.isReachable) {
                someDevicesReachable = true
            }
        }

        val hasNotificationsPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        binding.devicesList.removeHeaderView(headerText)
        binding.devicesList.removeHeaderView(noWifiHeader)
        binding.devicesList.removeHeaderView(notTrustedText)
        binding.devicesList.removeHeaderView(noNotificationsHeader)

        if (someDevicesReachable || isConnectedToNonCellularNetwork) {
            if (!hasNotificationsPermission) {
                binding.devicesList.addHeaderView(noNotificationsHeader)
            } else if (isTrustedNetwork(requireContext())) {
                binding.devicesList.addHeaderView(headerText)
            } else {
                binding.devicesList.addHeaderView(notTrustedText)
            }
        } else {
            binding.devicesList.addHeaderView(noWifiHeader)
        }
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
        (mActivity as? MainActivity)?.onDeviceSelected(device.deviceId, !device.isPaired || !device.isReachable)
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
    }
}
