package org.kde.kdeconnect.Plugins.PowerPlugin;

import java.util.HashMap;
import java.util.concurrent.Flow;

public class PowerPluginsSingleton {

    HashMap<String, PowerPlugin> availableDevices = new HashMap<>();


    static public PowerPluginsSingleton getInstance() {
        if (instance == null) {
            instance = new PowerPluginsSingleton();
        }
        return instance;
    }

    private static PowerPluginsSingleton instance = null;
    private PowerPluginsSingleton() { }


}
