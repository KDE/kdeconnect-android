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
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.Fragment;
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


/**
 * The view that the user will see when there are no devices paired, or when you choose "add a new device" from the sidebar.
 */

public class PairingFragment extends Fragment implements PairingDeviceItem.Callback {

    private static final int RESULT_PAIRING_SUCCESFUL = Activity.RESULT_FIRST_USER;

    private View rootView;
    private MaterialActivity mActivity;

    private MenuItem menuProgress;

    boolean listRefreshCalledThisFrame = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        //Log.e("PairingFragmen", "OnCreateView");

        mActivity.getSupportActionBar().setTitle(R.string.pairing_title);



        setHasOptionsMenu(true);

        rootView = inflater.inflate(R.layout.activity_list, container, false);

        TextView text = new TextView(inflater.getContext());
        text.setText(getString(R.string.pairing_description));
        text.setPadding(0, (int) (16 * getResources().getDisplayMetrics().density), 0, (int) (12 * getResources().getDisplayMetrics().density));
        ((ListView) rootView).addHeaderView(text);

        return rootView;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mActivity = ((MaterialActivity) getActivity());
    }

    void updateComputerList() {
        BackgroundService.RunCommand(mActivity, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(final BackgroundService service) {
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        if (listRefreshCalledThisFrame) {
                            // This makes sure we don't try to call list.getFirstVisiblePosition()
                            // twice per frame, because the second time the list hasn't been drawn
                            // yet and it would always return 0.
                            return;
                        }
                        listRefreshCalledThisFrame = true;

                        try {
                            Collection<Device> devices = service.getDevices().values();
                            final ArrayList<ListAdapter.Item> items = new ArrayList<>();

                            SectionItem section;
                            Resources res = getResources();

                            section = new SectionItem(res.getString(R.string.category_not_paired_devices));
                            section.isSectionEmpty = true;
                            items.add(section);
                            for (Device device : devices) {
                                if (device.isReachable() && !device.isPaired()) {
                                    items.add(new PairingDeviceItem(device, PairingFragment.this));
                                    section.isSectionEmpty = false;
                                }
                            }

                            section = new SectionItem(res.getString(R.string.category_connected_devices));
                            section.isSectionEmpty = true;
                            items.add(section);
                            for (Device device : devices) {
                                if (device.isReachable() && device.isPaired()) {
                                    items.add(new PairingDeviceItem(device, PairingFragment.this));
                                    section.isSectionEmpty = false;
                                }
                            }
                            if (section.isSectionEmpty) {
                                items.remove(items.size() - 1); //Remove connected devices section if empty
                            }

                            section = new SectionItem(res.getString(R.string.category_remembered_devices));
                            section.isSectionEmpty = true;
                            items.add(section);
                            for (Device device : devices) {
                                if (!device.isReachable() && device.isPaired()) {
                                    items.add(new PairingDeviceItem(device, PairingFragment.this));
                                    section.isSectionEmpty = false;
                                }
                            }
                            if (section.isSectionEmpty) {
                                items.remove(items.size() - 1); //Remove remembered devices section if empty
                            }

                            final ListView list = (ListView) rootView.findViewById(R.id.listView1);

                            //Store current scroll
                            int index = list.getFirstVisiblePosition();
                            View v = list.getChildAt(0);
                            int top = (v == null) ? 0 : (v.getTop() - list.getPaddingTop());

                            list.setAdapter(new ListAdapter(mActivity, items));

                            //Restore scroll
                            list.setSelectionFromTop(index, top);
                        } catch (IllegalStateException e) {
                            e.printStackTrace();
                            //Ignore: The activity was closed while we were trying to update it
                        } finally {
                            listRefreshCalledThisFrame = false;
                        }
                    }
                });

            }
        });
    }


    @Override
    public void onStart() {
        super.onStart();
        BackgroundService.RunCommand(mActivity, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                service.addDeviceListChangedCallback("PairingFragment", new BackgroundService.DeviceListChangedCallback() {
                    @Override
                    public void onDeviceListChanged() {
                        updateComputerList();
                    }
                });
            }
        });
        updateComputerList();
    }

    @Override
    public void onStop() {
        super.onStop();
        BackgroundService.RunCommand(mActivity, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                service.removeDeviceListChangedCallback("PairingFragment");
            }
        });
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
        menuProgress = menu.findItem(R.id.menu_progress);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_refresh:
                updateComputerList();
                BackgroundService.RunCommand(mActivity, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        service.onNetworkChange();
                    }
                });
                menuProgress.setVisible(true);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try { Thread.sleep(1500); } catch (InterruptedException e) { }
                        mActivity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                menuProgress.setVisible(false);
                            }
                        });
                    }
                }).start();
                break;
            case R.id.menu_rename:
                mActivity.renameDevice();
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
