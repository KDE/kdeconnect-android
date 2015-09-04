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

package org.kde.kdeconnect.NewUserInterface;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.NewUserInterface.List.PluginItem;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.UserInterface.List.ButtonItem;
import org.kde.kdeconnect.UserInterface.List.CustomItem;
import org.kde.kdeconnect.UserInterface.List.ListAdapter;
import org.kde.kdeconnect.UserInterface.List.SmallEntryItem;
import org.kde.kdeconnect.UserInterface.List.TextItem;
import org.kde.kdeconnect.UserInterface.SettingsActivity;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;


/**
 * Main view. Displays the current device and its plugins
 */
public class DeviceFragment extends Fragment {

    private static final String ARG_DEVICE_ID = "deviceId";
    private View rootView;
    static private String mDeviceId; //Static because if we get here by using the back button in the action bar, the extra deviceId will not be set.
    private Device device;

    private TextView errorHeader;

    private MaterialActivity mActivity;

    public DeviceFragment() { }

    public DeviceFragment(String deviceId) {
        Bundle args = new Bundle();
        args.putString(ARG_DEVICE_ID, deviceId);
        this.setArguments(args);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mActivity = ((MaterialActivity) getActivity());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        rootView = inflater.inflate(R.layout.activity_device, container, false);

        String deviceId = getArguments().getString(ARG_DEVICE_ID);
        if (deviceId != null) {
            mDeviceId = deviceId;
        }

        setHasOptionsMenu(true);

        Log.e("DeviceFragment", "device: " + deviceId);

        BackgroundService.RunCommand(mActivity, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                device = service.getDevice(mDeviceId);
                if (device == null) {
                    Log.e("DeviceFragment", "Trying to display a device fragment but the device is not present");
                    mActivity.getSupportActionBar().setTitle(R.string.error_not_reachable);
                    return;
                }

                if (!device.isPaired()) {
                    Log.e("DeviceFragment", "This device is no longer paired!");
                    mActivity.onDeviceSelected(null);
                }

                mActivity.getSupportActionBar().setTitle(device.getName());

                device.addPluginsChangedListener(pluginsChangedListener);
                pluginsChangedListener.onPluginsChanged(device);

                if (!device.hasPluginsLoaded()) {
                    device.reloadPluginsFromSettings();
                }
            }
        });



        return rootView;
    }


    private final Device.PluginsChangedListener pluginsChangedListener = new Device.PluginsChangedListener() {
        @Override
        public void onPluginsChanged(final Device device) {
            Log.e("DeviceFramgnet", "Refreshing GUI");
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    try {
                        ArrayList<ListAdapter.Item> items = new ArrayList<>();

                        if (!device.isReachable()) {
                            //Not reachable, show unpair button
                            Button b = new Button(mActivity);
                            b.setText(R.string.device_menu_unpair);
                            b.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    device.unpair();
                                    //finish();
                                }
                            });
                            items.add(new TextItem(getString(R.string.device_not_reachable)));
                            items.add(new ButtonItem(b));
                        } else {
                            //Plugins button list
                            final Collection<Plugin> plugins = device.getLoadedPlugins().values();
                            for (final Plugin p : plugins) {
                                if (!p.hasMainActivity()) continue;
                                if (p.displayInContextMenu()) continue;

                                items.add(new PluginItem(p, new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        p.startMainActivity(mActivity);
                                    }
                                }));
                            }

                            //Failed plugins List
                            final Collection<Plugin> failed = device.getFailedPlugins().values();
                            if (!failed.isEmpty()) {
                                if (errorHeader == null) {
                                    errorHeader = new TextView(mActivity);
                                    errorHeader.setPadding(
                                            0,
                                            ((int)(28 * getResources().getDisplayMetrics().density)),
                                            0,
                                            ((int)(8 * getResources().getDisplayMetrics().density))
                                    );
                                    errorHeader.setOnClickListener(null);
                                    errorHeader.setOnLongClickListener(null);
                                    errorHeader.setText(getResources().getString(R.string.plugins_failed_to_load));
                                }
                                items.add(new CustomItem(errorHeader));
                                for (final Plugin p : failed) {
                                    items.add(new SmallEntryItem(p.getDisplayName(), new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            p.getErrorDialog(mActivity).show();
                                        }
                                    }));
                                }
                            }
                        }

                        ListView buttonsList = (ListView) rootView.findViewById(R.id.buttons_list);
                        ListAdapter adapter = new ListAdapter(mActivity, items);
                        buttonsList.setAdapter(adapter);

                    } catch (ConcurrentModificationException e) {
                        Log.e("DeviceActivity", "ConcurrentModificationException");
                        this.run(); //Try again
                    }

                    mActivity.invalidateOptionsMenu();

                }
            });

        }
    };

    @Override
    public void onDestroyView() {
        BackgroundService.RunCommand(mActivity, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                Device device = service.getDevice(mDeviceId);
                if (device == null) return;
                device.removePluginsChangedListener(pluginsChangedListener);
            }
        });
        super.onDestroyView();
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {

        Log.e("DeviceFragment", "onPrepareOptionsMenu");

        super.onPrepareOptionsMenu(menu);
        menu.clear();

        if (device == null || !device.isPaired()) {
            return;
        }

        //Plugins button list
        final Collection<Plugin> plugins = device.getLoadedPlugins().values();
        for (final Plugin p : plugins) {
            if (!p.displayInContextMenu()) {
                continue;
            }
            menu.add(p.getActionName()).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    p.startMainActivity(mActivity);
                    return true;
                }
            });
        }

        menu.add(R.string.device_menu_plugins).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                Intent intent = new Intent(mActivity, SettingsActivity.class);
                intent.putExtra("deviceId", mDeviceId);
                startActivity(intent);
                return true;
            }
        });
        menu.add(R.string.device_menu_unpair).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                device.unpair();
                mActivity.onDeviceSelected(null);
                return true;
            }
        });

    }

    @Override
    public void onResume() {
        super.onResume();
        BackgroundService.RunCommand(mActivity, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                if (mDeviceId != null) {
                    Device device = service.getDevice(mDeviceId);
                    device.reloadPluginsFromSettings();
                }
            }
        });
    }
}
