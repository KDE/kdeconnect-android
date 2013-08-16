package org.kde.connect;


import android.content.Context;
import android.os.Build;
import android.util.Log;

import org.kde.connect.Plugins.BatteryPlugin;
import org.kde.connect.Plugins.ClipboardPlugin;
import org.kde.connect.Plugins.MprisPlugin;
import org.kde.connect.Plugins.PingPlugin;
import org.kde.connect.Plugins.Plugin;
import org.kde.connect.Plugins.TelephonyPlugin;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class PluginFactory {

    private static final Map<String, Class> availablePlugins = new TreeMap<String, Class>();
    static {
        //Note that settings to enable the plugins must have tha same names as keys than here
        availablePlugins.put("plugin_battery", BatteryPlugin.class);
        availablePlugins.put("plugin_clipboard", ClipboardPlugin.class);
        availablePlugins.put("plugin_mpris", MprisPlugin.class);
        availablePlugins.put("plugin_ping", PingPlugin.class);
        availablePlugins.put("plugin_telephony", TelephonyPlugin.class);
    }

    public static Set<String> getAvailablePlugins() {
        return availablePlugins.keySet();
    }

    public static boolean isPluginEnabledByDefault(String pluginName) {

        if (pluginName.equals("plugin_clibpoard"))
            return (Build.VERSION.SDK_INT > 10 && Build.VERSION.SDK_INT != 18);
        else
            return true;

    }

    public static Plugin instantiatePluginForDevice(Context context, String name, Device device) {
        Class c = availablePlugins.get(name);
        if (c == null) {
            Log.e("PluginFactory", "Plugin not found: "+name);
            return null;
        }

        try {
            Plugin plugin = (Plugin)c.newInstance();
            plugin.setContext(context, device);
            return plugin;
        } catch(Exception e) {
            e.printStackTrace();
            Log.e("PluginFactory", "Could not instantiate plugin: "+name);
            return null;
        }

    }

}
