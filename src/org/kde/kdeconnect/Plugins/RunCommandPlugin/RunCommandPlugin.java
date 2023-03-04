/*
 * SPDX-FileCopyrightText: 2015 Aleix Pol Gonzalez <aleixpol@kde.org>
 * SPDX-FileCopyrightText: 2015 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Plugins.RunCommandPlugin;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

@PluginFactory.LoadablePlugin
public class RunCommandPlugin extends Plugin {

    private final static String PACKET_TYPE_RUNCOMMAND = "kdeconnect.runcommand";
    private final static String PACKET_TYPE_RUNCOMMAND_REQUEST = "kdeconnect.runcommand.request";
    public final static String KEY_COMMANDS_PREFERENCE = "commands_preference_";

    private final ArrayList<JSONObject> commandList = new ArrayList<>();
    private final ArrayList<CommandsChangedCallback> callbacks = new ArrayList<>();
    private final ArrayList<CommandEntry> commandItems = new ArrayList<>();

    private SharedPreferences sharedPreferences;

    private boolean canAddCommand;

    public void addCommandsUpdatedCallback(CommandsChangedCallback newCallback) {
        callbacks.add(newCallback);
    }

    public void removeCommandsUpdatedCallback(CommandsChangedCallback theCallback) {
        callbacks.remove(theCallback);
    }

    interface CommandsChangedCallback {
        void update();
    }

    public ArrayList<JSONObject> getCommandList() {
        return commandList;
    }

    public ArrayList<CommandEntry> getCommandItems() {
        return commandItems;
    }

    @Override
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_runcommand);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_runcommand_desc);
    }

    @Override
    public Drawable getIcon() {
        return ContextCompat.getDrawable(context, R.drawable.run_command_plugin_icon_24dp);
    }

    @Override
    public boolean onCreate() {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.context);
        requestCommandList();
        return true;
    }

    @Override
    public boolean onPacketReceived(NetworkPacket np) {

        if (np.has("commandList")) {
            commandList.clear();
            try {
                commandItems.clear();
                JSONObject obj = new JSONObject(np.getString("commandList"));
                Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String s = keys.next();
                    JSONObject o = obj.getJSONObject(s);
                    o.put("key", s);
                    commandList.add(o);

                    try {
                        commandItems.add(
                                new CommandEntry(
                                        o.getString("name"),
                                        o.getString("command"),
                                        o.getString("key")
                                )
                        );
                    } catch (JSONException e) {
                        Log.e("RunCommand", "Error parsing JSON", e);
                    }
                }

                Collections.sort(commandItems, Comparator.comparing(CommandEntry::getName));

                // Used only by RunCommandControlsProviderService to display controls correctly even when device is not available
                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    JSONArray array = new JSONArray();

                    for (JSONObject command : commandList) {
                        array.put(command);
                    }

                    sharedPreferences.edit().putString(KEY_COMMANDS_PREFERENCE + device.getDeviceId(), array.toString()).apply();
                }

                Intent updateWidget = new Intent(context, RunCommandWidget.class);
                context.sendBroadcast(updateWidget);

            } catch (JSONException e) {
                Log.e("RunCommand", "Error parsing JSON", e);
            }

            for (CommandsChangedCallback aCallback : callbacks) {
                aCallback.update();
            }

            device.onPluginsChanged();

            canAddCommand = np.getBoolean("canAddCommand", false);

            return true;
        }
        return false;
    }

    @Override
    public String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_RUNCOMMAND};
    }

    @Override
    public String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_RUNCOMMAND_REQUEST};
    }

    public void runCommand(String cmdKey) {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_RUNCOMMAND_REQUEST);
        np.set("key", cmdKey);
        device.sendPacket(np);
    }

    private void requestCommandList() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_RUNCOMMAND_REQUEST);
        np.set("requestCommandList", true);
        device.sendPacket(np);
    }

    @Override
    public boolean hasMainActivity(Context context) {
        return true;
    }

    @Override
    public void startMainActivity(Activity parentActivity) {
        Intent intent = new Intent(parentActivity, RunCommandActivity.class);
        intent.putExtra("deviceId", device.getDeviceId());
        parentActivity.startActivity(intent);
    }

    @Override
    public String getActionName() {
        return context.getString(R.string.pref_plugin_runcommand);
    }

    public boolean canAddCommand(){
        return canAddCommand;
    }

    void sendSetupPacket() {
        NetworkPacket np = new NetworkPacket(RunCommandPlugin.PACKET_TYPE_RUNCOMMAND_REQUEST);
        np.set("setup", true);
        device.sendPacket(np);
    }

}
