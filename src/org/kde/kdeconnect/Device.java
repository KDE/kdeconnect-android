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

package org.kde.kdeconnect;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
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
import java.util.HashMap;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class Device implements BaseLink.PackageReceiver {

    private final Context context;

    private final String deviceId;
    private String name;
    public PublicKey publicKey;
    private int notificationId;
    private int protocolVersion;

    public enum PairStatus {
        NotPaired,
        Requested,
        RequestedByPeer,
        Paired
    }

    public enum DeviceType {
        Phone,
        Tablet,
        Computer;

        public static DeviceType FromString(String s) {
            if ("tablet".equals(s)) return Tablet;
            if ("computer".equals(s)) return Computer;
            return Phone; //Default
        }
        public String toString() {
            switch (this) {
                case Tablet: return "tablet";
                case Computer: return "computer";
                default: return "phone";
            }
        }
    }

    public interface PairingCallback {
        void incomingRequest();
        void pairingSuccessful();
        void pairingFailed(String error);
        void unpaired();
    }

    private DeviceType deviceType;
    private PairStatus pairStatus;
    private final ArrayList<PairingCallback> pairingCallback = new ArrayList<>();
    private Timer pairingTimer;

    private final ArrayList<BaseLink> links = new ArrayList<>();
    private final HashMap<String, Plugin> plugins = new HashMap<>();
    private final HashMap<String, Plugin> failedPlugins = new HashMap<>();

    private final SharedPreferences settings;

    //Remembered trusted device, we need to wait for a incoming devicelink to communicate
    Device(Context context, String deviceId) {
        settings = context.getSharedPreferences(deviceId, Context.MODE_PRIVATE);

        //Log.e("Device","Constructor A");

        this.context = context;
        this.deviceId = deviceId;
        this.name = settings.getString("deviceName", context.getString(R.string.unknown_device));
        this.pairStatus = PairStatus.Paired;
        this.protocolVersion = NetworkPackage.ProtocolVersion; //We don't know it yet
        this.deviceType = DeviceType.FromString(settings.getString("deviceType", "computer"));

        try {
            byte[] publicKeyBytes = Base64.decode(settings.getString("publicKey", ""), 0);
            publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(publicKeyBytes));
        } catch (Exception e) {
            e.printStackTrace();
            unpair();
            Log.e("KDE/Device","Exception");
        }

        reloadPluginsFromSettings();
    }

    //Device known via an incoming connection sent to us via a devicelink, we know everything but we don't trust it yet
    Device(Context context, NetworkPackage np, BaseLink dl) {

        //Log.e("Device","Constructor B");

        this.context = context;
        this.deviceId = np.getString("deviceId");
        this.name = np.getString("deviceName", context.getString(R.string.unknown_device));
        this.pairStatus = PairStatus.NotPaired;
        this.protocolVersion = np.getInt("protocolVersion");
        this.deviceType = DeviceType.FromString(np.getString("deviceType", "computer"));
        this.publicKey = null;

        settings = context.getSharedPreferences(deviceId, Context.MODE_PRIVATE);

        addLink(np, dl);
    }

    public String getName() {
        return name != null? name : context.getString(R.string.unknown_device);
    }

    public Drawable getIcon()
    {
        int drawableId;
        switch (deviceType) {
            case Phone: drawableId = R.drawable.ic_device_phone; break;
            case Tablet: drawableId = R.drawable.ic_device_tablet; break;
            default: drawableId = R.drawable.ic_device_laptop;
        }
        return ContextCompat.getDrawable(context, drawableId);
    }

    public DeviceType getDeviceType() {
        return deviceType;
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

        switch(pairStatus) {
            case Paired:
                for (PairingCallback cb : pairingCallback) {
                    cb.pairingFailed(res.getString(R.string.error_already_paired));
                }
                return;
            case Requested:
                for (PairingCallback cb : pairingCallback) {
                    cb.pairingFailed(res.getString(R.string.error_already_requested));
                }
                return;
            case RequestedByPeer:
                Log.d("requestPairing", "Pairing already started by the other end, accepting their request.");
                acceptPairing();
                return;
            case NotPaired:
                ;
        }

        if (!isReachable()) {
            for (PairingCallback cb : pairingCallback) {
                cb.pairingFailed(res.getString(R.string.error_not_reachable));
            }
            return;
        }

        //Send our own public key
        NetworkPackage np = NetworkPackage.createPublicKeyPackage(context);
        sendPackage(np, new SendPackageStatusCallback(){
            @Override
            public void onSuccess() {
                if (pairingTimer != null) pairingTimer.cancel();
                pairingTimer = new Timer();
                pairingTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        for (PairingCallback cb : pairingCallback) {
                            cb.pairingFailed(context.getString(R.string.error_timed_out));
                        }
                        Log.e("KDE/Device","Unpairing (timeout A)");
                        pairStatus = PairStatus.NotPaired;
                    }
                }, 30*1000); //Time to wait for the other to accept
                pairStatus = PairStatus.Requested;
            }

            @Override
            public void onFailure(Throwable e) {
                for (PairingCallback cb : pairingCallback) {
                    cb.pairingFailed(context.getString(R.string.error_could_not_send_package));
                }
                Log.e("KDE/Device","Unpairing (sendFailed A)");
                pairStatus = PairStatus.NotPaired;
            }

        });

    }

    public int getNotificationId() {
        return notificationId;
    }

    public void unpair() {

        //Log.e("Device","Unpairing (unpair)");
        pairStatus = PairStatus.NotPaired;

        SharedPreferences preferences = context.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE);
        preferences.edit().remove(deviceId).apply();

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
        preferences.edit().putBoolean(deviceId,true).apply();

        //Store device information needed to create a Device object in a future
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("deviceName", getName());
        editor.putString("deviceType", deviceType.toString());
        String encodedPublicKey = Base64.encodeToString(publicKey.getEncoded(), 0);
        editor.putString("publicKey", encodedPublicKey);
        editor.apply();

        reloadPluginsFromSettings();

        for (PairingCallback cb : pairingCallback) {
            cb.pairingSuccessful();
        }

    }

    public void acceptPairing() {

        Log.i("KDE/Device","Accepted pair request started by the other device");

        //Send our own public key
        NetworkPackage np = NetworkPackage.createPublicKeyPackage(context);
        sendPackage(np, new SendPackageStatusCallback() {
            @Override
            protected void onSuccess() {
                pairingDone();
            }
            @Override
            protected void onFailure(Throwable e) {
                Log.e("Device","Unpairing (sendFailed B)");
                pairStatus = PairStatus.NotPaired;
                for (PairingCallback cb : pairingCallback) {
                    cb.pairingFailed(context.getString(R.string.error_not_reachable));
                }
            }
        });

    }

    public void rejectPairing() {

        Log.i("KDE/Device","Rejected pair request started by the other device");

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
        //FilesHelper.LogOpenFileCount();

        this.protocolVersion = identityPackage.getInt("protocolVersion");

        if (identityPackage.has("deviceName")) {
            this.name = identityPackage.getString("deviceName", this.name);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString("deviceName", this.name);
            editor.apply();
        }

        if (identityPackage.has("deviceType")) {
            this.deviceType = DeviceType.FromString(identityPackage.getString("deviceType", "computer"));
        }


        links.add(link);

        try {
            SharedPreferences globalSettings = PreferenceManager.getDefaultSharedPreferences(context);
            byte[] privateKeyBytes = Base64.decode(globalSettings.getString("privateKey", ""), 0);
            PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
            link.setPrivateKey(privateKey);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("KDE/Device", "Exception reading our own private key"); //Should not happen
        }

        Log.i("KDE/Device","addLink "+link.getLinkProvider().getName()+" -> "+getName() + " active links: "+ links.size());

        /*
        Collections.sort(links, new Comparator<BaseLink>() {
            @Override
            public int compare(BaseLink o, BaseLink o2) {
                return o2.getLinkProvider().getPriority() - o.getLinkProvider().getPriority();
            }
        });
        */

        link.addPackageReceiver(this);

        if (links.size() == 1) {
            reloadPluginsFromSettings();
        }
    }

    public void removeLink(BaseLink link) {
        //FilesHelper.LogOpenFileCount();

        link.removePackageReceiver(this);
        links.remove(link);
        Log.i("KDE/Device","removeLink: "+link.getLinkProvider().getName() + " -> "+getName() + " active links: "+ links.size());
        if (links.isEmpty()) {
            reloadPluginsFromSettings();
        }
    }

    @Override
    public void onPackageReceived(NetworkPackage np) {

        if (np.getType().equals(NetworkPackage.PACKAGE_TYPE_PAIR)) {

            Log.i("KDE/Device","Pair package");

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
                    Log.e("KDE/Device","Pairing exception: Received incorrect key");
                    for (PairingCallback cb : pairingCallback) {
                        cb.pairingFailed(context.getString(R.string.error_invalid_key));
                    }
                    return;
                }

                if (pairStatus == PairStatus.Requested)  { //We started pairing

                    Log.i("KDE/Pairing","Pair answer");

                    if (pairingTimer != null) pairingTimer.cancel();

                    pairingDone();

                } else {

                    Log.i("KDE/Pairing","Pair request");

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
                            .setDefaults(Notification.DEFAULT_ALL)
                            .build();


                    final NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                    notificationId = (int)System.currentTimeMillis();
                    try {
                        notificationManager.notify(notificationId, noti);
                    } catch(Exception e) {
                        //4.1 will throw an exception about not having the VIBRATE permission, ignore it.
                        //https://android.googlesource.com/platform/frameworks/base/+/android-4.2.1_r1.2%5E%5E!/
                    }

                    if (pairingTimer != null) pairingTimer.cancel();
                    pairingTimer = new Timer();

                    pairingTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            Log.e("KDE/Device","Unpairing (timeout B)");
                            pairStatus = PairStatus.NotPaired;
                            notificationManager.cancel(notificationId);
                        }
                    }, 25*1000); //Time to show notification, waiting for user to accept (peer will timeout in 30 seconds)
                    pairStatus = PairStatus.RequestedByPeer;
                    for (PairingCallback cb : pairingCallback) cb.incomingRequest();

                }
            } else {
                Log.i("KDE/Pairing","Unpair request");

                if (pairStatus == PairStatus.Requested) {
                    pairingTimer.cancel();
                    for (PairingCallback cb : pairingCallback) {
                        cb.pairingFailed(context.getString(R.string.error_canceled_by_other_peer));
                    }
                } else if (pairStatus == PairStatus.Paired) {
                    SharedPreferences preferences = context.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE);
                    preferences.edit().remove(deviceId).apply();
                    reloadPluginsFromSettings();
                }

                pairStatus = PairStatus.NotPaired;
                for (PairingCallback cb : pairingCallback) cb.unpaired();

            }
        } else if (isPaired()) {

            for (Plugin plugin : plugins.values()) {
                try {
                    plugin.onPackageReceived(np);
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e("KDE/Device", "Exception in "+plugin.getDisplayName()+"'s onPackageReceived()");
                }

            }

        } else {

            Log.e("KDE/onPackageReceived","Device not paired, will pass package to unpairedPackageListeners");

            if (pairStatus != PairStatus.Requested) {
                unpair();
            }

            for (Plugin plugin : unpairedPackageListeners) {
                try {
                    plugin.onUnpairedDevicePackageReceived(np);
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e("KDE/Device", "Exception in "+plugin.getDisplayName()+"'s onPackageReceived() in unPairedPackageListeners");
                }
            }

        }

    }

    public static abstract class SendPackageStatusCallback {
        protected abstract void onSuccess();
        protected abstract void onFailure(Throwable e);
        protected void onProgressChanged(int percent) { }

        private boolean success = false;
        public void sendSuccess() {
            success = true;
            onSuccess();
        }
        public void sendFailure(Throwable e) {
            if (e != null) {
                e.printStackTrace();
                Log.e("KDE/sendPackage", "Exception: " + e.getMessage());
            } else {
                Log.e("KDE/sendPackage", "Unknown (null) exception");
            }
            onFailure(e);
        }
        public void sendProgress(int percent) { onProgressChanged(percent); }
    }


    public void sendPackage(NetworkPackage np) {
        sendPackage(np,new SendPackageStatusCallback() {
            @Override
            protected void onSuccess() { }
            @Override
            protected void onFailure(Throwable e) { }
        });
    }

    //Async
    public void sendPackage(final NetworkPackage np, final SendPackageStatusCallback callback) {

        //Log.e("sendPackage", "Sending package...");
        //Log.e("sendPackage", np.serialize());

        final Throwable backtrace = new Throwable();
        new Thread(new Runnable() {
            @Override
            public void run() {

                boolean useEncryption = (!np.getType().equals(NetworkPackage.PACKAGE_TYPE_PAIR) && isPaired());

                //Make a copy to avoid concurrent modification exception if the original list changes
                ArrayList<BaseLink> mLinks = new ArrayList<>(links);
                for (final BaseLink link : mLinks) {
                    if (link == null) continue; //Since we made a copy, maybe somebody destroyed the link in the meanwhile
                    if (useEncryption) {
                        link.sendPackageEncrypted(np, callback, publicKey);
                    } else {
                        link.sendPackage(np, callback);
                    }
                    if (callback.success) break; //If the link didn't call sendSuccess(), try the next one
                }

                if (!callback.success) {
                    Log.e("KDE/sendPackage", "No device link (of "+mLinks.size()+" available) could send the package. Package lost!");
                    backtrace.printStackTrace();
                }

            }
        }).start();

    }

    //
    // Plugin-related functions
    //

    public <T extends Plugin> T getPlugin(Class<T> pluginClass) {
        return (T)getPlugin(Plugin.getPluginKey(pluginClass));
    }

    public <T extends Plugin> T getPlugin(Class<T> pluginClass, boolean includeFailed) {
        return (T)getPlugin(Plugin.getPluginKey(pluginClass), includeFailed);
    }

    public Plugin getPlugin(String pluginKey) {
        return getPlugin(pluginKey, false);
    }

    public Plugin getPlugin(String pluginKey, boolean includeFailed) {
        Plugin plugin = plugins.get(pluginKey);
        if (includeFailed && plugin == null) {
            plugin = failedPlugins.get(pluginKey);
        }
        return plugin;
    }

    private synchronized void addPlugin(final String pluginKey) {
        Plugin existing = plugins.get(pluginKey);
        if (existing != null) {
            Log.w("KDE/addPlugin","plugin already present:" + pluginKey);
            return;
        }

        final Plugin plugin = PluginFactory.instantiatePluginForDevice(context, pluginKey, this);
        if (plugin == null) {
            Log.e("KDE/addPlugin","could not instantiate plugin: "+pluginKey);
            failedPlugins.put(pluginKey, null);
            return;
        }

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {

                boolean success;
                try {
                    success = plugin.onCreate();
                } catch (Exception e) {
                    success = false;
                    e.printStackTrace();
                    Log.e("KDE/addPlugin", "Exception loading plugin " + pluginKey);
                }

                if (success) {
                    //Log.e("addPlugin","added " + pluginKey);
                    failedPlugins.remove(pluginKey);
                    plugins.put(pluginKey, plugin);
                } else {
                    Log.e("KDE/addPlugin", "plugin failed to load " + pluginKey);
                    plugins.remove(pluginKey);
                    failedPlugins.put(pluginKey, plugin);
                }

                for (PluginsChangedListener listener : pluginsChangedListeners) {
                    listener.onPluginsChanged(Device.this);
                }

            }
        });

    }

    private synchronized boolean removePlugin(String pluginKey) {

        Plugin plugin = plugins.remove(pluginKey);
        Plugin failedPlugin = failedPlugins.remove(pluginKey);

        if (plugin == null) {
            if (failedPlugin == null) {
                //Not found
                return false;
            }
            plugin = failedPlugin;
        }

        try {
            plugin.onDestroy();
            //Log.e("removePlugin","removed " + pluginKey);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("KDE/removePlugin","Exception calling onDestroy for plugin "+pluginKey);
        }

        if (unpairedPackageListeners.contains(plugin)) {
            unpairedPackageListeners.remove(plugin);
        }

        for (PluginsChangedListener listener : pluginsChangedListeners) {
            listener.onPluginsChanged(this);
        }

        return true;
    }

    public void setPluginEnabled(String pluginKey, boolean value) {
        settings.edit().putBoolean(pluginKey,value).apply();
        if (value && isPaired() && isReachable()) addPlugin(pluginKey);
        else removePlugin(pluginKey);
    }

    public boolean isPluginEnabled(String pluginKey) {
        boolean enabledByDefault = PluginFactory.getPluginInfo(context, pluginKey).isEnabledByDefault();
        boolean enabled = settings.getBoolean(pluginKey, enabledByDefault);
        return enabled;
    }

    public boolean hasPluginsLoaded() {
        return !plugins.isEmpty() || !failedPlugins.isEmpty();
    }

    public void reloadPluginsFromSettings() {

        failedPlugins.clear();

        Set<String> availablePlugins = PluginFactory.getAvailablePlugins();

        for(String pluginKey : availablePlugins) {
            boolean enabled = false;
            if (isPaired() && isReachable()) {
                enabled = isPluginEnabled(pluginKey);
            }
            if (enabled) {
                addPlugin(pluginKey);
            } else {
                removePlugin(pluginKey);
            }
        }

        //No need to call PluginsChangedListeners because addPlugin and removePlugin already do so
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

    private final ArrayList<PluginsChangedListener> pluginsChangedListeners = new ArrayList<>();

    public void addPluginsChangedListener(PluginsChangedListener listener) {
        pluginsChangedListeners.add(listener);
    }

    public void removePluginsChangedListener(PluginsChangedListener listener) {
        pluginsChangedListeners.remove(listener);
    }

    private final ArrayList<Plugin> unpairedPackageListeners = new ArrayList<>();

    public void registerUnpairedPackageListener(Plugin p) {
        Log.e("KDE/registerUnpairedPackageListener", p.getPluginKey() + " plugin registered to receive package from unpaired device");
        unpairedPackageListeners.add(p);
    }

}
