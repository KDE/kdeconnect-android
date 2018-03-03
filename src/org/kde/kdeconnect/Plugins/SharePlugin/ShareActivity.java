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
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.UserInterface.List.EntryItem;
import org.kde.kdeconnect.UserInterface.List.ListAdapter;
import org.kde.kdeconnect.UserInterface.List.SectionItem;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;
import java.util.Collection;


public class ShareActivity extends AppCompatActivity {

    private SwipeRefreshLayout mSwipeRefreshLayout;

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
        BackgroundService.RunCommand(ShareActivity.this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                service.onNetworkChange();
            }
        });

        mSwipeRefreshLayout.setRefreshing(true);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException ignored) {
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mSwipeRefreshLayout.setRefreshing(false);
                    }
                });
            }
        }).start();
    }

    private void updateComputerList() {

        final Intent intent = getIntent();

        String action = intent.getAction();
        if (!Intent.ACTION_SEND.equals(action) && !Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            finish();
            return;
        }

        BackgroundService.RunCommand(this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(final BackgroundService service) {

                Collection<Device> devices = service.getDevices().values();
                final ArrayList<Device> devicesList = new ArrayList<>();
                final ArrayList<ListAdapter.Item> items = new ArrayList<>();

                items.add(new SectionItem(getString(R.string.share_to)));

                for (Device d : devices) {
                    if (d.isReachable() && d.isPaired()) {
                        devicesList.add(d);
                        items.add(new EntryItem(d.getName()));
                    }
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ListView list = (ListView) findViewById(R.id.listView1);
                        list.setAdapter(new ListAdapter(ShareActivity.this, items));
                        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                            @Override
                            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

                                Device device = devicesList.get(i - 1); //NOTE: -1 because of the title!
                                SharePlugin.share(intent, device);
                                finish();
                            }
                        });
                    }
                });

            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_refresh_list);

        ActionBar actionBar = getSupportActionBar();
        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.refresh_list_layout);
        mSwipeRefreshLayout.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        updateComputerListAction();
                    }
                }
        );
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

            BackgroundService.RunCommand(this, new BackgroundService.InstanceCallback() {

                @Override
                public void onServiceStart(BackgroundService service) {
                    Log.d("DirectShare", "sharing to " + service.getDevice(deviceId).getName());
                    Device device = service.getDevice(deviceId);
                    if (device.isReachable() && device.isPaired()) {
                        SharePlugin.share(intent, device);
                    }
                    finish();
                }
            });
        } else {

            BackgroundService.addGuiInUseCounter(this);
            BackgroundService.RunCommand(this, new BackgroundService.InstanceCallback() {
                @Override
                public void onServiceStart(BackgroundService service) {
                    service.onNetworkChange();
                    service.addDeviceListChangedCallback("ShareActivity", new BackgroundService.DeviceListChangedCallback() {
                        @Override
                        public void onDeviceListChanged() {
                            updateComputerList();
                        }
                    });
                }
            });
            updateComputerList();
        }
    }


    @Override
    protected void onStop() {
        BackgroundService.RunCommand(this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                service.removeDeviceListChangedCallback("ShareActivity");
            }
        });
        BackgroundService.removeGuiInUseCounter(this);
        super.onStop();
    }

}
