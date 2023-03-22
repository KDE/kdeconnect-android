/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.UserInterface;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
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
import androidx.fragment.app.Fragment;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Helpers.TrustedNetworkHelper;
import org.kde.kdeconnect.UserInterface.List.ListAdapter;
import org.kde.kdeconnect.UserInterface.List.PairingDeviceItem;
import org.kde.kdeconnect.UserInterface.List.SectionItem;
import org.kde.kdeconnect_tp.R;
import org.kde.kdeconnect_tp.databinding.DevicesListBinding;
import org.kde.kdeconnect_tp.databinding.PairingExplanationNotTrustedBinding;
import org.kde.kdeconnect_tp.databinding.PairingExplanationTextBinding;
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

    private MainActivity mActivity;

    private boolean listRefreshCalledThisFrame = false;

    private TextView headerText;
    private TextView noWifiHeader;
    private TextView notTrustedText;
    private boolean isConnectedToNonCellularNetwork = true;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mActivity.getSupportActionBar().setTitle(R.string.pairing_title);

        setHasOptionsMenu(true);

        devicesListBinding = DevicesListBinding.inflate(inflater, container, false);
        pairingExplanationNotTrustedBinding = PairingExplanationNotTrustedBinding.inflate(inflater);
        pairingExplanationTextBinding = PairingExplanationTextBinding.inflate(inflater);
        pairingExplanationTextNoWifiBinding = PairingExplanationTextNoWifiBinding.inflate(inflater);

        devicesListBinding.refreshListLayout.setOnRefreshListener(this::updateDeviceListAction);

        notTrustedText = pairingExplanationNotTrustedBinding.getRoot();
        notTrustedText.setOnClickListener(null);
        notTrustedText.setOnLongClickListener(null);

        headerText = pairingExplanationTextBinding.getRoot();
        headerText.setOnClickListener(null);
        headerText.setOnLongClickListener(null);

        noWifiHeader = pairingExplanationTextNoWifiBinding.getRoot();
        noWifiHeader.setOnClickListener(view -> startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        devicesListBinding.devicesList.addHeaderView(headerText);

        return devicesListBinding.getRoot();
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

    private void updateDeviceListAction() {
        updateDeviceList();
        BackgroundService.RunCommand(mActivity, BackgroundService::onNetworkChange);
        devicesListBinding.refreshListLayout.setRefreshing(true);

        devicesListBinding.refreshListLayout.postDelayed(() -> {
            // the view might be destroyed by now
            if (devicesListBinding == null) {
                return;
            }

            devicesListBinding.refreshListLayout.setRefreshing(false);
        }, 1500);
    }

    private void updateDeviceList() {
        BackgroundService.RunCommand(mActivity, service -> mActivity.runOnUiThread(() -> {

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

            Collection<Device> devices = service.getDevices().values();
            boolean someDevicesReachable = false;
            for (Device device : devices) {
                if (device.isReachable()) {
                    someDevicesReachable = true;
                }
            }

            devicesListBinding.devicesList.removeHeaderView(headerText);
            devicesListBinding.devicesList.removeHeaderView(noWifiHeader);
            devicesListBinding.devicesList.removeHeaderView(notTrustedText);

            //Check if we're on Wi-Fi/Local network. If we still see a device, don't do anything special
            if (someDevicesReachable || isConnectedToNonCellularNetwork) {
                if (TrustedNetworkHelper.isTrustedNetwork(getContext())) {
                    devicesListBinding.devicesList.addHeaderView(headerText);
                } else {
                    devicesListBinding.devicesList.addHeaderView(notTrustedText);
                }
            } else {
                devicesListBinding.devicesList.addHeaderView(noWifiHeader);
            }

            try {
                final ArrayList<ListAdapter.Item> items = new ArrayList<>();

                SectionItem connectedSection;
                Resources res = getResources();

                connectedSection = new SectionItem(res.getString(R.string.category_connected_devices));
                items.add(connectedSection);
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

        }));
    }

    @Override
    public void onStart() {
        super.onStart();
        devicesListBinding.refreshListLayout.setEnabled(true);
        BackgroundService.RunCommand(mActivity, service -> service.addDeviceListChangedCallback("PairingFragment", newIsConnectedToNonCellularNetwork -> {
            isConnectedToNonCellularNetwork = newIsConnectedToNonCellularNetwork;
            updateDeviceList();
        }));
        updateDeviceList();
    }

    @Override
    public void onStop() {
        super.onStop();
        devicesListBinding.refreshListLayout.setEnabled(false);
        BackgroundService.RunCommand(mActivity, service -> service.removeDeviceListChangedCallback("PairingFragment"));
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
            updateDeviceListAction();
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
