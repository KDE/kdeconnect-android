/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.UserInterface;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Helpers.TrustedNetworkHelper;
import org.kde.kdeconnect.KdeConnect;
import org.kde.kdeconnect.UserInterface.List.ListAdapter;
import org.kde.kdeconnect.UserInterface.List.PairingDeviceItem;
import org.kde.kdeconnect.UserInterface.List.SectionItem;
import org.kde.kdeconnect_tp.R;
import org.kde.kdeconnect_tp.databinding.DevicesListBinding;
import org.kde.kdeconnect_tp.databinding.PairingExplanationNotTrustedBinding;
import org.kde.kdeconnect_tp.databinding.PairingExplanationTextBinding;
import org.kde.kdeconnect_tp.databinding.PairingExplanationTextNoNotificationsBinding;
import org.kde.kdeconnect_tp.databinding.PairingExplanationTextNoWifiBinding;

import java.util.ArrayList;
import java.util.Collection;


/**
 * The view that the user will see when there are no devices paired, or when you choose "add a new device" from the sidebar.
 */

public class PairingFragment extends Fragment implements PairingDeviceItem.Callback {

    private static final int RESULT_PAIRING_SUCCESFUL = Activity.RESULT_FIRST_USER;

    private DevicesListBinding devicesListBinding;
    private PairingExplanationNotTrustedBinding pairingExplanationNotTrustedBinding;
    private PairingExplanationTextBinding pairingExplanationTextBinding;
    private PairingExplanationTextNoWifiBinding pairingExplanationTextNoWifiBinding;
    private PairingExplanationTextNoNotificationsBinding pairingExplanationTextNoNotificationsBinding;

    private MainActivity mActivity;

    private boolean listRefreshCalledThisFrame = false;

    private TextView headerText;
    private TextView noWifiHeader;
    private TextView noNotificationsHeader;
    private TextView notTrustedText;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mActivity.getSupportActionBar().setTitle(R.string.pairing_title);

        setHasOptionsMenu(true);

        devicesListBinding = DevicesListBinding.inflate(inflater, container, false);

        pairingExplanationNotTrustedBinding = PairingExplanationNotTrustedBinding.inflate(inflater);
        notTrustedText = pairingExplanationNotTrustedBinding.getRoot();
        notTrustedText.setOnClickListener(null);
        notTrustedText.setOnLongClickListener(null);

        pairingExplanationTextBinding = PairingExplanationTextBinding.inflate(inflater);
        headerText = pairingExplanationTextBinding.getRoot();
        headerText.setOnClickListener(null);
        headerText.setOnLongClickListener(null);

