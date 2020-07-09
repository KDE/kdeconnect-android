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
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.lang3.StringUtils;
import org.kde.kdeconnect.Backends.BaseLink;
import org.kde.kdeconnect.Backends.BasePairingHandler;
import org.kde.kdeconnect.Helpers.DeviceHelper;
import org.kde.kdeconnect.Helpers.NotificationHelper;
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect.UserInterface.MainActivity;
import org.kde.kdeconnect_tp.R;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class Device implements BaseLink.PacketReceiver {

    private final Context context;

    private final String deviceId;
    private String name;
    public Certificate certificate;
    private int notificationId;
    private int protocolVersion;

    private DeviceType deviceType;
    private PairStatus pairStatus;

    private final CopyOnWriteArrayList<PairingCallback> pairingCallback = new CopyOnWriteArrayList<>();
    private final Map<String, BasePairingHandler> pairingHandlers = new HashMap<>();

    private final CopyOnWriteArrayList<BaseLink> links = new CopyOnWriteArrayList<>();
    private DevicePacketQueue packetQueue;

    private List<String> supportedPlugins = new ArrayList<>();
    private final ConcurrentHashMap<String, Plugin> plugins = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Plugin> pluginsWithoutPermissions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Plugin> pluginsWithoutOptionalPermissions = new ConcurrentHashMap<>();
    private MultiValuedMap<String, String> pluginsByIncomingInterface = new ArrayListValuedHashMap<>();

    private final SharedPreferences settings;

    private final CopyOnWriteArrayList<PluginsChangedListener> pluginsChangedListeners = new CopyOnWriteArrayList<>();
    private Set<String> incomingCapabilities = new HashSet<>();

    public boolean supportsPacketType(String type) {
        if (incomingCapabilities == null) {
            return true;
        } else {
            return incomingCapabilities.contains(type);
        }
    }

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
        Computer,
        Tv;

        static public DeviceType FromString(String s) {
            if ("tablet".equals(s)) return Tablet;
            if ("phone".equals(s)) return Phone;
            if ("tv".equals(s)) return Tv;
            return Computer; //Default
        }

        public String toString() {
            switch (this) {
                case Tablet:
                    return "tablet";
                case Phone:
                    return "phone";
                case Tv:
                    return "tv";
                default:
                    return "desktop";
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
        this.protocolVersion = DeviceHelper.ProtocolVersion; //We don't know it yet
        this.deviceType = DeviceType.FromString(settings.getString("deviceType", "desktop"));

        //Assume every plugin is supported until addLink is called and we can get the actual list
        supportedPlugins = new Vector<>(PluginFactory.getAvailablePlugins());

        //Do not load plugins yet, the device is not present
        //reloadPluginsFromSettings();
    }

    //Device known via an incoming connection sent to us via a devicelink, we know everything but we don't trust it yet
    Device(Context context, NetworkPacket np, BaseLink dl) {

        //Log.e("Device","Constructor B");

        this.context = context;
        this.deviceId = np.getString("deviceId");
        this.name = context.getString(R.string.unknown_device); //We read it in addLink
        this.pairStatus = PairStatus.NotPaired;
        this.protocolVersion = 0;
        this.deviceType = DeviceType.Computer;

        settings = context.getSharedPreferences(deviceId, Context.MODE_PRIVATE);

        addLink(np, dl);
    }

    public String getName() {
        return StringUtils.defaultString(name, context.getString(R.string.unknown_device));
    }

    public Drawable getIcon() {
        int drawableId;
        switch (deviceType) {
            case Phone:
                drawableId = R.drawable.ic_device_phone_32dp;
                break;
            case Tablet:
                drawableId = R.drawable.ic_device_tablet_32dp;
                break;
            case Tv:
                drawableId = R.drawable.ic_device_tv_32dp;
                break;
            default:
                drawableId = R.drawable.ic_device_laptop_32dp;
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
        return protocolVersion - DeviceHelper.ProtocolVersion;
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
        for (BasePairingHandler ph : pairingHandlers.values()) {
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

        if (isPaired()) {
            for (PairingCallback cb : pairingCallback) {
                cb.pairingFailed(res.getString(R.string.error_already_paired));
            }
            return;
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
        preferences.edit().putBoolean(deviceId, true).apply();

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

        notificationId = (int) System.currentTimeMillis();

        Intent intent = new Intent(getContext(), MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_DEVICE_ID, getDeviceId());
        intent.putExtra(MainActivity.PAIR_REQUEST_STATUS, MainActivity.PAIRING_PENDING);
        PendingIntent pendingIntent = PendingIntent.getActivity(getContext(), 1, intent, PendingIntent.FLAG_CANCEL_CURRENT);

        Intent acceptIntent = new Intent(getContext(), MainActivity.class);
        Intent rejectIntent = new Intent(getContext(), MainActivity.class);

        acceptIntent.putExtra(MainActivity.EXTRA_DEVICE_ID, getDeviceId());
        //acceptIntent.putExtra("notificationId", notificationId);
        acceptIntent.putExtra(MainActivity.PAIR_REQUEST_STATUS, MainActivity.PAIRING_ACCEPTED);

        rejectIntent.putExtra(MainActivity.EXTRA_DEVICE_ID, getDeviceId());
        //rejectIntent.putExtra("notificationId", notificationId);
        rejectIntent.putExtra(MainActivity.PAIR_REQUEST_STATUS, MainActivity.PAIRING_REJECTED);

        PendingIntent acceptedPendingIntent = PendingIntent.getActivity(getContext(), 2, acceptIntent, PendingIntent.FLAG_ONE_SHOT);
        PendingIntent rejectedPendingIntent = PendingIntent.getActivity(getContext(), 4, rejectIntent, PendingIntent.FLAG_ONE_SHOT);

        Resources res = getContext().getResources();

        final NotificationManager notificationManager = ContextCompat.getSystemService(getContext(), NotificationManager.class);

        Notification noti = new NotificationCompat.Builder(getContext(), NotificationHelper.Channels.DEFAULT)
                .setContentTitle(res.getString(R.string.pairing_request_from, getName()))
                .setContentText(res.getString(R.string.tap_to_answer))
                .setContentIntent(pendingIntent)
                .setTicker(res.getString(R.string.pair_requested))
                .setSmallIcon(R.drawable.ic_notification)
                .addAction(R.drawable.ic_accept_pairing_24dp, res.getString(R.string.pairing_accept), acceptedPendingIntent)
                .addAction(R.drawable.ic_reject_pairing_24dp, res.getString(R.string.pairing_reject), rejectedPendingIntent)
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .build();

        NotificationHelper.notifyCompat(notificationManager, notificationId, noti);
    }

    public void hidePairingNotification() {
        final NotificationManager notificationManager = ContextCompat.getSystemService(getContext(),
                NotificationManager.class);
        notificationManager.cancel(notificationId);
    }

    //
    // ComputerLink-related functions
    //

    public boolean isReachable() {
        return !links.isEmpty();
    }

    public void addLink(NetworkPacket identityPacket, BaseLink link) {
        if (links.isEmpty()) {
            packetQueue = new DevicePacketQueue(this);
        }
        //FilesHelper.LogOpenFileCount();
        links.add(link);
        link.addPacketReceiver(this);

        this.protocolVersion = identityPacket.getInt("protocolVersion");

        if (identityPacket.has("deviceName")) {
            this.name = identityPacket.getString("deviceName", this.name);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString("deviceName", this.name);
            editor.apply();
        }

        if (identityPacket.has("deviceType")) {
            this.deviceType = DeviceType.FromString(identityPacket.getString("deviceType", "desktop"));
        }

        if (identityPacket.has("certificate")) {
            String certificateString = identityPacket.getString("certificate");

            try {
                byte[] certificateBytes = Base64.decode(certificateString, 0);
                certificate = SslHelper.parseCertificate(certificateBytes);
                Log.i("KDE/Device", "Got certificate ");
            } catch (Exception e) {
                Log.e("KDE/Device", "Error getting certificate", e);

            }
        }

        try {
            SharedPreferences globalSettings = PreferenceManager.getDefaultSharedPreferences(context);
            byte[] privateKeyBytes = Base64.decode(globalSettings.getString("privateKey", ""), 0);
            PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
            link.setPrivateKey(privateKey);
        } catch (Exception e) {
            Log.e("KDE/Device", "Exception reading our own private key", e); //Should not happen
        }

        Log.i("KDE/Device", "addLink " + link.getLinkProvider().getName() + " -> " + getName() + " active links: " + links.size());

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

        Set<String> outgoingCapabilities = identityPacket.getStringSet("outgoingCapabilities", null);
        Set<String> incomingCapabilities = identityPacket.getStringSet("incomingCapabilities", null);


        if (incomingCapabilities != null && outgoingCapabilities != null) {
            supportedPlugins = new Vector<>(PluginFactory.pluginsForCapabilities(incomingCapabilities, outgoingCapabilities));
        } else {
            supportedPlugins = new Vector<>(PluginFactory.getAvailablePlugins());
        }
        this.incomingCapabilities = incomingCapabilities;

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

        link.removePacketReceiver(this);
        links.remove(link);
        Log.i("KDE/Device", "removeLink: " + link.getLinkProvider().getName() + " -> " + getName() + " active links: " + links.size());
        if (links.isEmpty()) {
            reloadPluginsFromSettings();
            if (packetQueue != null) {
                packetQueue.disconnected();
                packetQueue = null;
            }
        }
    }

    @Override
    public void onPacketReceived(NetworkPacket np) {

        if (NetworkPacket.PACKET_TYPE_PAIR.equals(np.getType())) {

            Log.i("KDE/Device", "Pair package");

            for (BasePairingHandler ph : pairingHandlers.values()) {
                try {
                    ph.packageReceived(np);
                } catch (Exception e) {
                    Log.e("PairingPacketReceived", "Exception", e);
                }
            }
        } else if (isPaired()) {
            // pluginsByIncomingInterface may not be built yet
            if(pluginsByIncomingInterface.isEmpty()) {
                reloadPluginsFromSettings();
            }

            //If capabilities are not supported, iterate all plugins
            Collection<String> targetPlugins = pluginsByIncomingInterface.get(np.getType());
            if (!targetPlugins.isEmpty()) {
                for (String pluginKey : targetPlugins) {
                    Plugin plugin = plugins.get(pluginKey);
                    try {
                        plugin.onPacketReceived(np);
                    } catch (Exception e) {
                        Log.e("KDE/Device", "Exception in " + plugin.getPluginKey() + "'s onPacketReceived()", e);
                        //try { Log.e("KDE/Device", "NetworkPacket:" + np.serialize()); } catch (Exception _) { }
                    }
                }
            } else {
                Log.w("Device", "Ignoring packet with type " + np.getType() + " because no plugin can handle it");
            }
        } else {

            //Log.e("KDE/onPacketReceived","Device not paired, will pass package to unpairedPacketListeners");

            // If it is pair package, it should be captured by "if" at start
            // If not and device is paired, it should be captured by isPaired
            // Else unpair, this handles the situation when one device unpairs, but other dont know like unpairing when wi-fi is off

            unpair();

            //If capabilities are not supported, iterate through all plugins.
            Collection<String> targetPlugins = pluginsByIncomingInterface.get(np.getType());
            // When a mapping doesn't exist, an empty collection is added to the map and
            // then returned, so a null check is not necessary.
            if (!targetPlugins.isEmpty()) {
                for (String pluginKey : targetPlugins) {
                    Plugin plugin = plugins.get(pluginKey);
                    try {
                        plugin.onUnpairedDevicePacketReceived(np);
                    } catch (Exception e) {
                        Log.e("KDE/Device", "Exception in " + plugin.getDisplayName() + "'s onPacketReceived() in unPairedPacketListeners", e);
                    }
                }
            } else {
                Log.e("Device", "Ignoring packet with type " + np.getType() + " because no plugin can handle it");
            }
        }
    }

    public static abstract class SendPacketStatusCallback {
        public abstract void onSuccess();

        public abstract void onFailure(Throwable e);

        public void onProgressChanged(int percent) {
        }
    }

    private final SendPacketStatusCallback defaultCallback = new SendPacketStatusCallback() {
        @Override
        public void onSuccess() {
        }

        @Override
        public void onFailure(Throwable e) {
            Log.e("KDE/sendPacket", "Exception", e);
        }
    };

    @AnyThread
    public void sendPacket(NetworkPacket np) {
        sendPacket(np, -1, defaultCallback);
    }

    @AnyThread
    public void sendPacket(NetworkPacket np, int replaceID) {
        sendPacket(np, replaceID, defaultCallback);
    }

    @WorkerThread
    public boolean sendPacketBlocking(NetworkPacket np) {
        return sendPacketBlocking(np, defaultCallback);
    }

    @AnyThread
    public void sendPacket(final NetworkPacket np, final SendPacketStatusCallback callback) {
        sendPacket(np, -1, callback);
    }

    /**
     * Send a packet to the device asynchronously
     * @param np The packet
     * @param replaceID If positive, replaces all unsent packages with the same replaceID
     * @param callback A callback for success/failure
     */
    @AnyThread
    public void sendPacket(final NetworkPacket np, int replaceID, final SendPacketStatusCallback callback) {
        if (packetQueue == null) {
            callback.onFailure(new Exception("Device disconnected!"));
        } else {
            packetQueue.addPacket(np, replaceID, callback);
        }
    }

    /**
     * Check if we still have an unsent packet in the queue with the given ID.
     * If so, remove it from the queue and return it
     * @param replaceID The replace ID (must be positive)
     * @return The found packet, or null
     */
    public NetworkPacket getAndRemoveUnsentPacket(int replaceID) {
        if (packetQueue == null) {
            return null;
        } else {
            return packetQueue.getAndRemoveUnsentPacket(replaceID);
        }
    }

    /**
     * Send {@code np} over one of this device's connected {@link #links}.
     *
     * @param np       the packet to send
     * @param callback a callback that can receive realtime updates
     * @return true if the packet was sent ok, false otherwise
     * @see BaseLink#sendPacket(NetworkPacket, SendPacketStatusCallback)
     */
    @WorkerThread
    public boolean sendPacketBlocking(final NetworkPacket np, final SendPacketStatusCallback callback) {

        /*
        if (!m_outgoingCapabilities.contains(np.getType()) && !NetworkPacket.protocolPacketTypes.contains(np.getType())) {
            Log.e("Device/sendPacket", "Plugin tried to send an undeclared package: " + np.getType());
            Log.w("Device/sendPacket", "Declared outgoing package types: " + Arrays.toString(m_outgoingCapabilities.toArray()));
        }
        */

        boolean success = false;
        //Make a copy to avoid concurrent modification exception if the original list changes
        for (final BaseLink link : links) {
            if (link == null)
                continue; //Since we made a copy, maybe somebody destroyed the link in the meanwhile
            success = link.sendPacket(np, callback);
            if (success) break; //If the link didn't call sendSuccess(), try the next one
        }

        if (!success) {
            Log.e("KDE/sendPacket", "No device link (of " + links.size() + " available) could send the package. Packet " + np.getType() + " to " + name + " lost!");
        }

        return success;

    }
    //
    // Plugin-related functions
    //

    @Nullable
    public <T extends Plugin> T getPlugin(Class<T> pluginClass) {
        Plugin plugin = getPlugin(Plugin.getPluginKey(pluginClass));
        return (T) plugin;
    }

    @Nullable
    public Plugin getPlugin(String pluginKey) {
        return plugins.get(pluginKey);
    }

    private synchronized boolean addPlugin(final String pluginKey) {
        Plugin existing = plugins.get(pluginKey);
        if (existing != null) {

            if (existing.getMinSdk() > Build.VERSION.SDK_INT) {
                Log.i("KDE/addPlugin", "Min API level not fulfilled " + pluginKey);
                return false;
            }

            //Log.w("KDE/addPlugin","plugin already present:" + pluginKey);
            if (existing.checkOptionalPermissions()) {
                Log.i("KDE/addPlugin", "Optional Permissions OK " + pluginKey);
                pluginsWithoutOptionalPermissions.remove(pluginKey);
            } else {
                Log.e("KDE/addPlugin", "No optional permission " + pluginKey);
                pluginsWithoutOptionalPermissions.put(pluginKey, existing);
            }
            return true;
        }

        final Plugin plugin = PluginFactory.instantiatePluginForDevice(context, pluginKey, this);
        if (plugin == null) {
            Log.e("KDE/addPlugin", "could not instantiate plugin: " + pluginKey);
            return false;
        }

        if (plugin.getMinSdk() > Build.VERSION.SDK_INT) {
            Log.i("KDE/addPlugin", "Min API level not fulfilled" + pluginKey);
            return false;
        }

        boolean success;
        try {
            success = plugin.onCreate();
        } catch (Exception e) {
            success = false;
            Log.e("KDE/addPlugin", "plugin failed to load " + pluginKey, e);
        }

        plugins.put(pluginKey, plugin);

        if (!plugin.checkRequiredPermissions()) {
            Log.e("KDE/addPlugin", "No permission " + pluginKey);
            plugins.remove(pluginKey);
            pluginsWithoutPermissions.put(pluginKey, plugin);
            success = false;
        } else {
            Log.i("KDE/addPlugin", "Permissions OK " + pluginKey);
            pluginsWithoutPermissions.remove(pluginKey);

            if (plugin.checkOptionalPermissions()) {
                Log.i("KDE/addPlugin", "Optional Permissions OK " + pluginKey);
                pluginsWithoutOptionalPermissions.remove(pluginKey);
            } else {
                Log.e("KDE/addPlugin", "No optional permission " + pluginKey);
                pluginsWithoutOptionalPermissions.put(pluginKey, plugin);
            }
        }

        return success;
    }

    private synchronized boolean removePlugin(String pluginKey) {

        Plugin plugin = plugins.remove(pluginKey);

        if (plugin == null) {
            return false;
        }

        try {
            plugin.onDestroy();
            //Log.e("removePlugin","removed " + pluginKey);
        } catch (Exception e) {
            Log.e("KDE/removePlugin", "Exception calling onDestroy for plugin " + pluginKey, e);
        }

        return true;
    }

    public void setPluginEnabled(String pluginKey, boolean value) {
        settings.edit().putBoolean(pluginKey, value).apply();
        reloadPluginsFromSettings();
    }

    public boolean isPluginEnabled(String pluginKey) {
        boolean enabledByDefault = PluginFactory.getPluginInfo(pluginKey).isEnabledByDefault();
        return settings.getBoolean(pluginKey, enabledByDefault);
    }

    public void reloadPluginsFromSettings() {
        MultiValuedMap<String, String> newPluginsByIncomingInterface = new ArrayListValuedHashMap<>();

        for (String pluginKey : supportedPlugins) {
            PluginFactory.PluginInfo pluginInfo = PluginFactory.getPluginInfo(pluginKey);

            boolean pluginEnabled = false;
            boolean listenToUnpaired = pluginInfo.listenToUnpaired();
            if ((isPaired() || listenToUnpaired) && isReachable()) {
                pluginEnabled = isPluginEnabled(pluginKey);
            }

            if (pluginEnabled) {
                boolean success = addPlugin(pluginKey);
                if (success) {
                    for (String packageType : pluginInfo.getSupportedPacketTypes()) {
                        newPluginsByIncomingInterface.put(packageType, pluginKey);
                    }
                } else {
                    removePlugin(pluginKey);
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

    public ConcurrentHashMap<String, Plugin> getLoadedPlugins() {
        return plugins;
    }

    public ConcurrentHashMap<String, Plugin> getPluginsWithoutPermissions() {
        return pluginsWithoutPermissions;
    }

    public ConcurrentHashMap<String, Plugin> getPluginsWithoutOptionalPermissions() {
        return pluginsWithoutOptionalPermissions;
    }

    public void addPluginsChangedListener(PluginsChangedListener listener) {
        pluginsChangedListeners.add(listener);
    }

    public void removePluginsChangedListener(PluginsChangedListener listener) {
        pluginsChangedListeners.remove(listener);
    }

    public void disconnect() {
        for (BaseLink link : links) {
            link.disconnect();
        }
    }

    public boolean deviceShouldBeKeptAlive() {

        SharedPreferences preferences = context.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE);
        if (preferences.contains(getDeviceId())) {
            //Log.e("DeviceShouldBeKeptAlive", "because it's a paired device");
            return true; //Already paired
        }

        for (BaseLink l : links) {
            if (l.linkShouldBeKeptAlive()) {
                return true;
            }
        }
        return false;
    }

    public List<String> getSupportedPlugins() {
        return supportedPlugins;
    }

}
