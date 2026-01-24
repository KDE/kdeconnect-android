/*
 * SPDX-FileCopyrightText: 2014 Achilleas Koutsou <achilleas.k@gmail.com>
 * SPDX-FileCopyrightText: 2019 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.TooltipCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import org.kde.kdeconnect.DeviceHost;
import org.kde.kdeconnect.helpers.WindowHelper;
import org.kde.kdeconnect.base.BaseActivity;
import org.kde.kdeconnect_tp.R;
import org.kde.kdeconnect_tp.databinding.ActivityCustomDevicesBinding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;

import kotlin.Lazy;
import kotlin.LazyKt;
import kotlin.Unit;

public class CustomDevicesActivity extends BaseActivity<ActivityCustomDevicesBinding> implements CustomDevicesAdapter.Callback {
    private static final String TAG_ADD_DEVICE_DIALOG = "AddDeviceDialog";

    private static final String KEY_CUSTOM_DEVLIST_PREFERENCE = "device_list_preference";
    private static final String IP_DELIM = ",";
    private static final String KEY_EDITING_DEVICE_AT_POSITION = "EditingDeviceAtPosition";

    private RecyclerView recyclerView;
    private TextView emptyListMessage;

    private ArrayList<DeviceHost> customDeviceList;
    private EditTextAlertDialogFragment addDeviceDialog;
    private CustomDevicesAdapter customDevicesAdapter;
    private DeletedCustomDevice lastDeletedCustomDevice;
    private int editingDeviceAtPosition;

    private final Lazy<ActivityCustomDevicesBinding> lazyBinding = LazyKt.lazy(() -> ActivityCustomDevicesBinding.inflate(getLayoutInflater()));

    @NonNull
    @Override
    protected ActivityCustomDevicesBinding getBinding() {
        return lazyBinding.getValue();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        recyclerView = getBinding().recyclerView;
        emptyListMessage = getBinding().emptyListMessage;
        final FloatingActionButton fab = getBinding().floatingActionButton;

        setSupportActionBar(getBinding().toolbarLayout.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        fab.setOnClickListener(v -> showEditTextDialog(null));

        customDeviceList = getCustomDeviceList(this);
        customDeviceList.forEach(host -> host.checkReachable(() -> {
            runOnUiThread(() -> customDevicesAdapter.notifyDataSetChanged());
            return Unit.INSTANCE;
        }));

        showEmptyListMessageIfRequired();

        customDevicesAdapter = new CustomDevicesAdapter(this, getApplicationContext());
        customDevicesAdapter.setCustomDevices(customDeviceList);

        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this, RecyclerView.VERTICAL, false));
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        recyclerView.setAdapter(customDevicesAdapter);

        WindowHelper.setupBottomPadding(recyclerView);
        WindowHelper.setupBottomMargin(getBinding().floatingActionButton);

        addDeviceDialog = (EditTextAlertDialogFragment) getSupportFragmentManager().findFragmentByTag(TAG_ADD_DEVICE_DIALOG);
        if (addDeviceDialog != null) {
            addDeviceDialog.setCallback(new AddDeviceDialogCallback());
        }

        TooltipCompat.setTooltipText(fab, getString(R.string.custom_device_fab_hint));

        if (savedInstanceState != null) {
            editingDeviceAtPosition = savedInstanceState.getInt(KEY_EDITING_DEVICE_AT_POSITION);
        } else {
            editingDeviceAtPosition = -1;
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt(KEY_EDITING_DEVICE_AT_POSITION, editingDeviceAtPosition);
    }

    private void showEmptyListMessageIfRequired() {
        emptyListMessage.setVisibility(customDeviceList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showEditTextDialog(DeviceHost deviceHost) {
        String text = "";
        if (deviceHost != null) {
            text = deviceHost.toString();
        }
        addDeviceDialog = new EditTextAlertDialogFragment.Builder()
                .setTitle(R.string.add_device_dialog_title)
                .setHint(R.string.add_device_hint)
                .setText(text)
                .setPositiveButton(R.string.ok)
                .setNegativeButton(R.string.cancel)
                .create();

        addDeviceDialog.setCallback(new AddDeviceDialogCallback());
        addDeviceDialog.show(getSupportFragmentManager(), TAG_ADD_DEVICE_DIALOG);
    }

    private void saveList() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String serialized = TextUtils.join(IP_DELIM, customDeviceList);
        sharedPreferences
                .edit()
                .putString(KEY_CUSTOM_DEVLIST_PREFERENCE, serialized)
                .apply();
    }

    private static ArrayList<DeviceHost> deserializeIpList(String serialized) {
        ArrayList<DeviceHost> ipList = new ArrayList<>();

        if (!serialized.isEmpty()) {
            for (String ip: serialized.split(IP_DELIM)) {
                DeviceHost deviceHost = DeviceHost.toDeviceHostOrNull(ip);
                // To prevent crashes when migrating if invalid hosts are present
                if (deviceHost != null) {
                    ipList.add(deviceHost);
                }
            }
        }

        return ipList;
    }

    public static ArrayList<DeviceHost> getCustomDeviceList(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String deviceListPrefs = sharedPreferences.getString(KEY_CUSTOM_DEVLIST_PREFERENCE, "");
        ArrayList<DeviceHost> list = deserializeIpList(deviceListPrefs);
        list.sort(Comparator.comparing(DeviceHost::toString));
        return list;
    }

    @Override
    public void onCustomDeviceClicked(DeviceHost customDevice) {
        editingDeviceAtPosition = customDeviceList.indexOf(customDevice);
        showEditTextDialog(customDevice);
    }

    @Override
    public void onCustomDeviceDismissed(DeviceHost customDevice) {
        lastDeletedCustomDevice = new DeletedCustomDevice(customDevice, customDeviceList.indexOf(customDevice));
        customDeviceList.remove(lastDeletedCustomDevice.position);
        customDevicesAdapter.notifyItemRemoved(lastDeletedCustomDevice.position);
        saveList();
        showEmptyListMessageIfRequired();

        Snackbar.make(recyclerView, R.string.custom_device_deleted, Snackbar.LENGTH_LONG)
                .setAction(R.string.undo, v -> {
                    customDeviceList.add(lastDeletedCustomDevice.position, lastDeletedCustomDevice.hostnameOrIP);
                    customDevicesAdapter.notifyItemInserted(lastDeletedCustomDevice.position);
                    lastDeletedCustomDevice = null;
                    saveList();
                    showEmptyListMessageIfRequired();
                })
                .addCallback(new BaseTransientBottomBar.BaseCallback<>() {
                    @Override
                    public void onDismissed(Snackbar transientBottomBar, int event) {
                        switch (event) {
                            case DISMISS_EVENT_SWIPE:
                            case DISMISS_EVENT_TIMEOUT:
                                lastDeletedCustomDevice = null;
                                break;
                            case DISMISS_EVENT_ACTION:
                            case DISMISS_EVENT_CONSECUTIVE:
                            case DISMISS_EVENT_MANUAL:
                                break;
                        }
                    }
                })
                .show();
    }

    private class AddDeviceDialogCallback extends EditTextAlertDialogFragment.Callback {
        @Override
        public boolean onPositiveButtonClicked() {
            if (addDeviceDialog.editText.getText() != null) {
                String deviceNameOrIP = addDeviceDialog.editText.getText().toString().trim();
                DeviceHost host = DeviceHost.toDeviceHostOrNull(deviceNameOrIP);

                // don't add empty string (after trimming)
                if (host != null) {
                    if (!customDeviceList.stream().anyMatch(h -> h.toString().equals(host.toString()))) {
                        if (editingDeviceAtPosition >= 0) {
                            customDeviceList.set(editingDeviceAtPosition, host);
                            customDevicesAdapter.notifyItemChanged(editingDeviceAtPosition);
                            host.checkReachable(() -> {
                                runOnUiThread(() -> customDevicesAdapter.notifyItemChanged(editingDeviceAtPosition));
                                return Unit.INSTANCE;
                            });
                        }
                        else {
                            // Find insertion position to ensure list remains sorted
                            int pos = 0;
                            while (customDeviceList.size() - 1 >= pos && customDeviceList.get(pos).toString().compareTo(host.toString()) < 0) {
                                pos++;
                            }
                            final int position = pos;

                            customDeviceList.add(position, host);
                            customDevicesAdapter.notifyItemInserted(pos);
                            host.checkReachable(() -> {
                                runOnUiThread(() -> customDevicesAdapter.notifyItemChanged(position));
                                return Unit.INSTANCE;
                            });
                        }

                        saveList();
                        showEmptyListMessageIfRequired();
                    }
                    else {
                        if (editingDeviceAtPosition >= 0 && customDeviceList.get(editingDeviceAtPosition).toString().equals(host.toString())) {
                            return true; // Allow saving without changes when editing an existing entry
                        }
                        Context context = addDeviceDialog.getContext();
                        if (context != null) {
                            Toast.makeText(addDeviceDialog.getContext(), R.string.device_host_duplicate, Toast.LENGTH_SHORT).show();
                            return false;
                        }
                    }
                }
                else {
                    Context context = addDeviceDialog.getContext();
                    if (context != null) {
                        Toast.makeText(addDeviceDialog.getContext(), R.string.device_host_invalid, Toast.LENGTH_SHORT).show();
                        return false;
                    }
                }
            }
            return true;
        }

        @Override
        public void onDismiss() {
            editingDeviceAtPosition = -1;
        }
    }

    private static class DeletedCustomDevice {
        @NonNull final DeviceHost hostnameOrIP;
        final int position;

        DeletedCustomDevice(@NonNull DeviceHost hostnameOrIP, int position) {
            this.hostnameOrIP = hostnameOrIP;
            this.position = position;
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        super.onBackPressed();
        return true;
    }

    @Override
    public boolean isScrollable() {
        return true;
    }
}
