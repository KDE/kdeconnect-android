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
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Base64;
import android.util.Log;

import org.kde.kdeconnect.Backends.BaseLink;
import org.kde.kdeconnect.Backends.BasePairingHandler;
import org.kde.kdeconnect.Backends.LanBackend.LanLinkProvider;
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect.UserInterface.MaterialActivity;
import org.kde.kdeconnect_tp.R;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class Device implements BaseLink.PackageReceiver {

    private final Context context;

    private final String deviceId;
    private String name;
    public PublicKey publicKey;
    public Certificate certificate;
    private int notificationId;
    private int protocolVersion;

    private DeviceType deviceType;
    private PairStatus pairStatus;

    private final CopyOnWriteArrayList<PairingCallback> pairingCallback = new CopyOnWriteArrayList<>();
    private Map<String, BasePairingHandler> pairingHandlers = new HashMap<>();

    private final CopyOnWriteArrayList<BaseLink> links = new CopyOnWriteArrayList<>();

    private List<String> m_supportedPlugins = new ArrayList<>();
    private final ConcurrentHashMap<String, Plugin> plugins = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Plugin> failedPlugins = new ConcurrentHashMap<>();
    private Map<String, ArrayList<String>> pluginsByIncomingInterface;

    private final SharedPreferences settings;

    private final CopyOnWriteArrayList<PluginsChangedListener> pluginsChangedListeners = new CopyOnWriteArrayList<>();

    public interface PluginsChangedListener {
        void onPluginsChanged(Device device);
    }

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
            if ("phone".equals(s)) return Phone;
            return Computer; //Default
        }
        public String toString() {
            switch (this) {
                case Tablet: return "tablet";
                case Phone: return "phone";
                default: return "desktop";
            }
        }
    }

    public interface PairingCallback {
        void incomingRequest();
        void pairingSuccessful();
        void pairingFailed(String error);
        void unpaired();
    }

    //Remembered trusted device, we need to wait for a incoming devicelink to communicate
    Device(Context context, String deviceId) {
        settings = context.getSharedPreferences(deviceId, Context.MODE_PRIVATE);

        //Log.e("Device","Constructor A");

        this.context = context;
        this.deviceId = deviceId;
        this.name = settings.getString("deviceName", context.getString(R.string.unknown_device));
        this.pairStatus = PairStatus.Paired;
        this.protocolVersion = NetworkPackage.ProtocolVersion; //We don't know it yet
        this.deviceType = DeviceType.FromString(settings.getString("deviceType", "desktop"));

        try {
            String publicKeyStr = settings.getString("publicKey", null);
            if (publicKeyStr != null) {
                byte[] publicKeyBytes = Base64.decode(publicKeyStr, 0);
                publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(publicKeyBytes));
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("KDE/Device","Exception deserializing stored public key for device");
        }

        //Do not load plugins yet, the device is not present
        //reloadPluginsFromSettings();
    }

    //Device known via an incoming connection sent to us via a devicelink, we know everything but we don't trust it yet
    Device(Context context, NetworkPackage np, BaseLink dl) {

        //Log.e("Device","Constructor B");

        this.context = context;
        this.deviceId = np.getString("deviceId");
        this.name = context.getString(R.string.unknown_device); //We read it in addLink
        this.pairStatus = PairStatus.NotPaired;
        this.protocolVersion = 0;
        this.deviceType = DeviceType.Computer;
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

        hidePairingNotification();

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

        hidePairingNotification();

        notificationId = (int)System.currentTimeMillis();

        Intent intent = new Intent(getContext(), MaterialActivity.class);
        intent.putExtra("deviceId", getDeviceId());
        intent.putExtra("notificationId", notificationId);
        PendingIntent pendingIntent = PendingIntent.getActivity(getContext(), 0, intent, PendingIntent.FLAG_ONE_SHOT);

        Resources res = getContext().getResources();

        Notification noti = new NotificationCompat.Builder(getContext())
                .setContentTitle(res.getString(R.string.pairing_request_from, getName()))
                .setContentText(res.getString(R.string.tap_to_answer))
                .setContentIntent(pendingIntent)
                .setTicker(res.getString(R.string.pair_requested))
                .setSmallIcon(R.drawable.ic_notification)
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .build();

        final NotificationManager notificationManager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);

        try {
            BackgroundService.addGuiInUseCounter(context);
            notificationManager.notify(notificationId, noti);
        } catch(Exception e) {
            //4.1 will throw an exception about not having the VIBRATE permission, ignore it.
            //https://android.googlesource.com/platform/frameworks/base/+/android-4.2.1_r1.2%5E%5E!/
        }
    }

    public void hidePairingNotification() {
        final NotificationManager notificationManager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(notificationId);
        BackgroundService.removeGuiInUseCounter(context);
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
            this.deviceType = DeviceType.FromString(identityPackage.getString("deviceType", "desktop"));
        }

        if (identityPackage.has("certificate")) {
            String certificateString = identityPackage.getString("certificate");

            try {
                byte[] certificateBytes = Base64.decode(certificateString, 0);
                certificate = SslHelper.parseCertificate(certificateBytes);
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

        Set<String> outgoingCapabilities = identityPackage.getStringSet("outgoingCapabilities", null);
        Set<String> incomingCapabilities = identityPackage.getStringSet("incomingCapabilities", null);
        if (incomingCapabilities != null && outgoingCapabilities != null) {
            m_supportedPlugins = new Vector<>(PluginFactory.pluginsForCapabilities(context, incomingCapabilities, outgoingCapabilities));
        } else {
            m_supportedPlugins = new Vector<>(PluginFactory.getAvailablePlugins());
        }

        link.addPackageReceiver(this);

        reloadPluginsFromSettings();

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

        hackToMakeRetrocompatiblePacketTypes(np);

        if (NetworkPackage.PACKAGE_TYPE_PAIR.equals(np.getType())) {

            Log.i("KDE/Device", "Pair package");

            for (BasePairingHandler ph: pairingHandlers.values()) {
                try {
                    ph.packageReceived(np);
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e("PairingPackageReceived","Exception");
                }
            }
        } else if (isPaired()) {

            //If capabilities are not supported, iterate all plugins
            Collection<String> targetPlugins = pluginsByIncomingInterface.get(np.getType());
            if (targetPlugins != null && !targetPlugins.isEmpty()) {
                for (String pluginKey : targetPlugins) {
                    Plugin plugin = plugins.get(pluginKey);
                    try {
                        plugin.onPackageReceived(np);
                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.e("KDE/Device", "Exception in " + plugin.getPluginKey() + "'s onPackageReceived()");
                        //try { Log.e("KDE/Device", "NetworkPackage:" + np.serialize()); } catch (Exception _) { }
                    }
                }
            } else {
                Log.w("Device", "Ignoring packet with type " + np.getType() + " because no plugin can handle it");
            }
        } else {

            //Log.e("KDE/onPackageReceived","Device not paired, will pass package to unpairedPackageListeners");

            // If it is pair package, it should be captured by "if" at start
            // If not and device is paired, it should be captured by isPaired
            // Else unpair, this handles the situation when one device unpairs, but other dont know like unpairing when wi-fi is off

            unpair();

            //If capabilities are not supported, iterate all plugins
            Collection<String> targetPlugins = pluginsByIncomingInterface.get(np.getType());
            if (targetPlugins != null && !targetPlugins.isEmpty()) {
                for (String pluginKey : targetPlugins) {
                    Plugin plugin = plugins.get(pluginKey);
                    try {
                        plugin.onUnpairedDevicePackageReceived(np);
                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.e("KDE/Device", "Exception in " + plugin.getDisplayName() + "'s onPackageReceived() in unPairedPackageListeners");
                    }
                }
            } else {
                Log.e("Device", "Ignoring packet with type " + np.getType() + " because no plugin can handle it");
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

        hackToMakeRetrocompatiblePacketTypes(np);


        /*
        if (!m_outgoingCapabilities.contains(np.getType()) && !NetworkPackage.protocolPackageTypes.contains(np.getType())) {
            Log.e("Device/sendPackage", "Plugin tried to send an undeclared package: " + np.getType());
            Log.w("Device/sendPackage", "Declared outgoing package types: " + Arrays.toString(m_outgoingCapabilities.toArray()));
        }
        */

        //Log.e("sendPackage", "Sending package...");
        //Log.e("sendPackage", np.serialize());

        final Throwable backtrace = new Throwable();
        new Thread(new Runnable() {
            @Override
            public void run() {

                boolean useEncryption = (protocolVersion < LanLinkProvider.MIN_VERSION_WITH_SSL_SUPPORT && (!np.getType().equals(NetworkPackage.PACKAGE_TYPE_PAIR) && isPaired()));

                //Make a copy to avoid concurrent modification exception if the original list changes
                for (final BaseLink link : links) {
                    if (link == null) continue; //Since we made a copy, maybe somebody destroyed the link in the meanwhile
                    if (useEncryption) {
                        link.sendPackageEncrypted(np, callback, publicKey);
                    } else {
                        link.sendPackage(np, callback);
                    }
                    if (callback.success) break; //If the link didn't call sendSuccess(), try the next one
                }

                if (!callback.success) {
                    Log.e("KDE/sendPackage", "No device link (of "+links.size()+" available) could send the package. Package "+np.getType()+" to " + name + " lost!");
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

    private synchronized boolean addPlugin(final String pluginKey) {
        Plugin existing = plugins.get(pluginKey);
        if (existing != null) {
            //Log.w("KDE/addPlugin","plugin already present:" + pluginKey);
            return true;
        }

        final Plugin plugin = PluginFactory.instantiatePluginForDevice(context, pluginKey, this);
        if (plugin == null) {
            Log.e("KDE/addPlugin","could not instantiate plugin: "+pluginKey);
            //Can't put a null
            //failedPlugins.put(pluginKey, null);
            return false;
        }

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

        return success;
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

        return true;
    }

    public void setPluginEnabled(String pluginKey, boolean value) {
        settings.edit().putBoolean(pluginKey,value).apply();
        reloadPluginsFromSettings();
    }

    public boolean isPluginEnabled(String pluginKey) {
        boolean enabledByDefault = PluginFactory.getPluginInfo(context, pluginKey).isEnabledByDefault();
        boolean enabled = settings.getBoolean(pluginKey, enabledByDefault);
        return enabled;
    }

    public void reloadPluginsFromSettings() {

        failedPlugins.clear();

        HashMap<String, ArrayList<String>> newPluginsByIncomingInterface = new HashMap<>();

        for (String pluginKey : m_supportedPlugins) {

            PluginFactory.PluginInfo pluginInfo = PluginFactory.getPluginInfo(context, pluginKey);

            boolean pluginEnabled = false;
            boolean listenToUnpaired = pluginInfo.listenToUnpaired();
            if ((isPaired() || listenToUnpaired) && isReachable()) {
                pluginEnabled = isPluginEnabled(pluginKey);
            }

            if (pluginEnabled) {
                boolean success = addPlugin(pluginKey);
                if (success) {
                    for (String packageType : pluginInfo.getSupportedPackageTypes()) {
                        ArrayList<String> plugins = newPluginsByIncomingInterface.get(packageType);
                        if (plugins == null) plugins = new ArrayList<>();
                        plugins.add(pluginKey);
                        newPluginsByIncomingInterface.put(packageType, plugins);
                    }
                }
            } else {
                removePlugin(pluginKey);
            }

        }

        pluginsByIncomingInterface = newPluginsByIncomingInterface;

        onPluginsChanged();
    }

    public void onPluginsChanged() {
        for (PluginsChangedListener listener : pluginsChangedListeners) {
            listener.onPluginsChanged(Device.this);
        }
    }

    public ConcurrentHashMap<String,Plugin> getLoadedPlugins() {
        return plugins;
    }

    public ConcurrentHashMap<String,Plugin> getFailedPlugins() {
        return failedPlugins;
    }

    public void addPluginsChangedListener(PluginsChangedListener listener) {
        pluginsChangedListeners.add(listener);
    }

    public void removePluginsChangedListener(PluginsChangedListener listener) {
        pluginsChangedListeners.remove(listener);
    }

    public void disconnect() {
        for(BaseLink link : links) {
            link.disconnect();
        }
    }

    public boolean deviceShouldBeKeptAlive() {

        SharedPreferences preferences = context.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE);
        if (preferences.contains(getDeviceId())) {
            //Log.e("DeviceShouldBeKeptAlive", "because it's a paired device");
            return true; //Already paired
        }

        for(BaseLink l : links) {
            if (l.linkShouldBeKeptAlive()) {
                return true;
            }
        }
        return false;
    }

    public List<String> getSupportedPlugins() {
        return m_supportedPlugins;
    }

    public void hackToMakeRetrocompatiblePacketTypes(NetworkPackage np) {
        if (protocolVersion >= 6) return;
        np.mType = np.getType().replace(".request","");
    }


}
