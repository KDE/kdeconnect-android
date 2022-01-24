/*
 * SPDX-FileCopyrightText: 2014 Achilleas Koutsou <achilleas.k@gmail.com>
 * SPDX-FileCopyrightText: 2019 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.UserInterface;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.TooltipCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import org.kde.kdeconnect_tp.R;
import org.kde.kdeconnect_tp.databinding.ActivityCustomDevicesBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

//TODO: Require wifi connection so entries can be verified
//TODO: Resolve to ip address and don't allow unresolvable or duplicates based on ip address
//TODO: Sort the list
public class CustomDevicesActivity extends AppCompatActivity implements CustomDevicesAdapter.Callback {
    private static final String TAG_ADD_DEVICE_DIALOG = "AddDeviceDialog";

    private static final String KEY_CUSTOM_DEVLIST_PREFERENCE = "device_list_preference";
    private static final String IP_DELIM = ",";
    private static final String KEY_EDITING_DEVICE_AT_POSITION = "EditingDeviceAtPosition";

    private RecyclerView recyclerView;
    private TextView emptyListMessage;

    private ArrayList<String> customDeviceList;
    private EditTextAlertDialogFragment addDeviceDialog;
    private SharedPreferences sharedPreferences;
    private CustomDevicesAdapter customDevicesAdapter;
    private DeletedCustomDevice lastDeletedCustomDevice;
    private int editingDeviceAtPosition;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtil.setUserPreferredTheme(this);
        super.onCreate(savedInstanceState);

        final ActivityCustomDevicesBinding binding = ActivityCustomDevicesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        recyclerView = binding.recyclerView;
        emptyListMessage = binding.emptyListMessage;
        final FloatingActionButton fab = binding.floatingActionButton;

        setSupportActionBar(binding.toolbarLayout.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        fab.setOnClickListener(v -> showEditTextDialog(""));

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        customDeviceList = getCustomDeviceList(sharedPreferences);

        showEmptyListMessageIfRequired();

        customDevicesAdapter = new CustomDevicesAdapter(this);
        customDevicesAdapter.setCustomDevices(customDeviceList);

        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this, RecyclerView.VERTICAL, false));
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        recyclerView.setAdapter(customDevicesAdapter);

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

    private void showEditTextDialog(@NonNull String text) {
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
        String serialized = TextUtils.join(IP_DELIM, customDeviceList);
        sharedPreferences
                .edit()
                .putString(KEY_CUSTOM_DEVLIST_PREFERENCE, serialized)
                .apply();
    }

    private static ArrayList<String> deserializeIpList(String serialized) {
        ArrayList<String> ipList = new ArrayList<>();

        if (!serialized.isEmpty()) {
            Collections.addAll(ipList, serialized.split(IP_DELIM));
        }

        return ipList;
    }

    public static ArrayList<String> getCustomDeviceList(SharedPreferences sharedPreferences) {
        String deviceListPrefs = sharedPreferences.getString(KEY_CUSTOM_DEVLIST_PREFERENCE, "");

        return deserializeIpList(deviceListPrefs);
    }

    @Override
    public void onCustomDeviceClicked(String customDevice) {
        editingDeviceAtPosition = customDeviceList.indexOf(customDevice);
        showEditTextDialog(customDevice);
    }

    @Override
    public void onCustomDeviceDismissed(String customDevice) {
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
                .addCallback(new BaseTransientBottomBar.BaseCallback<Snackbar>() {
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
        public void onPositiveButtonClicked() {
            if (addDeviceDialog.editText.getText() != null) {
                String deviceNameOrIP = addDeviceDialog.editText.getText().toString().trim();

                // don't add empty string (after trimming)
                if (!deviceNameOrIP.isEmpty() && !customDeviceList.contains(deviceNameOrIP)) {
                    if (editingDeviceAtPosition >= 0) {
                        customDeviceList.set(editingDeviceAtPosition, deviceNameOrIP);
                        customDevicesAdapter.notifyItemChanged(editingDeviceAtPosition);
                    } else {
                        customDeviceList.add(deviceNameOrIP);
                        customDevicesAdapter.notifyItemInserted(customDeviceList.size() - 1);
                    }

                    saveList();
                    showEmptyListMessageIfRequired();
                }
            }
        }

        @Override
        public void onDismiss() {
            editingDeviceAtPosition = -1;
        }
    }

    private static class DeletedCustomDevice {
        @NonNull String hostnameOrIP;
        int position;

        DeletedCustomDevice(@NonNull String hostnameOrIP, int position) {
            this.hostnameOrIP = hostnameOrIP;
            this.position = position;
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        super.onBackPressed();
        return true;
    }
}
