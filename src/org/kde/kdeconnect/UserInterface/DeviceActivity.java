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
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.UserInterface.List.ButtonItem;
import org.kde.kdeconnect.UserInterface.List.CustomItem;
import org.kde.kdeconnect.UserInterface.List.ListAdapter;
import org.kde.kdeconnect.UserInterface.List.SectionItem;
import org.kde.kdeconnect.UserInterface.List.SmallEntryItem;
import org.kde.kdeconnect.UserInterface.List.TextItem;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;

public class DeviceActivity extends ActionBarActivity {

    static private String deviceId; //Static because if we get here by using the back button in the action bar, the extra deviceId will not be set.
    private Device device;

    public static final int RESULT_NEEDS_RELOAD = Activity.RESULT_FIRST_USER;

    TextView errorHeader;

    private final Device.PluginsChangedListener pluginsChangedListener = new Device.PluginsChangedListener() {
        @Override
        public void onPluginsChanged(final Device device) {

            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    try {
                        ArrayList<ListAdapter.Item> items = new ArrayList<ListAdapter.Item>();

                        if (!device.isReachable()) {
                            //Not reachable, show unpair button
                            Button b = new Button(DeviceActivity.this);
                            b.setText(R.string.device_menu_unpair);
                            b.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    device.unpair();
                                    finish();
                                }
                            });
                            items.add(new TextItem(getString(R.string.device_not_reachable)));
                            items.add(new ButtonItem(b));
                        } else {
                            //Plugins button list
                            final Collection<Plugin> plugins = device.getLoadedPlugins().values();
                            for (Plugin p : plugins) {
                                Button b = p.getInterfaceButton(DeviceActivity.this);
                                if (b != null) {
                                    items.add(new SectionItem(p.getDisplayName()));
                                    items.add(new ButtonItem(b));
                                }
                            }

                            //Failed plugins List
                            final Collection<Plugin> failed = device.getFailedPlugins().values();
                            if (!failed.isEmpty()) {
                                if (errorHeader == null) {
                                    errorHeader = new TextView(DeviceActivity.this);
                                    errorHeader.setPadding(0, 48, 0, 0);
                                    errorHeader.setOnClickListener(null);
                                    errorHeader.setOnLongClickListener(null);
                                    errorHeader.setText(getResources().getString(R.string.plugins_failed_to_load));
                                }
                                items.add(new CustomItem(errorHeader));
                                for (final Plugin p : failed) {
                                    items.add(new SmallEntryItem(p.getDisplayName(), new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            p.getErrorDialog(DeviceActivity.this).show();
                                        }
                                    }));
                                }
                            }
                        }

                        ListView buttonsList = (ListView)findViewById(R.id.buttons_list);
                        ListAdapter adapter = new ListAdapter(DeviceActivity.this, items);
                        buttonsList.setAdapter(adapter);

                    } catch(ConcurrentModificationException e) {
                        Log.e("DeviceActivity", "ConcurrentModificationException");
                        this.run(); //Try again
                    }

                }
            });

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE);
        actionBar.setDisplayHomeAsUpEnabled(true);

        if (getIntent().hasExtra("deviceId")) {
            deviceId = getIntent().getStringExtra("deviceId");
        }

        BackgroundService.RunCommand(DeviceActivity.this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                device = service.getDevice(deviceId);
                if (device == null) return;
                setTitle(device.getName());
                device.addPluginsChangedListener(pluginsChangedListener);
                pluginsChangedListener.onPluginsChanged(device);
                if (!device.hasPluginsLoaded()) {
                    device.reloadPluginsFromSettings();
                }
            }
        });

    }

    @Override
    protected void onDestroy() {
        BackgroundService.RunCommand(DeviceActivity.this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                Device device = service.getDevice(deviceId);
                if (device == null) return;
                device.removePluginsChangedListener(pluginsChangedListener);
            }
        });
        super.onDestroy();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.clear();
        if (device != null && device.isPaired()) {
            menu.add(R.string.device_menu_plugins).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem menuItem) {
                    Intent intent = new Intent(DeviceActivity.this, SettingsActivity.class);
                    intent.putExtra("deviceId", deviceId);
                    startActivity(intent);
                    return true;
                }
            });
            menu.add(R.string.device_menu_unpair).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem menuItem) {
                    device.unpair();
                    finish();
                    return true;
                }
            });
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode)
        {
            case RESULT_NEEDS_RELOAD:
                BackgroundService.RunCommand(DeviceActivity.this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        Device device = service.getDevice(deviceId);
                        device.reloadPluginsFromSettings();
                    }
                });
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }


}
