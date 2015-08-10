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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Plugins.BatteryPlugin.BatteryPlugin;
import org.kde.kdeconnect.Plugins.MousePadPlugin.MousePadPlugin;
import org.kde.kdeconnect.Plugins.SftpPlugin.SftpPlugin;
import org.kde.kdeconnect.Plugins.ClibpoardPlugin.ClipboardPlugin;
import org.kde.kdeconnect.Plugins.MprisPlugin.MprisPlugin;
import org.kde.kdeconnect.Plugins.NotificationsPlugin.NotificationsPlugin;
import org.kde.kdeconnect.Plugins.PingPlugin.PingPlugin;
import org.kde.kdeconnect.Plugins.SharePlugin.SharePlugin;
import org.kde.kdeconnect.Plugins.TelephonyPlugin.TelephonyPlugin;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class PluginFactory {

    public static class PluginInfo {

        public PluginInfo(String displayName, String description, Drawable icon,
                          boolean enabledByDefault, boolean hasSettings) {
            this.displayName = displayName;
            this.description = description;
            this.icon = icon;
            this.enabledByDefault = enabledByDefault;
            this.hasSettings = hasSettings;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }

        public Drawable getIcon() {
            return icon;
        }

        public boolean hasSettings() { return hasSettings; }

        public boolean isEnabledByDefault() {
            return enabledByDefault;
        }

        private final String displayName;
        private final String description;
        private final Drawable icon;
        private final boolean enabledByDefault;
        private final boolean hasSettings;

    }

    private static final Map<String, Class> availablePlugins = new TreeMap<>();
    private static final Map<String, PluginInfo> availablePluginsInfo = new TreeMap<>();

    static {
        //TODO: Use reflection to find all subclasses of Plugin, instead of adding them manually
        PluginFactory.registerPlugin(TelephonyPlugin.class);
        PluginFactory.registerPlugin(PingPlugin.class);
        PluginFactory.registerPlugin(MprisPlugin.class);
        PluginFactory.registerPlugin(ClipboardPlugin.class);
        PluginFactory.registerPlugin(BatteryPlugin.class);
        PluginFactory.registerPlugin(SftpPlugin.class);
        PluginFactory.registerPlugin(NotificationsPlugin.class);
        PluginFactory.registerPlugin(MousePadPlugin.class);
        PluginFactory.registerPlugin(SharePlugin.class);
    }

    public static PluginInfo getPluginInfo(Context context, String pluginKey) {
        PluginInfo info = availablePluginsInfo.get(pluginKey); //Is it cached?
        if (info != null) return info;
        try {
            Plugin p = ((Plugin)availablePlugins.get(pluginKey).newInstance());
            p.setContext(context, null);
            info = new PluginInfo(p.getDisplayName(), p.getDescription(), p.getIcon(),
                    p.isEnabledByDefault(), p.hasSettings());
            availablePluginsInfo.put(pluginKey, info); //Cache it
            return info;
        } catch(Exception e) {
            Log.e("PluginFactory","getPluginInfo exception");
            e.printStackTrace();
            return null;
        }
    }

    public static Set<String> getAvailablePlugins() {
        return availablePlugins.keySet();
    }

    public static Plugin instantiatePluginForDevice(Context context, String pluginKey, Device device) {
        Class c = availablePlugins.get(pluginKey);
        if (c == null) {
            Log.e("PluginFactory", "Plugin not found: "+pluginKey);
            return null;
        }

        try {
            Plugin plugin = (Plugin)c.newInstance();
            plugin.setContext(context, device);
            return plugin;
        } catch(Exception e) {
            Log.e("PluginFactory", "Could not instantiate plugin: "+pluginKey);
            e.printStackTrace();
            return null;
        }

    }

    public static void registerPlugin(Class<? extends Plugin> pluginClass) {
        try {
            String pluginKey = Plugin.getPluginKey(pluginClass);
            availablePlugins.put(pluginKey, pluginClass);
        } catch(Exception e) {
            Log.e("PluginFactory","addPlugin exception");
            e.printStackTrace();
        }
    }

}
