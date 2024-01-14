/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
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
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.kde.kdeconnect.Backends.BaseLink;
import org.kde.kdeconnect.Helpers.DeviceHelper;
import org.kde.kdeconnect.Helpers.NotificationHelper;
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect.UserInterface.MainActivity;
import org.kde.kdeconnect_tp.R;

import java.io.IOException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class Device implements BaseLink.PacketReceiver {

    private final Context context;

    final DeviceInfo deviceInfo;

    private int notificationId;
    PairingHandler pairingHandler;
    private final CopyOnWriteArrayList<PairingHandler.PairingCallback> pairingCallbacks = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<BaseLink> links = new CopyOnWriteArrayList<>();
    private DevicePacketQueue packetQueue;
    private List<String> supportedPlugins;
    private final ConcurrentHashMap<String, Plugin> plugins = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Plugin> pluginsWithoutPermissions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Plugin> pluginsWithoutOptionalPermissions = new ConcurrentHashMap<>();
    private MultiValuedMap<String, String> pluginsByIncomingInterface = new ArrayListValuedHashMap<>();
    private final SharedPreferences settings;
    private final CopyOnWriteArrayList<PluginsChangedListener> pluginsChangedListeners = new CopyOnWriteArrayList<>();
    private String connectivityType;

    public boolean supportsPacketType(String type) {
        if (deviceInfo.incomingCapabilities == null) {
            return true;
        } else {
            return deviceInfo.incomingCapabilities.contains(type);
        }
    }

    public String getConnectivityType() {
        return connectivityType;
    }

    public interface PluginsChangedListener {
        void onPluginsChanged(@NonNull Device device);
    }

    /**
     * Constructor for remembered, already-trusted devices.
     * Given the deviceId, it will load the other properties from SharedPreferences.
     */
    Device(@NonNull Context context, @NonNull String deviceId) throws CertificateException {
        this.context = context;
        this.settings = context.getSharedPreferences(deviceId, Context.MODE_PRIVATE);
        this.deviceInfo = DeviceInfo.loadFromSettings(context, deviceId, settings);
        this.pairingHandler = new PairingHandler(this, pairingCallback, PairingHandler.PairState.Paired);
        this.supportedPlugins = new Vector<>(PluginFactory.getAvailablePlugins()); // Assume all are supported until we receive capabilities
        this.connectivityType =  "";
        Log.i("Device","Loading trusted device: " + deviceInfo.name);
    }

    /**
     * Constructor for devices discovered but not trusted yet.
     * Gets the DeviceInfo by calling link.getDeviceInfo() on the link passed.
     * This constructor also calls addLink() with the link you pass to it, since it's not legal to have an unpaired Device with 0 links.
     */
    Device(@NonNull Context context, @NonNull BaseLink link) {
        this.context = context;
        this.deviceInfo = link.getDeviceInfo();
        this.settings = context.getSharedPreferences(deviceInfo.id, Context.MODE_PRIVATE);
        this.pairingHandler = new PairingHandler(this, pairingCallback, PairingHandler.PairState.NotPaired);
        this.supportedPlugins = new Vector<>(PluginFactory.getAvailablePlugins()); // Assume all are supported until we receive capabilities
        this.connectivityType = link.getLinkProvider().getName();
        Log.i("Device","Creating untrusted device: "+ deviceInfo.name);
        addLink(link);
    }

    public String getName() {
        return deviceInfo.name;
    }

    public Drawable getIcon() {
        return  deviceInfo.type.getIcon(context);
    }

    public DeviceType getDeviceType() {
        return deviceInfo.type;
    }

    public String getDeviceId() {
        return deviceInfo.id;
    }

    public Certificate getCertificate() {
        return deviceInfo.certificate;
    }

    public Context getContext() {
        return context;
    }

    //Returns 0 if the version matches, < 0 if it is older or > 0 if it is newer
    public int compareProtocolVersion() {
        return deviceInfo.protocolVersion - DeviceHelper.ProtocolVersion;
    }


    //
    // Pairing-related functions
    //

    public boolean isPaired() {
        return pairingHandler.getState() == PairingHandler.PairState.Paired;
    }

    public boolean isPairRequested() {
        return pairingHandler.getState() == PairingHandler.PairState.Requested;
    }

    public boolean isPairRequestedByPeer() {
        return pairingHandler.getState() == PairingHandler.PairState.RequestedByPeer;
    }

    public void addPairingCallback(PairingHandler.PairingCallback callback) {
        pairingCallbacks.add(callback);
    }

    public void removePairingCallback(PairingHandler.PairingCallback callback) {
        pairingCallbacks.remove(callback);
    }

    public void requestPairing() {
        pairingHandler.requestPairing();
    }

    public void unpair() {
        pairingHandler.unpair();
    }

    /* This method is called after accepting pair request form GUI */
    public void acceptPairing() {
        Log.i("KDE/Device", "Accepted pair request started by the other device");
        pairingHandler.acceptPairing();
    }

    /* This method is called after rejecting pairing from GUI */
    public void cancelPairing() {
        Log.i("KDE/Device", "This side cancelled the pair request");
        pairingHandler.cancelPairing();
    }

    PairingHandler.PairingCallback pairingCallback = new PairingHandler.PairingCallback() {
        @Override
        public void incomingPairRequest() {
            displayPairingNotification();
            for (PairingHandler.PairingCallback cb : pairingCallbacks) {
                cb.incomingPairRequest();
            }
        }

        @Override
        public void pairingSuccessful() {
            Log.i("Device", "pairing successful, adding to trusted devices list");

            hidePairingNotification();

            // Store current device certificate so we can check it in the future (TOFU)
            deviceInfo.saveInSettings(Device.this.settings);

            // Store as trusted device
            SharedPreferences preferences = context.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE);
            preferences.edit().putBoolean(deviceInfo.id, true).apply();

            try {
                reloadPluginsFromSettings();

                for (PairingHandler.PairingCallback cb : pairingCallbacks) {
                    cb.pairingSuccessful();
                }
            } catch (Exception e) {
                Log.e("PairingHandler", "Exception in pairingSuccessful. Not unpairing because saving the trusted device succeeded");
                e.printStackTrace();
            }
        }

        @Override
        public void pairingFailed(String error) {
            hidePairingNotification();
            for (PairingHandler.PairingCallback cb : pairingCallbacks) {
                cb.pairingFailed(error);
            }
        }

        @Override
        public void unpaired() {
            Log.i("Device", "unpaired, removing from trusted devices list");
            SharedPreferences preferences = context.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE);
            preferences.edit().remove(deviceInfo.id).apply();

            SharedPreferences devicePreferences = context.getSharedPreferences(deviceInfo.id, Context.MODE_PRIVATE);
            devicePreferences.edit().clear().apply();

            for (PairingHandler.PairingCallback cb : pairingCallbacks) {
                cb.unpaired();
            }

            reloadPluginsFromSettings();
        }
    };

    //
    // Notification related methods used during pairing
    //

    public void displayPairingNotification() {

        hidePairingNotification();

        notificationId = (int) System.currentTimeMillis();

        Intent intent = new Intent(getContext(), MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_DEVICE_ID, getDeviceId());
        intent.putExtra(MainActivity.PAIR_REQUEST_STATUS, MainActivity.PAIRING_PENDING);
        PendingIntent pendingIntent = PendingIntent.getActivity(getContext(), 1, intent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE);

        Intent acceptIntent = new Intent(getContext(), MainActivity.class);
        Intent rejectIntent = new Intent(getContext(), MainActivity.class);

        acceptIntent.putExtra(MainActivity.EXTRA_DEVICE_ID, getDeviceId());
        acceptIntent.putExtra(MainActivity.PAIR_REQUEST_STATUS, MainActivity.PAIRING_ACCEPTED);

        rejectIntent.putExtra(MainActivity.EXTRA_DEVICE_ID, getDeviceId());
        rejectIntent.putExtra(MainActivity.PAIR_REQUEST_STATUS, MainActivity.PAIRING_REJECTED);

        PendingIntent acceptedPendingIntent = PendingIntent.getActivity(getContext(), 2, acceptIntent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_MUTABLE);
        PendingIntent rejectedPendingIntent = PendingIntent.getActivity(getContext(), 4, rejectIntent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_MUTABLE);

        Resources res = getContext().getResources();

        final NotificationManager notificationManager = ContextCompat.getSystemService(getContext(), NotificationManager.class);

        String verificationKeyShort = SslHelper.getVerificationKey(SslHelper.certificate, deviceInfo.certificate).substring(8);

        Notification noti = new NotificationCompat.Builder(getContext(), NotificationHelper.Channels.DEFAULT)
                .setContentTitle(res.getString(R.string.pairing_request_from, getName()))
                .setContentText(res.getString(R.string.pairing_verification_code, verificationKeyShort))
                .setTicker(res.getString(R.string.pair_requested))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
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
    // Link-related functions
    //

    public boolean isReachable() {
        return !links.isEmpty();
    }

    public void addLink(BaseLink link) {
        if (links.isEmpty()) {
            packetQueue = new DevicePacketQueue(this);
        }
        //FilesHelper.LogOpenFileCount();

        links.add(link);

        List linksToSort = Arrays.asList(links.toArray());
        Collections.sort(linksToSort, (Comparator<BaseLink>) (o1, o2) -> Integer.compare(o2.getLinkProvider().getPriority(), o1.getLinkProvider().getPriority()));
        links.clear();
        links.addAll(linksToSort);

        link.addPacketReceiver(this);

        boolean hasChanges = updateDeviceInfo(link.getDeviceInfo());

        if (hasChanges || links.size() == 1) {
            reloadPluginsFromSettings();
        }
    }

    public void removeLink(BaseLink link) {
        //FilesHelper.LogOpenFileCount();

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

    public boolean updateDeviceInfo(@NonNull DeviceInfo newDeviceInfo) {

        boolean hasChanges = false;
        if (!deviceInfo.name.equals(newDeviceInfo.name) || deviceInfo.type != newDeviceInfo.type) {
            hasChanges = true;
            deviceInfo.name = newDeviceInfo.name;
            deviceInfo.type = newDeviceInfo.type;
            if (isPaired()) {
                deviceInfo.saveInSettings(settings);
            }
        }

        if (deviceInfo.outgoingCapabilities != newDeviceInfo.outgoingCapabilities ||
                deviceInfo.incomingCapabilities != newDeviceInfo.incomingCapabilities) {
            if (newDeviceInfo.outgoingCapabilities != null && newDeviceInfo.incomingCapabilities != null) {
                hasChanges = true;
                Log.i("updateDeviceInfo", "Updating supported plugins according to new capabilities");
                supportedPlugins = new Vector<>(PluginFactory.pluginsForCapabilities(newDeviceInfo.incomingCapabilities, newDeviceInfo.outgoingCapabilities));
            }
        }

        return hasChanges;
    }

    @Override
    public void onPacketReceived(@NonNull NetworkPacket np) {

        DeviceStats.countReceived(getDeviceId(), np.getType());

        if (NetworkPacket.PACKET_TYPE_PAIR.equals(np.getType())) {
            Log.i("KDE/Device", "Pair packet");
            pairingHandler.packetReceived(np);
        } else if (isPaired()) {
            // pluginsByIncomingInterface may not be built yet
            if(pluginsByIncomingInterface.isEmpty()) {
                reloadPluginsFromSettings();
            }

            Collection<String> targetPlugins = pluginsByIncomingInterface.get(np.getType());
            if (!targetPlugins.isEmpty()) { // When a key doesn't exist the multivaluemap returns an empty collection, so we don't need to check for null
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

            //Log.e("KDE/onPacketReceived","Device not paired, will pass packet to unpairedPacketListeners");

            // If it is pair packet, it should be captured by "if" at start
            // If not and device is paired, it should be captured by isPaired
            // Else unpair, this handles the situation when one device unpairs, but other dont know like unpairing when wi-fi is off

            unpair();

            // The following code is NOT USED. It adds support for receiving packets from not trusted devices, but as of March 2023 no plugin implements "onUnpairedDevicePacketReceived".
            Collection<String> targetPlugins = pluginsByIncomingInterface.get(np.getType());
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

        public void onPayloadProgressChanged(int percent) {
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
    public void sendPacket(@NonNull NetworkPacket np) {
        sendPacket(np, -1, defaultCallback);
    }

    @AnyThread
    public void sendPacket(@NonNull NetworkPacket np, int replaceID) {
        sendPacket(np, replaceID, defaultCallback);
    }

    @WorkerThread
    public boolean sendPacketBlocking(@NonNull NetworkPacket np) {
        return sendPacketBlocking(np, defaultCallback);
    }

    @AnyThread
    public void sendPacket(@NonNull final NetworkPacket np, @NonNull final SendPacketStatusCallback callback) {
        sendPacket(np, -1, callback);
    }

    /**
     * Send a packet to the device asynchronously
     * @param np The packet
     * @param replaceID If positive, replaces all unsent packets with the same replaceID
     * @param callback A callback for success/failure
     */
    @AnyThread
    public void sendPacket(@NonNull final NetworkPacket np, int replaceID, @NonNull final SendPacketStatusCallback callback) {
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

    @WorkerThread
    public boolean sendPacketBlocking(@NonNull final NetworkPacket np, @NonNull final SendPacketStatusCallback callback) {
        return sendPacketBlocking(np, callback, false);
    }

    /**
     * Send {@code np} over one of this device's connected {@link #links}.
     *
     * @param np                        the packet to send
     * @param callback                  a callback that can receive realtime updates
     * @param sendPayloadFromSameThread when set to true and np contains a Payload, this function
     *                                  won't return until the Payload has been received by the
     *                                  other end, or times out after 10 seconds
     * @return true if the packet was sent ok, false otherwise
     * @see BaseLink#sendPacket(NetworkPacket, SendPacketStatusCallback, boolean)
     */
    @WorkerThread
    public boolean sendPacketBlocking(@NonNull final NetworkPacket np, @NonNull final SendPacketStatusCallback callback, boolean sendPayloadFromSameThread) {

        boolean success = false;
        for (final BaseLink link : links) {
            if (link == null) continue;
            try {
                success = link.sendPacket(np, callback, sendPayloadFromSameThread);
            } catch (IOException e) {
                e.printStackTrace();
            }
            DeviceStats.countSent(getDeviceId(), np.getType(), success);
            if (success) break;
        }

        if (!success) {
            Log.e("KDE/sendPacket", "No device link (of " + links.size() + " available) could send the packet. Packet " + np.getType() + " to " + deviceInfo.name + " lost!");
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

    @Nullable
    public Plugin getPluginIncludingWithoutPermissions(String pluginKey) {
        Plugin p = plugins.get(pluginKey);
        if (p == null) {
            p = pluginsWithoutPermissions.get(pluginKey);
        }
        return p;
    }

    private synchronized boolean addPlugin(final String pluginKey) {
        Plugin existing = plugins.get(pluginKey);
        if (existing != null) {

            if (!existing.isCompatible()) {
                Log.d("KDE/addPlugin", "Minimum requirements (e.g. API level) not fulfilled " + pluginKey);
                return false;
            }

            //Log.w("KDE/addPlugin","plugin already present:" + pluginKey);
            if (existing.checkOptionalPermissions()) {
                Log.d("KDE/addPlugin", "Optional Permissions OK " + pluginKey);
                pluginsWithoutOptionalPermissions.remove(pluginKey);
            } else {
                Log.d("KDE/addPlugin", "No optional permission " + pluginKey);
                pluginsWithoutOptionalPermissions.put(pluginKey, existing);
            }
            return true;
        }

        final Plugin plugin = PluginFactory.instantiatePluginForDevice(context, pluginKey, this);
        if (plugin == null) {
            Log.e("KDE/addPlugin", "could not instantiate plugin: " + pluginKey);
            return false;
        }

        if (!plugin.isCompatible()) {
            Log.d("KDE/addPlugin", "Minimum requirements (e.g. API level) not fulfilled " + pluginKey);
            return false;
        }

        if (!plugin.checkRequiredPermissions()) {
            Log.d("KDE/addPlugin", "No permission " + pluginKey);
            plugins.remove(pluginKey);
            pluginsWithoutPermissions.put(pluginKey, plugin);
            return false;
        } else {
            Log.d("KDE/addPlugin", "Permissions OK " + pluginKey);
            plugins.put(pluginKey, plugin);
            pluginsWithoutPermissions.remove(pluginKey);
            if (plugin.checkOptionalPermissions()) {
                Log.d("KDE/addPlugin", "Optional Permissions OK " + pluginKey);
                pluginsWithoutOptionalPermissions.remove(pluginKey);
            } else {
                Log.d("KDE/addPlugin", "No optional permission " + pluginKey);
                pluginsWithoutOptionalPermissions.put(pluginKey, plugin);
            }
        }

        try {
            return plugin.onCreate();
        } catch (Exception e) {
            Log.e("KDE/addPlugin", "plugin failed to load " + pluginKey, e);
            return false;
        }
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
        Log.i("Device", deviceInfo.name +": reloading plugins");
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
                    for (String packetType : pluginInfo.getSupportedPacketTypes()) {
                        newPluginsByIncomingInterface.put(packetType, pluginKey);
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

    public List<String> getSupportedPlugins() {
        return supportedPlugins;
    }

}
