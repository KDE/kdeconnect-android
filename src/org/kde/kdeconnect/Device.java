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
import android.util.Base64;
import android.util.Log;

import org.kde.kdeconnect.Backends.BaseLink;
import org.kde.kdeconnect.Backends.BasePairingHandler;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect.UserInterface.PairActivity;
import org.kde.kdeconnect_tp.R;
import org.spongycastle.cert.X509CertificateHolder;
import org.spongycastle.cert.jcajce.JcaX509CertificateConverter;
import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.io.ByteArrayInputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Device implements BaseLink.PackageReceiver {

    private final Context context;

    private final String deviceId;
    private String name;
    public PublicKey publicKey;
    public Certificate certificate;
    private int notificationId;
    private int protocolVersion;

    public enum PairStatus {
        NotPaired,
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
    private ArrayList<PairingCallback> pairingCallback = new ArrayList<PairingCallback>();
    private Map<String, BasePairingHandler> pairingHandlers = new HashMap<String, BasePairingHandler>();

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
        this.name = settings.getString("deviceName", context.getString(R.string.unknown_device));
        this.pairStatus = PairStatus.Paired;
        this.protocolVersion = NetworkPackage.ProtocolVersion; //We don't know it yet
        this.deviceType = DeviceType.FromString(settings.getString("deviceType", "computer"));

        try {
            byte[] publicKeyBytes = Base64.decode(settings.getString("publicKey", ""), 0);
            publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(publicKeyBytes));
        } catch (Exception e) {
            e.printStackTrace();
            //Do not unpair, they may be paired in some other way
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
        switch (deviceType) {
            case Phone: return context.getResources().getDrawable(R.drawable.ic_device_phone);
            case Tablet: return context.getResources().getDrawable(R.drawable.ic_device_tablet);
            default: return context.getResources().getDrawable(R.drawable.ic_device_laptop);
        }
    }

    public DeviceType getDeviceType() {
        return deviceType;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public Context getContext() {
        return context;
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

    /* Asks all pairing handlers that, is pair requested? */
    public boolean isPairRequested() {
        boolean pairRequested = false;
        for (BasePairingHandler ph: pairingHandlers.values()) {
            pairRequested = pairRequested || ph.isPairRequested();
        }
        return pairRequested;
    }

    /* Asks all pairing handlers that, is pair requested by peer? */
    public boolean isPairRequestedByPeer() {
        boolean pairRequestedByPeer = false;
        for (BasePairingHandler ph : pairingHandlers.values()) {
            pairRequestedByPeer = pairRequestedByPeer || ph.isPairRequestedByPeer();
        }
        return pairRequestedByPeer;
    }

    public void addPairingCallback(PairingCallback callback) {
        pairingCallback.add(callback);
        if (isPairRequestedByPeer()) {
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
            case NotPaired:
                ;
        }

        if (!isReachable()) {
            for (PairingCallback cb : pairingCallback) {
                cb.pairingFailed(res.getString(R.string.error_not_reachable));
            }
            return;
        }

        for (BasePairingHandler ph : pairingHandlers.values()) {
            ph.requestPairing();
        }

    }

    public void unpair() {

        for (BasePairingHandler ph : pairingHandlers.values()) {
            ph.unpair();
        }
        unpairInternal(); // Even if there are no pairing handlers, unpair
    }

    /**
     * This method does not send an unpair package, instead it unpairs internally by deleting trusted device info. . Likely to be called after sending package from
     * pairing handler
     */
    private void unpairInternal() {

        //Log.e("Device","Unpairing (unpairInternal)");
        pairStatus = PairStatus.NotPaired;

        SharedPreferences preferences = context.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE);
        preferences.edit().remove(deviceId).apply();

        // FIXME : We delete all device info here, but the xml file still persists
        SharedPreferences devicePreferences = context.getSharedPreferences(deviceId, Context.MODE_PRIVATE);
        devicePreferences.edit().clear().apply();

        for (PairingCallback cb : pairingCallback) cb.unpaired();

        reloadPluginsFromSettings();

    }

    /* This method should be called after pairing is done from pairing handler. Calling this method again should not create any problem as most of the things will get over writter*/
    private void pairingDone() {

        //Log.e("Device", "Storing as trusted, deviceId: "+deviceId);

        pairStatus = PairStatus.Paired;

        //Store as trusted device
        SharedPreferences preferences = context.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE);
        preferences.edit().putBoolean(deviceId,true).apply();

        SharedPreferences.Editor editor = context.getSharedPreferences(deviceId, Context.MODE_PRIVATE).edit();
        editor.putString("deviceName", name);
        editor.putString("deviceType", deviceType.toString());
        editor.apply();

        reloadPluginsFromSettings();

        for (PairingCallback cb : pairingCallback) {
            cb.pairingSuccessful();
        }

    }

    /* This method is called after accepting pair request form GUI */
    public void acceptPairing() {

        Log.i("KDE/Device", "Accepted pair request started by the other device");

        for (BasePairingHandler ph : pairingHandlers.values()) {
            ph.acceptPairing();
        }

    }

    /* This method is called after rejecting pairing from GUI */
    public void rejectPairing() {

        Log.i("KDE/Device", "Rejected pair request started by the other device");

        //Log.e("Device","Unpairing (rejectPairing)");
        pairStatus = PairStatus.NotPaired;

        for (BasePairingHandler ph : pairingHandlers.values()) {
            ph.rejectPairing();
        }

        for (PairingCallback cb : pairingCallback) {
            cb.pairingFailed(context.getString(R.string.error_canceled_by_user));
        }

    }

    //
    // Notification related methods used during pairing
    //
    public int getNotificationId() {
        return notificationId;
    }

    public void displayPairingNotification() {

        notificationId = (int)System.currentTimeMillis();

        Intent intent = new Intent(getContext(), PairActivity.class);
        intent.putExtra("deviceId", getDeviceId());
        intent.putExtra("notificationId", notificationId);
        PendingIntent pendingIntent = PendingIntent.getActivity(getContext(), 0, intent, PendingIntent.FLAG_ONE_SHOT);

        Resources res = getContext().getResources();

        Notification noti = new NotificationCompat.Builder(getContext())
                .setContentTitle(res.getString(R.string.pairing_request_from, getName()))
                .setContentText(res.getString(R.string.tap_to_answer))
                .setContentIntent(pendingIntent)
                .setTicker(res.getString(R.string.pair_requested))
                .setSmallIcon(android.R.drawable.ic_menu_help)
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .build();

        final NotificationManager notificationManager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);

        try {
            notificationManager.notify(notificationId, noti);
        } catch(Exception e) {
            //4.1 will throw an exception about not having the VIBRATE permission, ignore it.
            //https://android.googlesource.com/platform/frameworks/base/+/android-4.2.1_r1.2%5E%5E!/
        }
    }

    public void cancelPairingNotification() {
        final NotificationManager notificationManager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(notificationId);
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

        if (identityPackage.has("certificate")) {
            String certificateString = identityPackage.getString("certificate");

            try {
                byte[] certificateBytes = Base64.decode(certificateString, 0);
                X509CertificateHolder certificateHolder = new X509CertificateHolder(certificateBytes);
                certificate = new JcaX509CertificateConverter().setProvider(new BouncyCastleProvider()).getCertificate(certificateHolder);
                Log.i("KDE/Device", "Got certificate ");
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("KDE/Device", "Error getting certificate");

            }
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

        if (!pairingHandlers.containsKey(link.getName())) {
            BasePairingHandler.PairingHandlerCallback callback = new BasePairingHandler.PairingHandlerCallback() {
                @Override
                public void incomingRequest() {
                    for (PairingCallback cb : pairingCallback) {
                        cb.incomingRequest();
                    }
                }

                @Override
                public void pairingDone() {
                    Device.this.pairingDone();
                }

                @Override
                public void pairingFailed(String error) {
                    for (PairingCallback cb : pairingCallback) {
                        cb.pairingFailed(error);
                    }
                }

                @Override
                public void unpaired() {
                    unpairInternal();
                }
            };
            pairingHandlers.put(link.getName(), link.getPairingHandler(this, callback));
        }
        pairingHandlers.get(link.getName()).setLink(link);

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

        /* Remove pairing handler corresponding to that link too if it was the only link*/
        boolean linkPresent = false;
        for (BaseLink bl : links) {
            if (bl.getName().equals(link.getName())) {
                linkPresent = true;
                break;
            }
        }
        if (!linkPresent) {
            pairingHandlers.remove(link.getName());
        }

        link.removePackageReceiver(this);
        links.remove(link);
        Log.i("KDE/Device", "removeLink: " + link.getLinkProvider().getName() + " -> " + getName() + " active links: " + links.size());
        if (links.isEmpty()) {
            reloadPluginsFromSettings();
        }
    }

    @Override
    public void onPackageReceived(NetworkPackage np) {

        if (np.getType().equals(NetworkPackage.PACKAGE_TYPE_PAIR)) {

            Log.i("KDE/Device", "Pair package");

            for (BasePairingHandler ph: pairingHandlers.values()) {
                try {
                    ph.packageReceived(np);
                } catch (Exception e) {
                    // There should be no exception here
                }
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

            Log.e("KDE/onPackageReceived","Device not paired, ignoring package!");

            // If it is pair package, it should be captured by "if" at start
            // If not and device is paired, it should be captured by isPaired
            // Else unpair, this handles the situation when one device unpairs, but other dont know like unpairing when wi-fi is off

            unpair();

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
                ArrayList<BaseLink> mLinks = new ArrayList<BaseLink>(links);
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
            failedPlugins.put(pluginKey, plugin);
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

    private final ArrayList<PluginsChangedListener> pluginsChangedListeners = new ArrayList<PluginsChangedListener>();

    public void addPluginsChangedListener(PluginsChangedListener listener) {
        pluginsChangedListeners.add(listener);
    }

    public void removePluginsChangedListener(PluginsChangedListener listener) {
        pluginsChangedListeners.remove(listener);
    }

}
