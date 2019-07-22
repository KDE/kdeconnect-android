/*
 * Copyright 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License or (at your option) version 3 or any later version
 * accepted by the membership of KDE e.V. (or its successor approved
 * by the membership of KDE e.V.), which shall act as a proxy
 * defined in Section 14 of version 3 of the license.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kde.kdeconnect.UserInterface;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.UserInterface.List.ListAdapter;
import org.kde.kdeconnect.UserInterface.List.PairingDeviceItem;
import org.kde.kdeconnect.UserInterface.List.SectionItem;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;
import java.util.Collection;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;


/**
 * The view that the user will see when there are no devices paired, or when you choose "add a new device" from the sidebar.
 */

public class PairingFragment extends Fragment implements PairingDeviceItem.Callback {

    private static final int RESULT_PAIRING_SUCCESFUL = Activity.RESULT_FIRST_USER;

    private View rootView;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private MainActivity mActivity;

    private boolean listRefreshCalledThisFrame = false;

    private TextView headerText;
    private TextView noWifiHeader;
    private Object networkChangeListener;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        //Log.e("PairingFragmen", "OnCreateView");

        mActivity.getSupportActionBar().setTitle(R.string.pairing_title);


        setHasOptionsMenu(true);

        rootView = inflater.inflate(R.layout.devices_list, container, false);
        View listRootView = rootView.findViewById(R.id.devices_list);
        mSwipeRefreshLayout = rootView.findViewById(R.id.refresh_list_layout);
        mSwipeRefreshLayout.setOnRefreshListener(
                this::updateComputerListAction
        );
        headerText = (TextView) inflater.inflate(R.layout.pairing_explanation_text, null);
        headerText.setOnClickListener(null);
        headerText.setOnLongClickListener(null);
        noWifiHeader = (TextView) inflater.inflate(R.layout.pairing_explanation_text_no_wifi, null);
        noWifiHeader.setOnClickListener(view -> {
            startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
        });
        ((ListView) listRootView).addHeaderView(headerText);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            networkChangeListener = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    updateDeviceList();
                }

                @Override
                public void onLost(Network network) {
                    updateDeviceList();
                }

                @Override
                public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
                    updateDeviceList();
                }
            };
            ConnectivityManager connManager = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            connManager.registerNetworkCallback(new NetworkRequest.Builder().build(), (ConnectivityManager.NetworkCallback) networkChangeListener);
        }

        return rootView;
    }

    @Override
    public void onDestroyView() {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ConnectivityManager connManager = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            connManager.unregisterNetworkCallback((ConnectivityManager.NetworkCallback) networkChangeListener);
        }

        super.onDestroyView();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mActivity = ((MainActivity) getActivity());
    }

    private void updateComputerListAction() {
        updateDeviceList();
        BackgroundService.RunCommand(mActivity, BackgroundService::onNetworkChange);
        mSwipeRefreshLayout.setRefreshing(true);
        new Thread(() -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {
            }
            mActivity.runOnUiThread(() -> mSwipeRefreshLayout.setRefreshing(false));
        }).start();
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

            ((ListView) rootView.findViewById(R.id.devices_list)).removeHeaderView(headerText);
            ((ListView) rootView.findViewById(R.id.devices_list)).removeHeaderView(noWifiHeader);

            ConnectivityManager connManager = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo wifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            //Check if we're on Wi-Fi. If we still see a device, don't do anything special
            if (someDevicesReachable || wifi.isConnected()) {
                ((ListView) rootView.findViewById(R.id.devices_list)).addHeaderView(headerText);
            } else {
                ((ListView) rootView.findViewById(R.id.devices_list)).addHeaderView(noWifiHeader);
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

                final ListView list = rootView.findViewById(R.id.devices_list);

                //Store current scroll
                int index = list.getFirstVisiblePosition();
                View v = list.getChildAt(0);
                int top = (v == null) ? 0 : (v.getTop() - list.getPaddingTop());

                list.setAdapter(new ListAdapter(mActivity, items));

                //Restore scroll
                list.setSelectionFromTop(index, top);
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
        BackgroundService.RunCommand(mActivity, service -> service.addDeviceListChangedCallback("PairingFragment", this::updateDeviceList));
        updateDeviceList();
    }

    @Override
    public void onStop() {
        super.onStop();
        mSwipeRefreshLayout.setEnabled(false);
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
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.pairing, menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_refresh:
                updateComputerListAction();
                break;
            case R.id.menu_custom_device_list:
                startActivity(new Intent(mActivity, CustomDevicesActivity.class));
                break;
            default:
                break;
        }
        return true;
    }


}
