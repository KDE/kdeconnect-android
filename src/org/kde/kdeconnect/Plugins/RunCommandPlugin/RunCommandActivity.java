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

import android.content.ClipboardManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
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

    @RequiresApi(api = Build.VERSION_CODES.HONEYCOMB)
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
}
