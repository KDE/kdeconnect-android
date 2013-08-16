package org.kde.connect;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

import org.kde.connect.ComputerLinks.BaseComputerLink;
import org.kde.connect.Plugins.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class Device implements BaseComputerLink.PackageReceiver {

    private ArrayList<BaseComputerLink> links = new ArrayList<BaseComputerLink>();
    private HashMap<String, Plugin> plugins = new HashMap<String, Plugin>();

    private Context context;
    private String deviceId;
    private String name;
    private boolean trusted;

    SharedPreferences settings;

    //Remembered trusted device, we need to wait for a incoming devicelink to communicate
    Device(Context context, String deviceId) {
        settings = context.getSharedPreferences(deviceId,Context.MODE_PRIVATE);

        //Log.e("Device","Constructor A");

        this.context = context;
        this.deviceId = deviceId;
        this.name = settings.getString("deviceName",null);
        this.trusted = true;

        reloadPluginsFromSettings();
    }

    //Device known via an incoming connection sent to us via a devicelink, we know everything but we don't trust it yet
    Device(Context context, String deviceId, String name, BaseComputerLink dl) {
        settings = context.getSharedPreferences(deviceId,Context.MODE_PRIVATE);

        //Log.e("Device","Constructor B");

        this.context = context;
        this.deviceId = deviceId;
        setName(name);
        setTrusted(false);

        addLink(dl);
    }


    public boolean hasName() {
        return name != null;
    }

    public String getName() {
        return name != null? name : "unknown device";
    }

    public void setName(String name) {
        this.name = name;
        settings.edit().putString("deviceName",name).commit();
    }

    public String getDeviceId() {
        return deviceId;
    }

    public boolean isReachable() {
        return !links.isEmpty();
    }

    public boolean isTrusted() {
        return trusted;
    }

    public void setTrusted(boolean b) {
        trusted = b;

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> trustedDevices = preferences.getStringSet("trusted", new HashSet<String>());

        boolean wasTrusted = trustedDevices.contains(deviceId);

        if (trusted != wasTrusted) {
            if (trusted) {
                //Log.e("trust",deviceId);
                trustedDevices.add(deviceId);
            } else {
                //Log.e("untrust",deviceId);
                trustedDevices.remove(deviceId);
            }
            SharedPreferences.Editor editor = preferences.edit();
            editor.putStringSet("trusted",trustedDevices);
            editor.commit();

        }

        reloadPluginsFromSettings();

    }




    //
    // Computer link-related functions
    //

    public void addLink(BaseComputerLink link) {
        Log.e("Device","addLink "+link.getLinkProvider().getName()+" -> "+getName());

        links.add(link);

        Collections.sort(links, new Comparator<BaseComputerLink>() {
            @Override
            public int compare(BaseComputerLink o, BaseComputerLink o2) {
                return o2.getLinkProvider().getPriority() - o.getLinkProvider().getPriority();
            }
        });

        link.addPackageReceiver(this);

        if (links.size() == 1) {
            reloadPluginsFromSettings();
        }
    }

    public void removeLink(BaseComputerLink link) {
        link.removePackageReceiver(this);
        links.remove(link);
        if (links.isEmpty()) {
            reloadPluginsFromSettings();
        }
    }

    @Override
    public void onPackageReceived(NetworkPackage np) {
        for (Plugin plugin : plugins.values()) {
            //Log.e("onPackageReceived",plugin.toString());
            plugin.onPackageReceived(np);
        }
    }


    public boolean sendPackage(final NetworkPackage np) {
        Log.e("Device", "sendPackage "+np.getType()+". "+links.size()+" links available");
        new AsyncTask<Void,Void,Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                //Log.e("sendPackage","Do in background");
                for(BaseComputerLink link : links) {
                    //Log.e("sendPackage","Trying "+link.getLinkProvider().getName());
                    if (link.sendPackage(np)) {
                        //Log.e("sent using", link.getLinkProvider().getName());
                        return null;
                    }
                }
                Log.e("sendPackage","Error: Package could not be sent");
                return null;
            }
        }.execute();

        return true; //FIXME: Detect when unable to send a package and try again somehow
    }





    //
    // Plugin-related functions
    //

    public Plugin getPlugin(String name) {
        return plugins.get(name);
    }

    public Plugin addPlugin(String name) {
        Plugin existing = plugins.get(name);
        if (existing != null) {
            Log.e("addPlugin","plugin already present:" + name);
            return existing;
        }

        Plugin plugin = PluginFactory.instantiatePluginForDevice(context, name, this);
        if (plugin == null) {
            Log.e("addPlugin","could not create plugin: "+name);
            return null;
        }

        try {
            plugin.onCreate();
        } catch (Exception e) {
            Log.e("addPlugin","Exception calling onCreate for "+name);
            e.printStackTrace();
            return null;
        }

        Log.e("addPlugin",name);

        plugins.put(name, plugin);
        return plugin;
    }

    public boolean removePlugin(String name) {
        Plugin plugin = plugins.remove(name);
        if (plugin == null) {
            return false;
        }

        try {
            plugin.onDestroy();
        } catch (Exception e) {
            Log.e("addPlugin","Exception calling onCreate for "+name);
            e.printStackTrace();
            return false;
        }

        Log.e("removePlugin",name);

        return true;
    }

    public void setPluginEnabled(String key, boolean value) {
        settings.edit().putBoolean(key,value).commit();
        if (value) addPlugin(key);
        else removePlugin(key);
    }

    public void reloadPluginsFromSettings() {

        Set<String> availablePlugins = PluginFactory.getAvailablePlugins();

        for(String pluginName : availablePlugins) {
            boolean enabled = false;
            if (isTrusted() && isReachable()) {
                boolean enabledByDefault = PluginFactory.isPluginEnabledByDefault(pluginName);
                enabled = settings.getBoolean(pluginName, enabledByDefault);
            }
            //Log.e("reloadPluginsFromSettings",pluginName+"->"+enabled);
            if (enabled) {
                addPlugin(pluginName);
            } else {
                removePlugin(pluginName);
            }
        }

    }

    public void readPluginPreferences(SharedPreferences outSettings) {
        SharedPreferences.Editor editor = outSettings.edit();

        Set<String> availablePlugins = PluginFactory.getAvailablePlugins();
        for(String pluginName : availablePlugins) {
            boolean enabledByDefault = PluginFactory.isPluginEnabledByDefault(pluginName);
            boolean enabled = settings.getBoolean(pluginName, enabledByDefault);
            editor.putBoolean(pluginName, enabled);
            //Log.e("readPluginPreferences",pluginName+"->"+enabled);
        }

        editor.commit();
    }

}
