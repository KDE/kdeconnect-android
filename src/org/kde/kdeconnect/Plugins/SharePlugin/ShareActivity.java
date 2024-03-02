/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.SharePlugin;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.webkit.URLUtil;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.KdeConnect;
import org.kde.kdeconnect.UserInterface.List.EntryItemWithIcon;
import org.kde.kdeconnect.UserInterface.List.ListAdapter;
import org.kde.kdeconnect.UserInterface.List.SectionItem;
import org.kde.kdeconnect_tp.R;
import org.kde.kdeconnect_tp.databinding.ActivityShareBinding;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;


public class ShareActivity extends AppCompatActivity {
    private ActivityShareBinding binding;
    private SharedPreferences mSharedPrefs;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.refresh, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == R.id.menu_refresh) {
            refreshDevicesAction();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void refreshDevicesAction() {
        BackgroundService.ForceRefreshConnections(this);

        binding.devicesListLayout.refreshListLayout.setRefreshing(true);
        binding.devicesListLayout.refreshListLayout.postDelayed(() -> {
            binding.devicesListLayout.refreshListLayout.setRefreshing(false);
        }, 1500);
    }

    private void updateDeviceList() {
        final Intent intent = getIntent();

        String action = intent.getAction();
        if (!Intent.ACTION_SEND.equals(action) && !Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            finish();
            return;
        }

        Collection<Device> devices = KdeConnect.getInstance().getDevices().values();
        final ArrayList<Device> devicesList = new ArrayList<>();
        final ArrayList<ListAdapter.Item> items = new ArrayList<>();

        SectionItem section = new SectionItem(getString(R.string.share_to));
        items.add(section);

        for (Device d : devices) {
            // Show the paired devices even if they are unreachable
            if (d.isPaired()) {
                devicesList.add(d);
                String deviceName = d.getName();
                if (!d.isReachable()) {
                    deviceName += " " + getString(R.string.unreachable_device);
                }
                items.add(new EntryItemWithIcon(deviceName, d.getIcon()));
                section.isEmpty = false;
            }
        }

        binding.devicesListLayout.devicesList.setAdapter(new ListAdapter(ShareActivity.this, items));
        binding.devicesListLayout.devicesList.setOnItemClickListener((adapterView, view, i, l) -> {
            Device device = devicesList.get(i - 1); //NOTE: -1 because of the title!
            SharePlugin plugin = KdeConnect.getInstance().getDevicePlugin(device.getDeviceId(), SharePlugin.class);
            if (!device.isReachable()) {
                // Store the intent to be delivered once device becomes online
                storeIntentForFutureDelivery(device, intent);
            } else if (plugin != null) {
                plugin.share(intent);
            }
            finish();
        });
    }

    private void storeIntentForFutureDelivery(Device device, Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras != null) {
            String text = extras.getString(Intent.EXTRA_TEXT);
            // Only if text is a URL, store it in the cache
            if (URLUtil.isHttpUrl(text) || URLUtil.isHttpsUrl(text)) {
                // Add to previous string set
                Set<String> oldUrlSet = mSharedPrefs.getStringSet(KdeConnect.KEY_UNREACHABLE_URL_LIST + device.getDeviceId(), null);
                // According to the API docs, we should not directly modify the set returned above
                Set<String> newUrlSet = new HashSet<>();
                newUrlSet.add(intent.toUri(0));
                if (oldUrlSet != null) {
                    newUrlSet.addAll(oldUrlSet);
                }
                mSharedPrefs.edit().putStringSet(KdeConnect.KEY_UNREACHABLE_URL_LIST + device.getDeviceId(), newUrlSet).apply();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityShareBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences (this);

        setSupportActionBar(binding.toolbarLayout.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        ActionBar actionBar = getSupportActionBar();
        binding.devicesListLayout.refreshListLayout.setOnRefreshListener(this::refreshDevicesAction);
        if (actionBar != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_CUSTOM);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        final Intent intent = getIntent();
        final String deviceId = intent.getStringExtra("deviceId");

        if (deviceId != null) {
            SharePlugin plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, SharePlugin.class);
            if (plugin != null) {
                plugin.share(intent);
            }
            finish();
        } else {
            KdeConnect.getInstance().addDeviceListChangedCallback("ShareActivity", () -> runOnUiThread(this::updateDeviceList));
            BackgroundService.ForceRefreshConnections(this); // force a network re-discover
            updateDeviceList();
        }
    }

    @Override
    protected void onStop() {
        KdeConnect.getInstance().removeDeviceListChangedCallback("ShareActivity");
        super.onStop();
    }
}
