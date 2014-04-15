package org.kde.kdeconnect;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Base64;
import android.util.Log;

import org.kde.kdeconnect.Backends.BaseLink;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect.UserInterface.PairActivity;
import org.kde.kdeconnect_tp.R;

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

public class Device implements BaseLink.PackageReceiver {

    private final Context context;

    private final String deviceId;
    private final String name;
    public PublicKey publicKey;
    private int notificationId;
    private int protocolVersion;

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

    private final ArrayList<BaseLink> links = new ArrayList<BaseLink>();
    private final HashMap<String, Plugin> plugins = new HashMap<String, Plugin>();
    private final HashMap<String, Plugin> failedPlugins = new HashMap<String, Plugin>();

    private final SharedPreferences settings;

    //Remembered trusted device, we need to wait for a incoming devicelink to communicate
    Device(Context context, String deviceId) {
        settings = context.getSharedPreferences(deviceId, Context.MODE_PRIVATE);

        //Log.e("Device","Constructor A");

        this.context = context;
        this.deviceId = deviceId;
        this.name = settings.getString("deviceName", "unknown device");
        this.pairStatus = PairStatus.Paired;
        this.protocolVersion = NetworkPackage.ProtocolVersion; //We don't know it yet

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
    Device(Context context, NetworkPackage np, BaseLink dl) {

        //Log.e("Device","Constructor B");

        this.context = context;
        this.deviceId = np.getString("deviceId");
        this.name = np.getString("deviceName", "unidentified device");
        this.protocolVersion = np.getInt("protocolVersion");
        this.pairStatus = PairStatus.NotPaired;
        this.publicKey = null;

        settings = context.getSharedPreferences(deviceId, Context.MODE_PRIVATE);

        addLink(np, dl);
    }

    public String getName() {
        return name != null? name : context.getString(R.string.unknown_device);
    }

    public String getDeviceId() {
        return deviceId;
    }

    //Returns 0 if the version matches, < 0 if it is older or > 0 if it is newer
    public int compareProtocolVersion() {
        return protocolVersion - NetworkPackage.ProtocolVersion;
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

        Resources res = context.getResources();

        if (pairStatus == PairStatus.Paired) {
            for (PairingCallback cb : pairingCallback) {
                cb.pairingFailed(res.getString(R.string.error_already_paired));
            }
            return;
        }
        if (pairStatus == PairStatus.Requested) {
            for (PairingCallback cb : pairingCallback) {
                cb.pairingFailed(res.getString(R.string.error_already_requested));
            }
            return;
        }
        if (!isReachable()) {
            for (PairingCallback cb : pairingCallback) {
                cb.pairingFailed(res.getString(R.string.error_not_reachable));
            }
            return;
        }

        //Send our own public key
        NetworkPackage np = NetworkPackage.createPublicKeyPackage(context);
        sendPackage(np, new SendPackageFinishedCallback(){

            @Override
            public void sendSuccessful() {
                if (pairingTimer != null) pairingTimer.cancel();
                pairingTimer = new Timer();
                pairingTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        for (PairingCallback cb : pairingCallback) {
                            cb.pairingFailed(context.getString(R.string.error_timed_out));
                        }
                        Log.e("Device","Unpairing (timeout A)");
                        pairStatus = PairStatus.NotPaired;
                    }
                }, 30*1000); //Time to wait for the other to accept
                pairStatus = PairStatus.Requested;
            }

            @Override
            public void sendFailed() {
                for (PairingCallback cb : pairingCallback) {
                    cb.pairingFailed(context.getString(R.string.error_could_not_send_package));
                }
                Log.e("Device","Unpairing (sendFailed A)");
                pairStatus = PairStatus.NotPaired;
            }

        });

    }

    public int getNotificationId() {
        return notificationId;
    }

    public void unpair() {

        if (!isPaired()) return;

        //Log.e("Device","Unpairing (unpair)");
        pairStatus = PairStatus.NotPaired;

        SharedPreferences preferences = context.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE);
        preferences.edit().remove(deviceId).commit();

        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_PAIR);
        np.set("pair", false);
        sendPackage(np);

        for (PairingCallback cb : pairingCallback) cb.unpaired();

        reloadPluginsFromSettings();

    }

    private void pairingDone() {

        //Log.e("Device", "Storing as trusted, deviceId: "+deviceId);

        if (pairingTimer != null) pairingTimer.cancel();

        pairStatus = PairStatus.Paired;

        //Store as trusted device
        SharedPreferences preferences = context.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE);
        preferences.edit().putBoolean(deviceId,true).commit();

        //Store device information needed to create a Device object in a future
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("deviceName", getName());
        String encodedPublicKey = Base64.encodeToString(publicKey.getEncoded(), 0);
        editor.putString("publicKey", encodedPublicKey);
        editor.commit();

        reloadPluginsFromSettings();

        for (PairingCallback cb : pairingCallback) {
            cb.pairingSuccessful();
        }

    }

    public void acceptPairing() {

        Log.i("Device","Accepted pair request started by the other device");

        //Send our own public key
        NetworkPackage np = NetworkPackage.createPublicKeyPackage(context);
        sendPackage(np, new SendPackageFinishedCallback() {
            @Override
            public void sendSuccessful() {
                pairingDone();
            }
            @Override
            public void sendFailed() {
                Log.e("Device","Unpairing (sendFailed B)");
                pairStatus = PairStatus.NotPaired;
                for (PairingCallback cb : pairingCallback) {
                    cb.pairingFailed(context.getString(R.string.error_not_reachable));
                }
            }
        });

    }

    public void rejectPairing() {

        Log.i("Device","Rejected pair request started by the other device");

        //Log.e("Device","Unpairing (rejectPairing)");
        pairStatus = PairStatus.NotPaired;

        NetworkPackage np = new NetworkPackage(NetworkPackage.PACKAGE_TYPE_PAIR);
        np.set("pair", false);
        sendPackage(np);

        for (PairingCallback cb : pairingCallback) {
            cb.pairingFailed(context.getString(R.string.error_canceled_by_user));
        }

    }




    //
    // ComputerLink-related functions
    //

    public boolean isReachable() {
        return !links.isEmpty();
    }

    public void addLink(NetworkPackage identityPackage, BaseLink link) {

        this.protocolVersion = identityPackage.getInt("protocolVersion");

        links.add(link);

        try {
            SharedPreferences globalSettings = PreferenceManager.getDefaultSharedPreferences(context);
            byte[] privateKeyBytes = Base64.decode(globalSettings.getString("privateKey", ""), 0);
            PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
            link.setPrivateKey(privateKey);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("Device", "Exception reading our own private key"); //Should not happen
        }

        Log.i("Device","addLink "+link.getLinkProvider().getName()+" -> "+getName() + " active links: "+ links.size());

        Collections.sort(links, new Comparator<BaseLink>() {
            @Override
            public int compare(BaseLink o, BaseLink o2) {
                return o2.getLinkProvider().getPriority() - o.getLinkProvider().getPriority();
            }
        });

        link.addPackageReceiver(this);

        if (links.size() == 1) {
            reloadPluginsFromSettings();
        }
    }

    public void removeLink(BaseLink link) {
        link.removePackageReceiver(this);
        links.remove(link);
        Log.i("Device","removeLink: "+link.getLinkProvider().getName() + " -> "+getName() + " active links: "+ links.size());
        if (links.isEmpty()) {
            reloadPluginsFromSettings();
        }
    }

    @Override
    public void onPackageReceived(NetworkPackage np) {

        if (np.getType().equals(NetworkPackage.PACKAGE_TYPE_PAIR)) {

            Log.i("Device","Pair package");

            boolean wantsPair = np.getBoolean("pair");

            if (wantsPair == isPaired()) {
                if (pairStatus == PairStatus.Requested) {
                    //Log.e("Device","Unpairing (pair rejected)");
                    pairStatus = PairStatus.NotPaired;
                    if (pairingTimer != null) pairingTimer.cancel();
                    for (PairingCallback cb : pairingCallback) {
                        cb.pairingFailed(context.getString(R.string.error_canceled_by_other_peer));
                    }
                }
                return;
            }

            if (wantsPair) {

                //Retrieve their public key
                try {
                    String publicKeyContent = np.getString("publicKey").replace("-----BEGIN PUBLIC KEY-----\n","").replace("-----END PUBLIC KEY-----\n","");
                    byte[] publicKeyBytes = Base64.decode(publicKeyContent, 0);
                    publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(publicKeyBytes));
                } catch(Exception e) {
                    e.printStackTrace();
                    Log.e("Device","Pairing exception: Received incorrect key");
                    for (PairingCallback cb : pairingCallback) {
                        cb.pairingFailed(context.getString(R.string.error_invalid_key));
                    }
                    return;
                }

                if (pairStatus == PairStatus.Requested)  { //We started pairing

                    Log.i("Pairing","Pair answer");

                    if (pairingTimer != null) pairingTimer.cancel();

                    pairingDone();

                } else {

                    Log.i("Pairing","Pair request");

                    Intent intent = new Intent(context, PairActivity.class);
                    intent.putExtra("deviceId", deviceId);
                    PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT);

                    Resources res = context.getResources();

                    Notification noti = new NotificationCompat.Builder(context)
                            .setContentTitle(res.getString(R.string.pairing_request_from, getName()))
                            .setContentText(res.getString(R.string.tap_to_answer))
                            .setContentIntent(pendingIntent)
                            .setTicker(res.getString(R.string.pair_requested))
                            .setSmallIcon(android.R.drawable.ic_menu_help)
                            .setAutoCancel(true)
                            .setDefaults(Notification.DEFAULT_SOUND)
                            .build();


                    final NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                    notificationId = (int)System.currentTimeMillis();
                    notificationManager.notify(notificationId, noti);

                    if (pairingTimer != null) pairingTimer.cancel();
                    pairingTimer = new Timer();

                    pairingTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            Log.e("Device","Unpairing (timeout B)");
                            pairStatus = PairStatus.NotPaired;
                            notificationManager.cancel(notificationId);
                        }
                    }, 25*1000); //Time to show notification, waiting for user to accept (peer will timeout in 30 seconds)
                    pairStatus = PairStatus.RequestedByPeer;
                    for (PairingCallback cb : pairingCallback) cb.incomingRequest();

                }
            } else {
                Log.i("Pairing","Unpair request");

                if (pairStatus == PairStatus.Requested) {
                    pairingTimer.cancel();
                    for (PairingCallback cb : pairingCallback) {
                        cb.pairingFailed(context.getString(R.string.error_canceled_by_other_peer));
                    }
                } else if (pairStatus == PairStatus.Paired) {
                    SharedPreferences preferences = context.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE);
                    preferences.edit().remove(deviceId).commit();
                    reloadPluginsFromSettings();
                }

                //Log.e("Device","Unpairing (unpair request)");
                pairStatus = PairStatus.NotPaired;
                for (PairingCallback cb : pairingCallback) cb.unpaired();

            }
        } else if (!isPaired()) {

            //TODO: Notify the other side that we don't trust them
            Log.e("onPackageReceived","Device not paired, ignoring package!");

        } else {

            for (Plugin plugin : plugins.values()) {
                plugin.onPackageReceived(np);
            }
        }

    }

    public interface SendPackageFinishedCallback {
        void sendSuccessful();
        void sendFailed();
    }

    public void sendPackage(NetworkPackage np) {
        sendPackage(np,null);
    }

    //Async
    public void sendPackage(final NetworkPackage np, final SendPackageFinishedCallback callback) {


        final Exception backtrace = new Exception();
        new Thread(new Runnable() {
            @Override
            public void run() {

                //Log.e("sendPackage", "Sending package...");
                //Log.e("sendPackage", np.serialize());

                boolean useEncryption = (!np.getType().equals(NetworkPackage.PACKAGE_TYPE_PAIR) && isPaired());

                //We need a copy to avoid concurrent modification exception if the original list changes
                ArrayList<BaseLink> mLinks = new ArrayList<BaseLink>(links);

                boolean success = false;
                try {
                    for (BaseLink link : mLinks) {
                        if (useEncryption) {
                            success = link.sendPackageEncrypted(np, publicKey);
                        } else {
                            success = link.sendPackage(np);
                        }
                        if (success) break;
                    }
                } catch(Exception e) {
                    e.printStackTrace();
                    Log.e("sendPackage","Error while sending package");
                    success = false;
                }

                if (success) {
                   // Log.e("sendPackage","Package sent");
                } else {
                    backtrace.printStackTrace();
                    Log.e("sendPackage","Error: Package could not be sent ("+mLinks.size()+" links available)");
                }

                if (callback != null) {
                    if (success) callback.sendSuccessful();
                    else callback.sendFailed();
                }

            }
        }).start();

    }





    //
    // Plugin-related functions
    //

    public Plugin getPlugin(String name) {
        return plugins.get(name);
    }

    private synchronized void addPlugin(final String name) {
        Plugin existing = plugins.get(name);
        if (existing != null) {
            Log.w("addPlugin","plugin already present:" + name);
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

    private synchronized boolean removePlugin(String name) {

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

    private final ArrayList<PluginsChangedListener> pluginsChangedListeners = new ArrayList<PluginsChangedListener>();

    public void addPluginsChangedListener(PluginsChangedListener listener) {
        pluginsChangedListeners.add(listener);
    }

    public void removePluginsChangedListener(PluginsChangedListener listener) {
        pluginsChangedListeners.remove(listener);
    }

}
