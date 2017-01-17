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
import android.support.v4.content.ContextCompat;

import org.json.JSONException;
import org.json.JSONObject;
import org.kde.kdeconnect.NetworkPackage;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;
import java.util.Iterator;

public class RunCommandPlugin extends Plugin {

    public final static String PACKAGE_TYPE_RUNCOMMAND = "kdeconnect.runcommand";
    public final static String PACKAGE_TYPE_RUNCOMMAND_REQUEST = "kdeconnect.runcommand.request";

    private ArrayList<JSONObject> commandList = new ArrayList<>();
    private ArrayList<CommandsChangedCallback> callbacks = new ArrayList<>();

    public void addCommandsUpdatedCallback(CommandsChangedCallback newCallback) {
        callbacks.add(newCallback);
    }

    public void removeCommandsUpdatedCallback(CommandsChangedCallback theCallback) {
        callbacks.remove(theCallback);
    }

    interface CommandsChangedCallback {
        void update();
    };

    public ArrayList<JSONObject> getCommandList() {
        return commandList;
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
        return ContextCompat.getDrawable(context, R.drawable.runcommand_plugin_icon);
    }

    @Override
    public boolean onCreate() {
        requestCommandList();
        return true;
    }

    @Override
    public boolean onPackageReceived(NetworkPackage np) {

        if (np.has("commandList")) {
            commandList.clear();
            try {
                JSONObject obj = new JSONObject(np.getString("commandList"));
                Iterator<String> keys = obj.keys();
                while(keys.hasNext()){
                    String s = keys.next();
                    JSONObject o = obj.getJSONObject(s);
                    o.put("key", s);
                    commandList.add(o);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

            for (CommandsChangedCallback aCallback : callbacks) {
                aCallback.update();
            }

            device.onPluginsChanged();

            return true;
        }
        return false;
    }

    @Override
    public String[] getSupportedPackageTypes() {
        return new String[]{PACKAGE_TYPE_RUNCOMMAND};
    }

    @Override
    public String[] getOutgoingPackageTypes() {
        return new String[]{PACKAGE_TYPE_RUNCOMMAND_REQUEST};
    }

    public void runCommand(String cmdKey) {
        NetworkPackage np = new NetworkPackage(PACKAGE_TYPE_RUNCOMMAND_REQUEST);
        np.set("key", cmdKey);
        device.sendPackage(np);
    }

    private void requestCommandList() {
        NetworkPackage np = new NetworkPackage(PACKAGE_TYPE_RUNCOMMAND_REQUEST);
        np.set("requestCommandList", true);
        device.sendPackage(np);
    }

    @Override
    public boolean hasMainActivity() {
        return !commandList.isEmpty();
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

}
