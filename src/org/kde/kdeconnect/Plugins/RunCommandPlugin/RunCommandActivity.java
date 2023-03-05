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

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.json.JSONException;
import org.json.JSONObject;
import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.UserInterface.List.ListAdapter;
import org.kde.kdeconnect.UserInterface.ThemeUtil;
import org.kde.kdeconnect_tp.R;
import org.kde.kdeconnect_tp.databinding.ActivityRunCommandBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class RunCommandActivity extends AppCompatActivity {
    private ActivityRunCommandBinding binding;
    private String deviceId;
    private final RunCommandPlugin.CommandsChangedCallback commandsChangedCallback = this::updateView;
    private List<CommandEntry> commandItems;

    private void updateView() {
        BackgroundService.RunWithPlugin(this, deviceId, RunCommandPlugin.class, plugin -> runOnUiThread(() -> {
            registerForContextMenu(binding.runCommandsList);

            commandItems = new ArrayList<>();
            for (JSONObject obj : plugin.getCommandList()) {
                try {
                    commandItems.add(new CommandEntry(obj.getString("name"),
                            obj.getString("command"), obj.getString("key")));
                } catch (JSONException e) {
                    Log.e("RunCommand", "Error parsing JSON", e);
                }
            }

            Collections.sort(commandItems, Comparator.comparing(CommandEntry::getName));

            ListAdapter adapter = new ListAdapter(RunCommandActivity.this, commandItems);

            binding.runCommandsList.setAdapter(adapter);
            binding.runCommandsList.setOnItemClickListener((adapterView, view1, i, l) ->
                    plugin.runCommand(commandItems.get(i).getKey()));

            String text = getString(R.string.addcommand_explanation);
            if (!plugin.canAddCommand()) {
                text += "\n" + getString(R.string.addcommand_explanation2);
            }
            binding.addComandExplanation.setText(text);
            binding.addComandExplanation.setVisibility(commandItems.isEmpty() ? View.VISIBLE : View.GONE);
        }));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtil.setUserPreferredTheme(this);

        binding = ActivityRunCommandBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbarLayout.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        deviceId = getIntent().getStringExtra("deviceId");

        boolean canAddCommands = false;
        try {
            canAddCommands = BackgroundService.getInstance().getDevice(deviceId).getPlugin(RunCommandPlugin.class).canAddCommand();
        } catch (Exception ignore) {
        }

        if (canAddCommands) {
            binding.addCommandButton.show();
        } else {
            binding.addCommandButton.hide();
        }

        binding.addCommandButton.setOnClickListener(v -> BackgroundService.RunWithPlugin(RunCommandActivity.this, deviceId, RunCommandPlugin.class, plugin -> {
            plugin.sendSetupPacket();
             new AlertDialog.Builder(RunCommandActivity.this)
                    .setTitle(R.string.add_command)
                    .setMessage(R.string.add_command_description)
                    .setPositiveButton(R.string.ok, null)
                    .show();
        }));
        updateView();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.runcommand_context, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        if (item.getItemId() == R.id.copy_url_to_clipboard) {
            CommandEntry entry = (CommandEntry) commandItems.get(info.position);
            String url = "kdeconnect://runcommand/" + deviceId + "/" + entry.getKey();
            ClipboardManager cm = ContextCompat.getSystemService(this, ClipboardManager.class);
            cm.setText(url);
            Toast toast = Toast.makeText(this, R.string.clipboard_toast, Toast.LENGTH_SHORT);
            toast.show();
            return true;
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();

        BackgroundService.RunWithPlugin(this, deviceId, RunCommandPlugin.class, plugin -> plugin.addCommandsUpdatedCallback(commandsChangedCallback));
    }

    @Override
    protected void onPause() {
        super.onPause();

        BackgroundService.RunWithPlugin(this, deviceId, RunCommandPlugin.class, plugin -> plugin.removeCommandsUpdatedCallback(commandsChangedCallback));
    }

    @Override
    public boolean onSupportNavigateUp() {
        super.onBackPressed();
        return true;
    }
}
