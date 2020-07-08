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
import org.kde.kdeconnect.UserInterface.List.EntryItem;
import org.kde.kdeconnect.UserInterface.List.ListAdapter;
import org.kde.kdeconnect.UserInterface.List.SectionItem;
import org.kde.kdeconnect.UserInterface.ThemeUtil;
import org.kde.kdeconnect_tp.R;
import org.kde.kdeconnect_tp.databinding.DevicesListBinding;

import java.util.ArrayList;
import java.util.Collection;


public class ShareActivity extends AppCompatActivity {
    private DevicesListBinding binding;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.refresh, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_refresh:
                updateComputerListAction();
                break;
            default:
                break;
        }
        return true;
    }

    private void updateComputerListAction() {
        updateComputerList();
        BackgroundService.RunCommand(ShareActivity.this, BackgroundService::onNetworkChange);

        binding.refreshListLayout.setRefreshing(true);
        new Thread(() -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {
            }
            runOnUiThread(() -> binding.refreshListLayout.setRefreshing(false));
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
                    items.add(new EntryItem(d.getName()));
                    section.isEmpty = false;
                }
            }

            runOnUiThread(() -> {
                binding.devicesList.setAdapter(new ListAdapter(ShareActivity.this, items));
                binding.devicesList.setOnItemClickListener((adapterView, view, i, l) -> {
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

        binding = DevicesListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ActionBar actionBar = getSupportActionBar();
        binding.refreshListLayout.setOnRefreshListener(this::updateComputerListAction);
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
