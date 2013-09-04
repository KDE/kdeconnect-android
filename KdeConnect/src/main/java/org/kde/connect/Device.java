package org.kde.connect;

import android.R;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Base64;
import android.util.Log;

import org.kde.connect.ComputerLinks.BaseComputerLink;
import org.kde.connect.Plugins.Plugin;
import org.kde.connect.Plugins.PluginFactory;
import org.kde.connect.UserInterface.PairActivity;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class Device implements BaseComputerLink.PackageReceiver {

    private Context context;

    private String deviceId;
    private String name;
    private PublicKey publicKey;
    private int notificationId;

    private enum PairStatus {
        NotPaired,
        Requested,
        RequestedByPeer,
        Paired
    }

    public interface PairingCallback {
        abstract void incomingRequest();
        abstract void pairingSuccessful();
        abstract void pairingFailed(String error);
        abstract void unpaired();
    }

    private PairStatus pairStatus;
    private ArrayList<PairingCallback> pairingCallback = new ArrayList<PairingCallback>();
    private Timer pairingTimer;

    private ArrayList<BaseComputerLink> links = new ArrayList<BaseComputerLink>();
    private HashMap<String, Plugin> plugins = new HashMap<String, Plugin>();
    private HashMap<String, Plugin> failedPlugins = new HashMap<String, Plugin>();

    SharedPreferences settings;

    //Remembered trusted device, we need to wait for a incoming devicelink to communicate
    Device(Context context, String deviceId) {
        settings = context.getSharedPreferences(deviceId, Context.MODE_PRIVATE);

        //Log.e("Device","Constructor A");

        this.context = context;
        this.deviceId = deviceId;
        this.name = settings.getString("deviceName", "unknown device");
        this.pairStatus = PairStatus.Paired;

        try {
            byte[] publicKeyBytes = Base64.decode(settings.getString("publicKey", ""), 0);
            publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(publicKeyBytes));
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("Device","Exception");
        }

        reloadPluginsFromSettings();
    }

    //Device known via an incoming connection sent to us via a devicelink, we know everything but we don't trust it yet
    Device(Context context, String deviceId, String name, BaseComputerLink dl) {
        settings = context.getSharedPreferences(deviceId, Context.MODE_PRIVATE);

        //Log.e("Device","Constructor B");

        this.context = context;
        this.deviceId = deviceId;
        this.name = name;
        this.pairStatus = PairStatus.NotPaired;
        this.publicKey = null;

        addLink(dl);
    }


    public boolean hasName() {
        return name != null;
    }

    public String getName() {
        return name != null? name : "unknown device"; //TODO: i18n
    }

    public String getDeviceId() {
        return deviceId;
    }






    //
    // Pairing-related functions
    //

    public boolean isPaired() {
        return pairStatus == PairStatus.Paired;
    }

    public boolean isPairRequested() {
        return pairStatus == PairStatus.Requested;
    }

    public void addPairingCallback(PairingCallback callback) {
        pairingCallback.add(callback);
        if (pairStatus == PairStatus.RequestedByPeer) {
            callback.incomingRequest();
        }
    }
    public void removePairingCallback(PairingCallback callback) {
        pairingCallback.remove(callback);
    }

    public void requestPairing() {

        if (pairStatus == PairStatus.Paired) {
            for (PairingCallback cb : pairingCallback) cb.pairingFailed("Device already paired"); //TODO: i18n
            return;
        }
        if (pairStatus == PairStatus.Requested) {
            for (PairingCallback cb : pairingCallback) cb.pairingFailed("Pairing already requested"); //TODO: i18n
            return;
        }
        if (!isReachable()) {
            for (PairingCallback cb : pairingCallback) cb.pairingFailed("Device not reachable"); //TODO: i18n
            return;
        }

        //Send our own public key
        NetworkPackage np = NetworkPackage.createPublicKeyPackage(context);
        boolean success = sendPackage(np);

        if (!success) {
            for (PairingCallback cb : pairingCallback) cb.pairingFailed("Could not send package");
            return;
        }

        pairingTimer = new Timer();
        pairingTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                for (PairingCallback cb : pairingCallback) cb.pairingFailed("Timed out"); //TODO: i18n
                pairStatus = PairStatus.NotPaired;
            }
        }, 20*1000);

        pairStatus = PairStatus.Requested;

    }

    public int getNotificationId() {
        return notificationId;
    }

    public void unpair() {

        if (!isPaired()) return;

        pairStatus = PairStatus.NotPaired;

        SharedPreferences preferences = context.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE);
        preferences.edit().remove(deviceId).commit();

        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_PAIR);
        np.set("pair", false);
        sendPackage(np);

        for (PairingCallback cb : pairingCallback) cb.unpaired();

        reloadPluginsFromSettings();

    }

    public void acceptPairing() {

        Log.e("Device","Accepted pairing");

        //Send our own public key
        NetworkPackage np = NetworkPackage.createPublicKeyPackage(context);
        boolean success = sendPackage(np);

        if (!success) return;

        pairStatus = PairStatus.Paired;

        //Store as trusted device
        String encodedPublicKey  = Base64.encodeToString(publicKey.getEncoded(), 0);
        SharedPreferences preferences = context.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE);
        preferences.edit().putBoolean(deviceId,true).commit();

        //Store device information needed to create a Device object in a future
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("deviceName", getName());
        editor.putString("publicKey", encodedPublicKey);
        editor.commit();

        reloadPluginsFromSettings();

        for (PairingCallback cb : pairingCallback) cb.pairingSuccessful();

    }

    public void rejectPairing() {

        Log.e("Device","Rejected pairing");

        pairStatus = PairStatus.NotPaired;

        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_PAIR);
        np.set("pair", false);
        sendPackage(np);

        for (PairingCallback cb : pairingCallback) cb.pairingFailed("Canceled by the user"); //TODO: i18n

    }




    //
    // ComputerLink-related functions
    //

    public boolean isReachable() {
        return !links.isEmpty();
    }

    public void addLink(BaseComputerLink link) {

        links.add(link);

        Log.e("Device","addLink "+link.getLinkProvider().getName()+" -> "+getName() + " active links: "+ links.size());

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
        Log.e("Device","removeLink: "+link.getLinkProvider().getName() + " -> "+getName() + " active links: "+ links.size());
        if (links.isEmpty()) {
            reloadPluginsFromSettings();
        }
    }

    @Override
    public void onPackageReceived(NetworkPackage np) {

        if (np.getType().equals(NetworkPackage.PACKAGE_TYPE_PAIR)) {

            Log.e("Device","Pair package");

            boolean wantsPair = np.getBoolean("pair");

            if (wantsPair == isPaired()) {
                if (pairStatus == PairStatus.Requested) {
                    pairStatus = PairStatus.NotPaired;
                    pairingTimer.cancel();
                    for (PairingCallback cb : pairingCallback) cb.pairingFailed("Canceled by other peer"); //TODO: i18n
                }
                return;
            }

            if (wantsPair) {

                //Retrieve their public key
                try {
                    String publicKeyContent = np.getString("publicKey").replace("-----BEGIN PUBLIC KEY-----\n","").replace("-----END PUBLIC KEY-----\n","");
                    byte[] publicKeyBytes = Base64.decode(publicKeyContent, 0);
                    Log.e("asdasd","key bytes: " + publicKeyBytes);
                    publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(publicKeyBytes));
                } catch(Exception e) {
                    e.printStackTrace();
                    Log.e("Device","Pairing exception: Received incorrect key");
                    for (PairingCallback cb : pairingCallback) cb.pairingFailed("Incorrect key received"); //TODO: i18n
                    return;
                }

                if (pairStatus == PairStatus.Requested)  { //We started pairing

                    Log.e("Pairing","Pair answer");

                    pairStatus = PairStatus.Paired;
                    pairingTimer.cancel();

                    //Store as trusted device
                    String encodedPublicKey  = Base64.encodeToString(publicKey.getEncoded(), 0);
                    SharedPreferences preferences = context.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE);
                    preferences.edit().putBoolean(deviceId,true).commit();

                    //Store device information needed to create a Device object in a future
                    SharedPreferences.Editor editor = settings.edit();
                    editor.putString("deviceName", getName());
                    editor.putString("publicKey", encodedPublicKey);
                    editor.commit();

                    reloadPluginsFromSettings();

                    for (PairingCallback cb : pairingCallback) cb.pairingSuccessful();

                } else {

                    Log.e("Pairing","Pair request");

                    Intent intent = new Intent(context, PairActivity.class);
                    intent.putExtra("deviceId", deviceId);
                    PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT);

                    Notification noti = new NotificationCompat.Builder(context)
                            .setContentTitle("Pairing request from" + getName()) //TODO: i18n
                            .setContentText("Tap to answer") //TODO: i18n
                            .setContentIntent(pendingIntent)
                            .setTicker("Pair requested") //TODO: i18n
                            .setSmallIcon(R.drawable.ic_menu_help)
                            .setAutoCancel(true)
                            .setDefaults(Notification.DEFAULT_SOUND)
                            .build();


                    final NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                    notificationId = (int)System.currentTimeMillis();
                    notificationManager.notify(notificationId, noti);

                    pairingTimer = new Timer();
                    pairingTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            pairStatus = PairStatus.NotPaired;
                            notificationManager.cancel(notificationId);
                        }
                    }, 19*1000); //Time to show notification

                    pairStatus = PairStatus.RequestedByPeer;
                    for (PairingCallback cb : pairingCallback) cb.incomingRequest();

                }
            } else {
                Log.e("Pairing","Unpair request");

                if (pairStatus == PairStatus.Requested) {
                    pairingTimer.cancel();
                    for (PairingCallback cb : pairingCallback) cb.pairingFailed("Canceled by other peer"); //TODO: i18n
                } else if (pairStatus == PairStatus.Paired) {
                    SharedPreferences preferences = context.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE);
                    preferences.edit().remove(deviceId).commit();
                    reloadPluginsFromSettings();
                }

                pairStatus = PairStatus.NotPaired;
                for (PairingCallback cb : pairingCallback) cb.unpaired();

            }
        } else if (!isPaired()) {

            //TODO: Notify the other side that we don't trust them
            Log.e("onPackageReceived","Device not paired, ignoring package!");

        } else {
            if (np.getType().equals(NetworkPackage.PACKAGE_TYPE_ENCRYPTED)) {

                try {
                    //TODO: Do not read the key every time
                    SharedPreferences globalSettings = PreferenceManager.getDefaultSharedPreferences(context);
                    byte[] privateKeyBytes = Base64.decode(globalSettings.getString("privateKey",""), 0);
                    PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
                    np = np.decrypt(privateKey);
                } catch(Exception e) {
                    e.printStackTrace();
                    Log.e("onPackageReceived","Exception reading the key needed to decrypt the package");
                }

            } else {
                //TODO: The other side doesn't know that we are already paired, do something
                Log.e("onPackageReceived","WARNING: Received unencrypted package from paired device!");
            }

            for (Plugin plugin : plugins.values()) {
                plugin.onPackageReceived(np);
            }
        }

    }


    public boolean sendPackage(final NetworkPackage np) {

        if (!np.getType().equals(NetworkPackage.PACKAGE_TYPE_PAIR) && isPaired()) {
            try {
                np.encrypt(publicKey);
            } catch(Exception e) {
                e.printStackTrace();
                Log.e("Device","sendPackage exception - could not encrypt");
            }
        }

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
                Log.e("sendPackage","Error: Package could not be sent ("+links.size()+" links available)");
                return null;
            }
        }.execute();

        //TODO: Detect when unable to send a package and try again somehow

        return !links.isEmpty();
    }





    //
    // Plugin-related functions
    //

    public Plugin getPlugin(String name) {
        return plugins.get(name);
    }

    private void addPlugin(final String name) {
        Plugin existing = plugins.get(name);
        if (existing != null) {
            Log.e("addPlugin","plugin already present:" + name);
            return;
        }

        final Plugin plugin = PluginFactory.instantiatePluginForDevice(context, name, this);
        if (plugin == null) {
            Log.e("addPlugin","could not instantiate plugin: "+name);
            failedPlugins.put(name, plugin);
            return;
        }

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {

                try {
                    boolean success = plugin.onCreate();
                    if (!success) {
                        Log.e("addPlugin", "plugin failed to load " + name);
                        failedPlugins.put(name, plugin);
                        return;
                    }
                } catch (Exception e) {
                    failedPlugins.put(name, plugin);
                    e.printStackTrace();
                    Log.e("addPlugin", "Exception loading plugin " + name);
                    return;
                }

                //Log.e("addPlugin","added " + name);

                failedPlugins.remove(name);
                plugins.put(name, plugin);

                for (PluginsChangedListener listener : pluginsChangedListeners) {
                    listener.onPluginsChanged(Device.this);
                }

            }
        });

    }

    private boolean removePlugin(String name) {

        Plugin plugin = plugins.remove(name);
        Plugin failedPlugin = failedPlugins.remove(name);

        if (plugin == null) {
            if (failedPlugin == null) {
                return false;
            }
            plugin = failedPlugin;
        }

        try {
            plugin.onDestroy();
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("removePlugin","Exception calling onDestroy for plugin "+name);
            return false;
        }

        //Log.e("removePlugin","removed " + name);

        for (PluginsChangedListener listener : pluginsChangedListeners) {
            listener.onPluginsChanged(this);
        }

        return true;

    }

    public void setPluginEnabled(String pluginName, boolean value) {
        settings.edit().putBoolean(pluginName,value).commit();
        if (value) addPlugin(pluginName);
        else removePlugin(pluginName);
    }

    public boolean isPluginEnabled(String pluginName) {
        boolean enabledByDefault = PluginFactory.getPluginInfo(context, pluginName).isEnabledByDefault();
        boolean enabled = settings.getBoolean(pluginName, enabledByDefault);
        return enabled;
    }


    public void reloadPluginsFromSettings() {

        failedPlugins.clear();

        Set<String> availablePlugins = PluginFactory.getAvailablePlugins();

        for(String pluginName : availablePlugins) {
            boolean enabled = false;
            if (isPaired() && isReachable()) {
                enabled = isPluginEnabled(pluginName);
            }
            //Log.e("reloadPluginsFromSettings",pluginName+"->"+enabled);
            if (enabled) {
                addPlugin(pluginName);
            } else {
                removePlugin(pluginName);
            }
        }

        for (PluginsChangedListener listener : pluginsChangedListeners) {
            listener.onPluginsChanged(this);
        }
    }

    public HashMap<String,Plugin> getLoadedPlugins() {
        return plugins;
    }

    public HashMap<String,Plugin> getFailedPlugins() {
        return failedPlugins;
    }

    public interface PluginsChangedListener {
        void onPluginsChanged(Device device);
    }

    private ArrayList<PluginsChangedListener> pluginsChangedListeners = new ArrayList<PluginsChangedListener>();

    public void addPluginsChangedListener(PluginsChangedListener listener) {
        pluginsChangedListeners.add(listener);
    }

    public void removePluginsChangedListener(PluginsChangedListener listener) {
        pluginsChangedListeners.remove(listener);
    }

}
