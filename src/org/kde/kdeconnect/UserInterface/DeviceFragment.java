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
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.KeyEvent;
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
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.UserInterface.List.CustomItem;
import org.kde.kdeconnect.UserInterface.List.ListAdapter;
import org.kde.kdeconnect.UserInterface.List.PluginItem;
import org.kde.kdeconnect.UserInterface.List.SmallEntryItem;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


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

    public DeviceFragment(String deviceId, boolean fromDeviceList) {
        Bundle args = new Bundle();
        args.putString(ARG_DEVICE_ID, deviceId);
        args.putBoolean("fromDeviceList", fromDeviceList);
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

        final String deviceId = getArguments().getString(ARG_DEVICE_ID);
        if (deviceId != null) {
            mDeviceId = deviceId;
        }

        setHasOptionsMenu(true);

        //Log.e("DeviceFragment", "device: " + deviceId);

        BackgroundService.RunCommand(mActivity, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                device = service.getDevice(mDeviceId);
                if (device == null) {
                    Log.e("DeviceFragment", "Trying to display a device fragment but the device is not present");
                    mActivity.onDeviceSelected(null);
                    return;
                }

                mActivity.getSupportActionBar().setTitle(device.getName());

                device.addPairingCallback(pairingCallback);
                device.addPluginsChangedListener(pluginsChangedListener);

                refreshUI();

                //TODO: Is this needed?
                //if (!device.hasPluginsLoaded() && device.isReachable()) {
                //    device.reloadPluginsFromSettings();
                //}
            }
        });

        final Button pairButton = (Button)rootView.findViewById(R.id.pair_button);
        pairButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pairButton.setVisibility(View.GONE);
                ((TextView) rootView.findViewById(R.id.pair_message)).setText("");
                rootView.findViewById(R.id.pair_progress).setVisibility(View.VISIBLE);
                BackgroundService.RunCommand(mActivity, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        device = service.getDevice(deviceId);
                        if (device == null) return;
                        device.requestPairing();
                    }
                });
            }
        });

        rootView.findViewById(R.id.accept_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BackgroundService.RunCommand(mActivity, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        if (device != null) {
                            device.acceptPairing();
                            rootView.findViewById(R.id.pairing_buttons).setVisibility(View.GONE);
                        }
                    }
                });
            }
        });

        rootView.findViewById(R.id.reject_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BackgroundService.RunCommand(mActivity, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        if (device != null) {
                            //Remove listener so buttons don't show for a while before changing the view
                            device.removePluginsChangedListener(pluginsChangedListener);
                            device.removePairingCallback(pairingCallback);
                            device.rejectPairing();
                        }
                        mActivity.onDeviceSelected(null);
                    }
                });
            }
        });

        return rootView;
    }


    private final Device.PluginsChangedListener pluginsChangedListener = new Device.PluginsChangedListener() {
        @Override
        public void onPluginsChanged(final Device device) {
            refreshUI();
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
                device.removePairingCallback(pairingCallback);
            }
        });
        super.onDestroyView();
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {

        //Log.e("DeviceFragment", "onPrepareOptionsMenu");

        super.onPrepareOptionsMenu(menu);
        menu.clear();

        if (device == null) {
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

        if (device.isPaired()) {

            menu.add(R.string.encryption_info_title).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem menuItem) {
                    Context context = mActivity;
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setTitle(context.getResources().getString(R.string.encryption_info_title));
                    builder.setPositiveButton(context.getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                        }
                    });

                    if (device.certificate == null) {
                        builder.setMessage(R.string.encryption_info_msg_no_ssl);
                    } else {
                        builder.setMessage(context.getResources().getString(R.string.my_device_fingerprint) + "\n" + SslHelper.getCertificateHash(SslHelper.certificate) + "\n\n"
                                + context.getResources().getString(R.string.remote_device_fingerprint) + "\n" + SslHelper.getCertificateHash(device.certificate));
                    }
                    builder.create().show();
                    return true;
                }
            });

            menu.add(R.string.device_menu_unpair).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem menuItem) {
                    //Remove listener so buttons don't show for a while before changing the view
                    device.removePluginsChangedListener(pluginsChangedListener);
                    device.removePairingCallback(pairingCallback);
                    device.unpair();
                    mActivity.onDeviceSelected(null);
                    return true;
                }
            });
        }

    }

    @Override
    public void onResume() {
        super.onResume();

        //TODO: Is this needed?
        /*
        BackgroundService.RunCommand(mActivity, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                if (mDeviceId != null) {
                    Device device = service.getDevice(mDeviceId);
                    if (device != null && device.isReachable()) {
                        device.reloadPluginsFromSettings();
                    }
                }
            }
        });
        */

        getView().setFocusableInTouchMode(true);
        getView().requestFocus();
        getView().setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
                    boolean fromDeviceList = getArguments().getBoolean("fromDeviceList", false);
                    // Handle back button so we go to the list of devices in case we came from there
                    if (fromDeviceList) {
                        mActivity.onDeviceSelected(null);
                        return true;
                    }
                }
                return false;
            }
        });
    }

    void refreshUI() {
        //Log.e("DeviceFragment", "refreshUI");

        if (device == null || rootView == null) {
            return;
        }

        //Once in-app, there is no point in keep displaying the notification if any
        device.hidePairingNotification();

        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {

                if (device.isPairRequestedByPeer()) {
                    ((TextView) rootView.findViewById(R.id.pair_message)).setText(R.string.pair_requested);
                    rootView.findViewById(R.id.pair_progress).setVisibility(View.GONE);
                    rootView.findViewById(R.id.pair_button).setVisibility(View.GONE);
                    rootView.findViewById(R.id.pair_request).setVisibility(View.VISIBLE);
                } else {

                    boolean paired = device.isPaired();
                    boolean reachable = device.isReachable();

                    rootView.findViewById(R.id.pairing_buttons).setVisibility(paired ? View.GONE : View.VISIBLE);
                    rootView.findViewById(R.id.unpair_message).setVisibility((paired && !reachable) ? View.VISIBLE : View.GONE);

                    try {
                        ArrayList<ListAdapter.Item> items = new ArrayList<>();

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
                        final ConcurrentHashMap<String, Plugin> failed = device.getFailedPlugins();
                        if (!failed.isEmpty()) {
                            if (errorHeader == null) {
                                errorHeader = new TextView(mActivity);
                                errorHeader.setPadding(
                                        0,
                                        ((int) (28 * getResources().getDisplayMetrics().density)),
                                        0,
                                        ((int) (8 * getResources().getDisplayMetrics().density))
                                );
                                errorHeader.setOnClickListener(null);
                                errorHeader.setOnLongClickListener(null);
                                errorHeader.setText(getResources().getString(R.string.plugins_failed_to_load));
                            }
                            items.add(new CustomItem(errorHeader));
                            for (Map.Entry<String, Plugin> entry : failed.entrySet()) {
                                String pluginKey = entry.getKey();
                                final Plugin plugin = entry.getValue();
                                if (plugin == null) {
                                    items.add(new SmallEntryItem(pluginKey));
                                } else {
                                    items.add(new SmallEntryItem(plugin.getDisplayName(), new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            plugin.getErrorDialog(mActivity).show();
                                        }
                                    }));
                                }
                            }
                        }

                        ListView buttonsList = (ListView) rootView.findViewById(R.id.buttons_list);
                        ListAdapter adapter = new ListAdapter(mActivity, items);
                        buttonsList.setAdapter(adapter);

                        mActivity.invalidateOptionsMenu();

                    } catch (IllegalStateException e) {
                        e.printStackTrace();
                        //Ignore: The activity was closed while we were trying to update it
                    } catch (ConcurrentModificationException e) {
                        Log.e("DeviceActivity", "ConcurrentModificationException");
                        this.run(); //Try again
                    }

                }
            }
        });

    }

    private final Device.PairingCallback pairingCallback = new Device.PairingCallback() {

        @Override
        public void incomingRequest() {
            refreshUI();
        }

        @Override
        public void pairingSuccessful() {
            refreshUI();
        }

        @Override
        public void pairingFailed(final String error) {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ((TextView) rootView.findViewById(R.id.pair_message)).setText(error);
                    rootView.findViewById(R.id.pair_progress).setVisibility(View.GONE);
                    rootView.findViewById(R.id.pair_button).setVisibility(View.VISIBLE);
                    rootView.findViewById(R.id.pair_request).setVisibility(View.GONE);
                    refreshUI();
                }
            });
        }

        @Override
        public void unpaired() {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ((TextView) rootView.findViewById(R.id.pair_message)).setText(R.string.device_not_paired);
                    rootView.findViewById(R.id.pair_progress).setVisibility(View.GONE);
                    rootView.findViewById(R.id.pair_button).setVisibility(View.VISIBLE);
                    rootView.findViewById(R.id.pair_request).setVisibility(View.GONE);
                    refreshUI();
                }
            });
        }

    };

}
