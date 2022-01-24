/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.UserInterface;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper;
import org.kde.kdeconnect.Plugins.BatteryPlugin.BatteryPlugin;
import org.kde.kdeconnect.Plugins.BatteryPlugin.DeviceBatteryInfo;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.UserInterface.List.FailedPluginListItem;
import org.kde.kdeconnect.UserInterface.List.ListAdapter;
import org.kde.kdeconnect.UserInterface.List.PluginItem;
import org.kde.kdeconnect.UserInterface.List.PluginListHeaderItem;
import org.kde.kdeconnect_tp.R;
import org.kde.kdeconnect_tp.databinding.ActivityDeviceBinding;
import org.kde.kdeconnect_tp.databinding.ViewPairErrorBinding;
import org.kde.kdeconnect_tp.databinding.ViewPairRequestBinding;

import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Main view. Displays the current device and its plugins
 */
public class DeviceFragment extends Fragment {

    private static final String ARG_DEVICE_ID = "deviceId";
    private static final String ARG_FROM_DEVICE_LIST = "fromDeviceList";

    private static final String TAG = "KDE/DeviceFragment";

    private String mDeviceId;
    private Device device;

    private MainActivity mActivity;

    private ArrayList<ListAdapter.Item> pluginListItems;

    /**
     * Top-level ViewBinding for this fragment.
     *
     * Host for {@link #pluginListItems}.
     */
    private ActivityDeviceBinding deviceBinding;
    /**
     * Not-yet-paired ViewBinding.
     *
     * Used to start and retry pairing.
     */
    private ViewPairRequestBinding binding;
    /**
     * Cannot-communicate ViewBinding.
     *
     * Used when the remote device is unreachable.
     */
    private ViewPairErrorBinding errorBinding;

    public DeviceFragment() {
    }

    public static DeviceFragment newInstance(String deviceId, boolean fromDeviceList) {
        DeviceFragment frag = new DeviceFragment();

        Bundle args = new Bundle();
        args.putString(ARG_DEVICE_ID, deviceId);
        args.putBoolean(ARG_FROM_DEVICE_LIST, fromDeviceList);
        frag.setArguments(args);

        return frag;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mActivity = ((MainActivity) getActivity());
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() == null || !getArguments().containsKey(ARG_DEVICE_ID)) {
            throw new RuntimeException("You must instantiate a new DeviceFragment using DeviceFragment.newInstance()");
        }

        mDeviceId = getArguments().getString(ARG_DEVICE_ID);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        deviceBinding = ActivityDeviceBinding.inflate(inflater, container, false);

        // Inner binding for the layout shown when we're not paired yet...
        binding = deviceBinding.pairRequest;
        // ...and for when pairing doesn't (or can't) work
        errorBinding = deviceBinding.pairError;

        binding.pairButton.setOnClickListener(v -> {
            binding.pairButton.setVisibility(View.GONE);
            binding.pairMessage.setText("");
            binding.pairVerification.setVisibility(View.VISIBLE);
            binding.pairVerification.setText(SslHelper.getVerificationKey(SslHelper.certificate, device.certificate));
            binding.pairProgress.setVisibility(View.VISIBLE);
            BackgroundService.RunCommand(mActivity, service -> {
                device = service.getDevice(mDeviceId);
                if (device == null) return;
                device.requestPairing();
            });
        });
        binding.acceptButton.setOnClickListener(v -> {
            if (device != null) {
                device.acceptPairing();
                binding.pairingButtons.setVisibility(View.GONE);
            }
        });
        binding.rejectButton.setOnClickListener(v -> {
            if (device != null) {
                //Remove listener so buttons don't show for a while before changing the view
                device.removePluginsChangedListener(pluginsChangedListener);
                device.removePairingCallback(pairingCallback);
                device.rejectPairing();
            }
            mActivity.onDeviceSelected(null);
        });

        setHasOptionsMenu(true);

        BackgroundService.RunCommand(mActivity, service -> {
            device = service.getDevice(mDeviceId);
            if (device == null) {
                Log.e(TAG, "Trying to display a device fragment but the device is not present");
                mActivity.onDeviceSelected(null);
                return;
            }

            mActivity.getSupportActionBar().setTitle(device.getName());

            device.addPairingCallback(pairingCallback);
            device.addPluginsChangedListener(pluginsChangedListener);

            refreshUI();

        });

