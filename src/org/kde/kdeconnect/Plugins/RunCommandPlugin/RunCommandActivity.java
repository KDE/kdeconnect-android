/*
 * Copyright 2015 Aleix Pol Gonzalez <aleixpol@kde.org>
 * Copyright 2015 Albert Vaca Cintora <albertvaka@gmail.com>
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

package org.kde.kdeconnect.Plugins.RunCommandPlugin;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import org.json.JSONException;
import org.json.JSONObject;
import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.UserInterface.List.ListAdapter;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class RunCommandActivity extends AppCompatActivity {

    private String deviceId;

    private void updateView() {
        BackgroundService.RunCommand(this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(final BackgroundService service) {

                final Device device = service.getDevice(deviceId);
                final RunCommandPlugin plugin = device.getPlugin(RunCommandPlugin.class);
                if (plugin == null) {
                    Log.e("RunCommandActivity", "device has no runcommand plugin!");
                    return;
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ListView view = (ListView) findViewById(R.id.listView1);

                        final ArrayList<ListAdapter.Item> commandItems = new ArrayList<>();
                        for (JSONObject obj : plugin.getCommandList()) {
                            try {
                                commandItems.add(new CommandEntry(obj.getString("name"),
                                        obj.getString("command"), obj.getString("key")));
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }

                        Collections.sort(commandItems, new Comparator<ListAdapter.Item>() {
                            @Override
                            public int compare(ListAdapter.Item lhs, ListAdapter.Item rhs) {
                                String lName = ((CommandEntry) lhs).getName();
                                String rName = ((CommandEntry) rhs).getName();
                                return lName.compareTo(rName);
                            }
                        });

                        ListAdapter adapter = new ListAdapter(RunCommandActivity.this, commandItems);

                        view.setAdapter(adapter);
                        view.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                            @Override
                            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                                CommandEntry entry = (CommandEntry) commandItems.get(i);
                                plugin.runCommand(entry.getKey());
                            }
                        });
                    }
                });
            }
        });
    }

    private final RunCommandPlugin.CommandsChangedCallback theCallback = new RunCommandPlugin.CommandsChangedCallback() {
        @Override
        public void update() {
            updateView();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);

        deviceId = getIntent().getStringExtra("deviceId");

        updateView();
    }

    @Override
    protected void onResume() {
        super.onResume();

        BackgroundService.RunCommand(this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(final BackgroundService service) {

                final Device device = service.getDevice(deviceId);
                final RunCommandPlugin plugin = device.getPlugin(RunCommandPlugin.class);
                if (plugin == null) {
                    Log.e("RunCommandActivity", "device has no runcommand plugin!");
                    return;
                }
                plugin.addCommandsUpdatedCallback(theCallback);
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();

        BackgroundService.RunCommand(this, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(final BackgroundService service) {

                final Device device = service.getDevice(deviceId);
                final RunCommandPlugin plugin = device.getPlugin(RunCommandPlugin.class);
                if (plugin == null) {
                    Log.e("RunCommandActivity", "device has no runcommand plugin!");
                    return;
                }
                plugin.removeCommandsUpdatedCallback(theCallback);
            }
        });
    }
}
