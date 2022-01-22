/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.SharePlugin;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.UserInterface.List.EntryItemWithIcon;
import org.kde.kdeconnect.UserInterface.List.ListAdapter;
import org.kde.kdeconnect.UserInterface.List.SectionItem;
import org.kde.kdeconnect.UserInterface.ThemeUtil;
import org.kde.kdeconnect_tp.R;
import org.kde.kdeconnect_tp.databinding.ActivityShareBinding;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;


public class ShareActivity extends AppCompatActivity {
    private ActivityShareBinding binding;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.refresh, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == R.id.menu_refresh) {
            updateComputerListAction();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void updateComputerListAction() {
        updateComputerList();
        BackgroundService.RunCommand(ShareActivity.this, BackgroundService::onNetworkChange);

        binding.devicesListLayout.refreshListLayout.setRefreshing(true);
        new Thread(() -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {
            }
            runOnUiThread(() -> binding.devicesListLayout.refreshListLayout.setRefreshing(false));
        }).start();
    }

    private void updateComputerList() {
        final Intent intent = getIntent();

        String action = intent.getAction();
        if (!Intent.ACTION_SEND.equals(action) && !Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            finish();
            return;
        }

        BackgroundService.RunCommand(this, service -> {

            Collection<Device> devices = service.getDevices().values();
            final ArrayList<Device> devicesList = new ArrayList<>();
            final ArrayList<ListAdapter.Item> items = new ArrayList<>();

            SectionItem section = new SectionItem(getString(R.string.share_to));
            items.add(section);

            for (Device d : devices) {
                if (d.isReachable() && d.isPaired()) {
                    devicesList.add(d);
                    items.add(new EntryItemWithIcon(d.getName(), d.getIcon()));
                    section.isEmpty = false;
                }
            }

            runOnUiThread(() -> {
                binding.devicesListLayout.devicesList.setAdapter(new ListAdapter(ShareActivity.this, items));
                binding.devicesListLayout.devicesList.setOnItemClickListener((adapterView, view, i, l) -> {
                    Device device = devicesList.get(i - 1); //NOTE: -1 because of the title!
                    BackgroundService.RunWithPlugin(this, device.getDeviceId(), SharePlugin.class, plugin -> plugin.share(intent));
                    finish();
                });
            });
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtil.setUserPreferredTheme(this);

        binding = ActivityShareBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbarLayout.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        ActionBar actionBar = getSupportActionBar();
        binding.devicesListLayout.refreshListLayout.setOnRefreshListener(this::updateComputerListAction);
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
            BackgroundService.RunWithPlugin(this, deviceId, SharePlugin.class, plugin -> {
                plugin.share(intent);
                finish();
            });
        } else {
            BackgroundService.RunCommand(this, service -> {
                service.onNetworkChange();
                service.addDeviceListChangedCallback("ShareActivity", this::updateComputerList);
            });
            updateComputerList();
        }
    }

    @Override
    protected void onStop() {
        BackgroundService.RunCommand(this, service -> service.removeDeviceListChangedCallback("ShareActivity"));
        super.onStop();
    }
}