        return deviceBinding.getRoot();
    }

    String getDeviceId() { return mDeviceId; }

    private final Device.PluginsChangedListener pluginsChangedListener = device -> refreshUI();

    @Override
    public void onDestroyView() {
        BackgroundService.RunCommand(mActivity, service -> {
            Device device = service.getDevice(mDeviceId);
            if (device == null) return;
            device.removePluginsChangedListener(pluginsChangedListener);
            device.removePairingCallback(pairingCallback);
        });

        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
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
            menu.add(p.getActionName()).setOnMenuItemClickListener(item -> {
                p.startMainActivity(mActivity);
                return true;
            });
        }

        menu.add(R.string.device_menu_plugins).setOnMenuItemClickListener(menuItem -> {
            Intent intent = new Intent(mActivity, PluginSettingsActivity.class);
            intent.putExtra("deviceId", mDeviceId);
            startActivity(intent);
            return true;
        });

        if (device.isReachable()) {

            menu.add(R.string.encryption_info_title).setOnMenuItemClickListener(menuItem -> {
                Context context = mActivity;
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle(context.getResources().getString(R.string.encryption_info_title));
                builder.setPositiveButton(context.getResources().getString(R.string.ok), (dialog, id) -> dialog.dismiss());

                if (device.certificate == null) {
                    builder.setMessage(R.string.encryption_info_msg_no_ssl);
                } else {
                    builder.setMessage(context.getResources().getString(R.string.my_device_fingerprint) + "\n" + SslHelper.getCertificateHash(SslHelper.certificate) + "\n\n"
                            + context.getResources().getString(R.string.remote_device_fingerprint) + "\n" + SslHelper.getCertificateHash(device.certificate));
                }
                builder.show();
                return true;
            });
        }

        if (device.isPaired()) {

            menu.add(R.string.device_menu_unpair).setOnMenuItemClickListener(menuItem -> {
                //Remove listener so buttons don't show for a while before changing the view
                device.removePluginsChangedListener(pluginsChangedListener);
                device.removePairingCallback(pairingCallback);
                device.unpair();
                mActivity.onDeviceSelected(null);
                return true;
            });
        }

    }

    @Override
    public void onResume() {
        super.onResume();

        getView().setFocusableInTouchMode(true);
        getView().requestFocus();
        getView().setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
                boolean fromDeviceList = getArguments().getBoolean(ARG_FROM_DEVICE_LIST, false);
                // Handle back button so we go to the list of devices in case we came from there
                if (fromDeviceList) {
                    mActivity.onDeviceSelected(null);
                    return true;
                }
            }
            return false;
        });
    }

    private void refreshUI() {
        if (device == null || binding == null) {
            return;
        }

        //Once in-app, there is no point in keep displaying the notification if any
        device.hidePairingNotification();

        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (device.isPairRequestedByPeer()) {
                    binding.pairMessage.setText(R.string.pair_requested);
                    binding.pairVerification.setVisibility(View.VISIBLE);
                    binding.pairVerification.setText(SslHelper.getVerificationKey(SslHelper.certificate, device.certificate));
                    binding.pairingButtons.setVisibility(View.VISIBLE);
                    binding.pairProgress.setVisibility(View.GONE);
                    binding.pairButton.setVisibility(View.GONE);
                    binding.pairRequestButtons.setVisibility(View.VISIBLE);
                } else {
                    boolean paired = device.isPaired();
                    boolean reachable = device.isReachable();

                    binding.pairingButtons.setVisibility(paired ? View.GONE : View.VISIBLE);
                    errorBinding.errorMessageContainer.setVisibility((paired && !reachable) ? View.VISIBLE : View.GONE);
                    errorBinding.notReachableMessage.setVisibility((paired && !reachable) ? View.VISIBLE : View.GONE);
                    deviceBinding.viewStatusContainer.setVisibility((paired && reachable) ? View.VISIBLE : View.GONE);

                    try {
                        pluginListItems = new ArrayList<>();

                        if (paired && reachable) {
                            //Plugins button list
                            final Collection<Plugin> plugins = device.getLoadedPlugins().values();
                            for (final Plugin p : plugins) {
                                if (!p.hasMainActivity()) continue;
                                if (p.displayInContextMenu()) continue;

                                pluginListItems.add(new PluginItem(p, v -> p.startMainActivity(mActivity)));
                            }
                            DeviceFragment.this.createPluginsList(device.getPluginsWithoutPermissions(), R.string.plugins_need_permission, (plugin) -> {
                                DialogFragment dialog = plugin.getPermissionExplanationDialog();
                                if (dialog != null) {
                                    dialog.show(getChildFragmentManager(), null);
                                }
                            });
                            DeviceFragment.this.createPluginsList(device.getPluginsWithoutOptionalPermissions(), R.string.plugins_need_optional_permission, (plugin) -> {
                                DialogFragment dialog = plugin.getOptionalPermissionExplanationDialog();

                                if (dialog != null) {
                                    dialog.show(getChildFragmentManager(), null);
                                }
                            });

                            DeviceFragment.this.displayBatteryInfoIfPossible();
                        }

                        ListAdapter adapter = new ListAdapter(mActivity, pluginListItems);
                        deviceBinding.buttonsList.setAdapter(adapter);

                        mActivity.invalidateOptionsMenu();

                    } catch (IllegalStateException e) {
                        //Ignore: The activity was closed while we were trying to update it
                    } catch (ConcurrentModificationException e) {
                        Log.e(TAG, "ConcurrentModificationException");
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
            mActivity.runOnUiThread(() -> {
                if (binding == null) return;
                binding.pairMessage.setText(error);
                binding.pairVerification.setText("");
                binding.pairVerification.setVisibility(View.GONE);
                binding.pairProgress.setVisibility(View.GONE);
                binding.pairButton.setVisibility(View.VISIBLE);
                binding.pairRequestButtons.setVisibility(View.GONE);
                refreshUI();
            });
        }

        @Override
        public void unpaired() {
            mActivity.runOnUiThread(() -> {
                if (binding == null) return;
                binding.pairMessage.setText(R.string.device_not_paired);
                binding.pairVerification.setVisibility(View.GONE);
                binding.pairProgress.setVisibility(View.GONE);
                binding.pairButton.setVisibility(View.VISIBLE);
                binding.pairRequestButtons.setVisibility(View.GONE);
                refreshUI();
            });
        }

    };

    private void createPluginsList(ConcurrentHashMap<String, Plugin> plugins, int headerText, FailedPluginListItem.Action action) {
        if (plugins.isEmpty())
            return;

        pluginListItems.add(new PluginListHeaderItem(headerText));
        for (Plugin plugin : plugins.values()) {
            if (!device.isPluginEnabled(plugin.getPluginKey())) {
                continue;
            }
            pluginListItems.add(new FailedPluginListItem(plugin, action));
        }
    }

    /**
     * This method tries to display battery info for the remote device. Includes
     * <ul>
     *     <li>Current charge as a percentage</li>
     *     <li>Whether the remote device is low on power</li>
     *     <li>Whether the remote device is currently charging</li>
     * </ul>
     * <p>
     *     This will show a simple message on the view instead if we don't have
     *     accurate info right now.
     * </p>
     */
    private void displayBatteryInfoIfPossible() {
        boolean canDisplayBatteryInfo = false;
        BatteryPlugin batteryPlugin = (BatteryPlugin) device.getLoadedPlugins().get(
                Plugin.getPluginKey(BatteryPlugin.class)
        );

        if (batteryPlugin != null) {
            DeviceBatteryInfo info = batteryPlugin.getRemoteBatteryInfo();
            if (info != null) {
                canDisplayBatteryInfo = true;

                Context ctx = deviceBinding.viewBatteryStatus.getContext();

                boolean isCharging = info.isCharging();
                @StringRes
                int resId;
                if (isCharging) {
                    resId = R.string.battery_status_charging_format;
                } else if (BatteryPlugin.isLowBattery(info))  {
                    resId = R.string.battery_status_low_format;
                } else {
                    resId = R.string.battery_status_format;
                }

                deviceBinding.viewBatteryStatus.setChecked(isCharging);
                deviceBinding.viewBatteryStatus.setText(ctx.getString(resId, info.getCurrentCharge()));
            }
        }

        if (!canDisplayBatteryInfo) {
            deviceBinding.viewBatteryStatus.setText(R.string.battery_status_unknown);
        }
    }
}
