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

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.util.Log;

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

@PluginFactory.LoadablePlugin
public class RunCommandPlugin extends Plugin {

    private final static String PACKET_TYPE_RUNCOMMAND = "kdeconnect.runcommand";
    private final static String PACKET_TYPE_RUNCOMMAND_REQUEST = "kdeconnect.runcommand.request";

    private final ArrayList<JSONObject> commandList = new ArrayList<>();
    private final ArrayList<CommandsChangedCallback> callbacks = new ArrayList<>();
    private final ArrayList<CommandEntry> commandItems = new ArrayList<>();

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
    public boolean hasMainActivity() {
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
