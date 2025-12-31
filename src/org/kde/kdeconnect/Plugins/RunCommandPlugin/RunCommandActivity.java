/*
 * SPDX-FileCopyrightText: 2015 Aleix Pol Gonzalez <aleixpol@kde.org>
 * SPDX-FileCopyrightText: 2015 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.RunCommandPlugin;

import android.content.ClipboardManager;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;

import org.json.JSONException;
import org.json.JSONObject;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.KdeConnect;
import org.kde.kdeconnect.base.BaseActivity;
import org.kde.kdeconnect_tp.R;
import org.kde.kdeconnect_tp.databinding.ActivityRunCommandBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import kotlin.Lazy;
import kotlin.LazyKt;

public class RunCommandActivity extends BaseActivity<ActivityRunCommandBinding> {

    private final Lazy<ActivityRunCommandBinding> lazyBinding = LazyKt.lazy(() -> ActivityRunCommandBinding.inflate(getLayoutInflater()));

    @NonNull
    @Override
    protected ActivityRunCommandBinding getBinding() {
        return lazyBinding.getValue();
    }

    private String deviceId;
    private final RunCommandPlugin.CommandsChangedCallback commandsChangedCallback = () -> runOnUiThread(this::updateView);
    private List<CommandEntry> commandItems;

    private void updateView() {
        RunCommandPlugin plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, RunCommandPlugin.class);
        if (plugin == null) {
            Log.e("RunCommand", "Plugin is null");
            finish();
            return;
        }

        try {
            registerForContextMenu(getBinding().runCommandsList);

            commandItems = new ArrayList<>();
            List<JSONObject> commandList = plugin.getCommandList();
            Log.d("RunCommand", "Found " + commandList.size() + " commands");
            
            for (JSONObject obj : commandList) {
                try {
                    CommandEntry entry = new CommandEntry(obj);
                    commandItems.add(entry);
                    Log.d("RunCommand", "Added command: " + entry.getName());
                } catch (JSONException e) {
                    Log.e("RunCommand", "Error parsing command: " + obj.toString(), e);
                }
            }

            if (commandItems.isEmpty()) {
                Log.d("RunCommand", "No commands found, showing explanation");
                getBinding().addCommandExplanation.setVisibility(View.VISIBLE);
                return;
            }

            Collections.sort(commandItems, Comparator.comparing(CommandEntry::getName));

            runOnUiThread(() -> {
                // Set up RecyclerView with GridLayoutManager (3 columns)
                getBinding().runCommandsList.setLayoutManager(new GridLayoutManager(this, 3));
                
                // Create and set the adapter
                CommandEntryAdapter adapter = new CommandEntryAdapter(
                    commandItems,
                    (CommandEntry command) -> {
                        Log.d("RunCommand", "Running command: " + command.getName());
                        plugin.runCommand(command.getKey());
                        return kotlin.Unit.INSTANCE;
                    }
                );
                getBinding().runCommandsList.setAdapter(adapter);
                getBinding().addCommandExplanation.setVisibility(View.GONE);
            });
        } catch (Exception e) {
            Log.e("RunCommand", "Error in updateView", e);
            getBinding().addCommandExplanation.setText("Error loading commands: " + e.getMessage());
            getBinding().addCommandExplanation.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setSupportActionBar(getBinding().toolbarLayout.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        deviceId = getIntent().getStringExtra("deviceId");
        Device device = KdeConnect.getInstance().getDevice(deviceId);
        if (device != null) {
            getSupportActionBar().setSubtitle(device.getName());
            RunCommandPlugin plugin = device.getPlugin(RunCommandPlugin.class);
            if (plugin != null) {
                if (plugin.canAddCommand()) {
                    getBinding().addCommandButton.show();
                } else {
                    getBinding().addCommandButton.hide();
                }
                getBinding().addCommandButton.setOnClickListener(v -> {
                    plugin.sendSetupPacket();
                    new AlertDialog.Builder(RunCommandActivity.this)
                            .setTitle(R.string.add_command)
                            .setMessage(R.string.add_command_description)
                            .setPositiveButton(R.string.ok, null)
                            .show();
                });
            }
        }
        updateView();
    }

    // Context menu handling for RecyclerView
    private int selectedPosition = -1;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.runcommand_context, menu);
        
        // Get the position of the long-pressed item
        View view = getCurrentFocus();
        if (view != null) {
            selectedPosition = getBinding().runCommandsList.getChildAdapterPosition(view);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (selectedPosition == -1) {
            return super.onContextItemSelected(item);
        }
        
        if (item.getItemId() == R.id.copy_url_to_clipboard) {
            CommandEntry entry = commandItems.get(selectedPosition);
            String url = "kdeconnect://runcommand/" + deviceId + "/" + entry.getKey();
            ClipboardManager cm = ContextCompat.getSystemService(this, ClipboardManager.class);
            cm.setText(url);
            Toast toast = Toast.makeText(this, R.string.clipboard_toast, Toast.LENGTH_SHORT);
            toast.show();
            selectedPosition = -1; // Reset after handling
            return true;
        }
        selectedPosition = -1; // Reset if not handled
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();

        RunCommandPlugin plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, RunCommandPlugin.class);
        if (plugin == null) {
            finish();
            return;
        }
        plugin.addCommandsUpdatedCallback(commandsChangedCallback);
    }

    @Override
    protected void onPause() {
        super.onPause();

        RunCommandPlugin plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, RunCommandPlugin.class);
        if (plugin == null) {
            return;
        }
        plugin.removeCommandsUpdatedCallback(commandsChangedCallback);
    }

    @Override
    public boolean onSupportNavigateUp() {
        super.onBackPressed();
        return true;
    }
}