        pairingExplanationTextNoWifiBinding = PairingExplanationTextNoWifiBinding.inflate(inflater);
        noWifiHeader = pairingExplanationTextNoWifiBinding.getRoot();
        noWifiHeader.setOnClickListener(view -> startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));

        pairingExplanationTextNoNotificationsBinding = PairingExplanationTextNoNotificationsBinding.inflate(inflater);
        noNotificationsHeader = pairingExplanationTextNoNotificationsBinding.getRoot();
        noNotificationsHeader.setOnClickListener(view -> ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.POST_NOTIFICATIONS}, MainActivity.RESULT_NOTIFICATIONS_ENABLED));
        noNotificationsHeader.setOnLongClickListener(view -> {
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", requireContext().getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
            return true;
        });

        devicesListBinding.devicesList.addHeaderView(headerText);
        devicesListBinding.refreshListLayout.setOnRefreshListener(this::refreshDevicesAction);

        return devicesListBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Configure focus order for Accessibility, for touchpads, and for TV remotes
        // (allow focus of items in the device list)
        devicesListBinding.devicesList.setItemsCanFocus(true);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        devicesListBinding = null;
        pairingExplanationNotTrustedBinding = null;
        pairingExplanationTextBinding = null;
        pairingExplanationTextNoWifiBinding = null;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mActivity = ((MainActivity) getActivity());
    }

    private void refreshDevicesAction() {
        BackgroundService.ForceRefreshConnections(requireContext());

        devicesListBinding.refreshListLayout.setRefreshing(true);
        devicesListBinding.refreshListLayout.postDelayed(() -> {
            if (devicesListBinding != null) { // the view might be destroyed by now
                devicesListBinding.refreshListLayout.setRefreshing(false);
            }
        }, 1500);
    }

    private void updateDeviceList() {
        if (!isAdded()) {
            //Fragment is not attached to an activity. We will crash if we try to do anything here.
            return;
        }

        if (listRefreshCalledThisFrame) {
            // This makes sure we don't try to call list.getFirstVisiblePosition()
            // twice per frame, because the second time the list hasn't been drawn
            // yet and it would always return 0.
            return;
        }
        listRefreshCalledThisFrame = true;

        //Check if we're on Wi-Fi/Local network. If we still see a device, don't do anything special
        BackgroundService service = BackgroundService.getInstance();
        if (service == null) {
            updateConnectivityInfoHeader(true);
        } else {
            service.isConnectedToNonCellularNetwork().observe(this, this::updateConnectivityInfoHeader);
        }

        try {
            final ArrayList<ListAdapter.Item> items = new ArrayList<>();

            SectionItem connectedSection;
            Resources res = getResources();

            connectedSection = new SectionItem(res.getString(R.string.category_connected_devices));
            items.add(connectedSection);

            Collection<Device> devices = KdeConnect.getInstance().getDevices().values();
            for (Device device : devices) {
                if (device.isReachable() && device.isPaired()) {
                    items.add(new PairingDeviceItem(device, PairingFragment.this));
                    connectedSection.isEmpty = false;
                }
            }
            if (connectedSection.isEmpty) {
                items.remove(items.size() - 1); //Remove connected devices section if empty
            }

            SectionItem availableSection = new SectionItem(res.getString(R.string.category_not_paired_devices));
            items.add(availableSection);
            for (Device device : devices) {
                if (device.isReachable() && !device.isPaired()) {
                    items.add(new PairingDeviceItem(device, PairingFragment.this));
                    availableSection.isEmpty = false;
                }
            }
            if (availableSection.isEmpty && !connectedSection.isEmpty) {
                items.remove(items.size() - 1); //Remove remembered devices section if empty
            }

            SectionItem rememberedSection = new SectionItem(res.getString(R.string.category_remembered_devices));
            items.add(rememberedSection);
            for (Device device : devices) {
                if (!device.isReachable() && device.isPaired()) {
                    items.add(new PairingDeviceItem(device, PairingFragment.this));
                    rememberedSection.isEmpty = false;
                }
            }
            if (rememberedSection.isEmpty) {
                items.remove(items.size() - 1); //Remove remembered devices section if empty
            }

            //Store current scroll
            int index = devicesListBinding.devicesList.getFirstVisiblePosition();
            View v = devicesListBinding.devicesList.getChildAt(0);
            int top = (v == null) ? 0 : (v.getTop() - devicesListBinding.devicesList.getPaddingTop());

            devicesListBinding.devicesList.setAdapter(new ListAdapter(mActivity, items));

            //Restore scroll
            devicesListBinding.devicesList.setSelectionFromTop(index, top);
        } catch (IllegalStateException e) {
            //Ignore: The activity was closed while we were trying to update it
        } finally {
            listRefreshCalledThisFrame = false;
        }
    }

    void updateConnectivityInfoHeader(boolean isConnectedToNonCellularNetwork) {
        Collection<Device> devices = KdeConnect.getInstance().getDevices().values();
        boolean someDevicesReachable = false;
        for (Device device : devices) {
            if (device.isReachable()) {
                someDevicesReachable = true;
            }
        }

        boolean hasNotificationsPermission = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;

        devicesListBinding.devicesList.removeHeaderView(headerText);
        devicesListBinding.devicesList.removeHeaderView(noWifiHeader);
        devicesListBinding.devicesList.removeHeaderView(notTrustedText);
        devicesListBinding.devicesList.removeHeaderView(noNotificationsHeader);

        if (someDevicesReachable || isConnectedToNonCellularNetwork) {
            if (!hasNotificationsPermission) {
                devicesListBinding.devicesList.addHeaderView(noNotificationsHeader);
            } else if (TrustedNetworkHelper.isTrustedNetwork(getContext())) {
                devicesListBinding.devicesList.addHeaderView(headerText);
            } else {
                devicesListBinding.devicesList.addHeaderView(notTrustedText);
            }
        } else {
            devicesListBinding.devicesList.addHeaderView(noWifiHeader);
        }
    }
    @Override
    public void onStart() {
        super.onStart();
        KdeConnect.getInstance().addDeviceListChangedCallback("PairingFragment", () -> mActivity.runOnUiThread(this::updateDeviceList));
        BackgroundService.ForceRefreshConnections(requireContext()); // force a network re-discover
        updateDeviceList();
    }

    @Override
    public void onStop() {
        KdeConnect.getInstance().removeDeviceListChangedCallback("PairingFragment");
        super.onStop();
    }

    @Override
    public void pairingClicked(Device device) {
        mActivity.onDeviceSelected(device.getDeviceId(), !device.isPaired() || !device.isReachable());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case RESULT_PAIRING_SUCCESFUL:
                if (resultCode == 1) {
                    String deviceId = data.getStringExtra("deviceId");
                    mActivity.onDeviceSelected(deviceId);
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.pairing, menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_refresh) {
            refreshDevicesAction();
            return true;
        } else if (id == R.id.menu_custom_device_list) {
            startActivity(new Intent(mActivity, CustomDevicesActivity.class));
            return true;
        } else if (id == R.id.menu_trusted_networks) {
            startActivity(new Intent(mActivity, TrustedNetworksActivity.class));
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }


}
