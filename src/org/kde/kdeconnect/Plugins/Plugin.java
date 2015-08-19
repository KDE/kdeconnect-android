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
import android.view.View;
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
     * To receive the network package from the unpaired device, Register your plugin
     * using Device::registerUnpairedPackageListener and override this method.
     */
    public boolean onUnpairedDevicePackageReceived(NetworkPackage np) {
        return false;
    }

    /**
     * Return the internal plugin name, that will be used as a
     * unique key to distinguish it. Use the class name as key.
     */
    public String getPluginKey() {
        return getPluginKey(this.getClass());
    }
    public static String getPluginKey(Class<? extends Plugin> p) {
        return p.getSimpleName();
    }

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
     * Return the action name displayed in the main activity, that
     * will call startMainActivity when clicked
     */
    public String getActionName() {
        return getDisplayName();
    }

    /**
     * Return an icon associated to this plugin. This function can
     * access this.context to load the image from resources.
     */
    public Drawable getIcon() {
        return null;
    }

    /**
     * Return true if this plugin should be enabled on new devices.
     * This function can access this.context and perform compatibility
     * checks with the Android version, but can not access this.device.
     */
    public boolean isEnabledByDefault() {
        return true;
    }

    /**
     * Return true if this plugin needs an specific UI settings.
     */
    public boolean hasSettings() {
        return false;
    }

    /**
     * If hasSettings returns true, this will be called when the user
     * wants to access this plugin preferences and should launch some
     * kind of interface. The default implementation will launch a
     * SettingsActivity with content from "yourplugin"_preferences.xml.
     */
    public void startPreferencesActivity(SettingsActivity parentActivity) {
        Intent intent = new Intent(parentActivity, PluginSettingsActivity.class);
        intent.putExtra("plugin_display_name", getDisplayName());
        intent.putExtra("plugin_key", getPluginKey());
        parentActivity.startActivity(intent);
    }

    /**
     * Return true if the plugin should display something in the Device main view
     */
    public boolean hasMainActivity() {
        return false;
    }

    /**
     * Implement here what your plugin should do when clicked
     */
    public void startMainActivity(Activity parentActivity) { }

    /**
     * Return true if the entry for this app should appear in the context menu instead of the main view
     */
    public boolean displayInContextMenu() {
        return false;
    }

    /**
     * Initialize the listeners and structures in your plugin.
     * Should return true if initialization was successful.
     */
    public boolean onCreate() {
        return true;
    }

    /**
     * Finish any ongoing operations, remove listeners... so
     * this object could be garbage collected.
     */
    public void onDestroy() { }

    /**
     * If onCreate returns false, should create a dialog explaining
     * the problem (and how to fix it, if possible) to the user.
     */
    public boolean onPackageReceived(NetworkPackage np) { return false; }

    /**
     * If onCreate returns false, should create a dialog explaining
     * the problem (and how to fix it, if possible) to the user.
     */
    public AlertDialog getErrorDialog(Activity deviceActivity) {
        return null;
    }

    /**
     * Creates a button that will be displayed in the user interface
     * It can open an activity or perform any other action that the
     * plugin would wants to expose to the user. Return null if no
     * button should be displayed.
     */
    @Deprecated
    public Button getInterfaceButton(final Activity activity) {
        if (!hasMainActivity()) return null;
        Button b = new Button(activity);
        b.setText(getActionName());
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startMainActivity(activity);
            }
        });
        return b;
    }

}
