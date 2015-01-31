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

package org.kde.kdeconnect.Plugins;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.widget.Button;

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.NetworkPackage;
import org.kde.kdeconnect.UserInterface.PluginSettingsActivity;
import org.kde.kdeconnect.UserInterface.SettingsActivity;

public abstract class Plugin {

    protected Device device;
    protected Context context;

    public final void setContext(Context context, Device device) {
        this.device = device;
        this.context = context;
    }

    /**
     * Return the internal plugin name, that will be used as a
     * unique key to distinguish it. This function can not access
     * this.context nor this.device.
     */
    public abstract String getPluginName();

    /**
     * Return the human-readable plugin name. This function can
     * access this.context to provide translated text.
     */
    public abstract String getDisplayName();

    /**
     * Return the human-readable description of this plugin. This
     * function can access this.context to provide translated text.
     */
    public abstract String getDescription();

    /**
     * Return an icon associated to this plugin. This function can
     * access this.context to load the image from resources.
     */
    public abstract Drawable getIcon();

    /**
     * Return true if this plugin should be enabled on new devices.
     * This function can access this.context and perform compatibility
     * checks with the Android version, but can not access this.device.
     */
    public abstract boolean isEnabledByDefault();

    /**
     * Return true if this plugin needs an specific UI settings.
     */
    public abstract boolean hasSettings();

    /**
     * If hasSettings returns true, this will be called when the user
     * wants to access this plugin preferences and should launch some
     * kind of interface. The default implementation will launch a
     * SettingsActivity with content from "yourplugin"_preferences.xml.
     */
    public void startPreferencesActivity(SettingsActivity parentActivity) {
        Intent intent = new Intent(parentActivity, PluginSettingsActivity.class);
        intent.putExtra("plugin_display_name", getDisplayName());
        intent.putExtra("plugin_name", getPluginName());
        parentActivity.startActivity(intent);
    }

    /**
     * Initialize the listeners and structures in your plugin.
     * Should return true if initialization was successful.
     */
    public abstract boolean onCreate();

    /**
     * Finish any ongoing operations, remove listeners... so
     * this object could be garbage collected.
     */
    public abstract void onDestroy();

    /**
     * If onCreate returns false, should create a dialog explaining
     * the problem (and how to fix it, if possible) to the user.
     */
    public abstract boolean onPackageReceived(NetworkPackage np);

    /**
     * If onCreate returns false, should create a dialog explaining
     * the problem (and how to fix it, if possible) to the user.
     */
    public abstract AlertDialog getErrorDialog(Activity deviceActivity);

    /**
     * Creates a button that will be displayed in the user interface
     * It can open an activity or perform any other action that the
     * plugin would wants to expose to the user. Return null if no
     * button should be displayed.
     */
    public abstract Button getInterfaceButton(Activity activity);


}
