/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import org.kde.kdeconnect.ui.background.ConnectionRecovery
import android.util.Log
import androidx.annotation.MainThread
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.kde.kdeconnect.backends.BaseLinkProvider
import org.kde.kdeconnect.backends.BaseLinkProvider.ConnectionReceiver
import org.kde.kdeconnect.backends.bluetooth.BluetoothLinkProvider
import org.kde.kdeconnect.backends.lan.LanLinkProvider
import org.kde.kdeconnect.helpers.NotificationHelper
import org.kde.kdeconnect.plugins.clipboard.ClipboardFloatingActivity
import org.kde.kdeconnect.plugins.clipboard.ClipboardPlugin
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect_tp.R

/**
 * This class (still) does 3 things:
 * - Keeps the app running by creating a foreground notification.
 * - Holds references to the active LinkProviders, but doesn't handle the DeviceLink those create (the KdeConnect class does that).
 * - Listens for network connectivity changes and tells the LinkProviders to re-check for devices.
 * It can be started by the KdeConnectBroadcastReceiver on some events or when the MainActivity is launched.
 */
class BackgroundService : Service() {
    private lateinit var applicationInstance: KdeConnect

    private val linkProviders = mutableListOf<BaseLinkProvider>()
    private val screenReceiver = KdeConnectBroadcastReceiver()
    private val availableNetworks = mutableSetOf<Network>()
    private val recovery by lazy {
        ConnectionRecovery(this) {
            // Retry LAN discovery only: restarting Bluetooth would interrupt an existing link.
            linkProviders.filterIsInstance<LanLinkProvider>().forEach { it.onNetworkChange(null) }
        }
    }
    private var receiverRegistered = false
    private var connectivityManager: ConnectivityManager? = null
    private val networkCallback = object : NetworkCallback() {
        override fun onAvailable(network: Network) {
            synchronized(availableNetworks) { availableNetworks.add(network) }
            isConnectedToNonCellularNetwork.postValue(true)
            onNetworkChange(network)
            recovery.networkChanged(true)
        }

        override fun onLost(network: Network) {
            val hasNetwork = synchronized(availableNetworks) {
                availableNetworks.remove(network)
                availableNetworks.isNotEmpty()
            }
            isConnectedToNonCellularNetwork.postValue(hasNetwork)
            onNetworkChange(network)
            recovery.networkChanged(hasNetwork)
        }
    }

    /** Indicates whether device is connected over wifi / usb / bluetooth / (anything other than cellular) */
    val isConnectedToNonCellularNetwork: LiveData<Boolean>
        field = MutableLiveData<Boolean>()

    fun updateForegroundNotification() {
        if (NotificationHelper.isPersistentNotificationEnabled(this)) {
            // Update the foreground notification with the currently connected device list
            val notificationManager = getSystemService<NotificationManager>()
            notificationManager?.notify(FOREGROUND_NOTIFICATION_ID, createForegroundNotification())
        }
    }

    private fun registerLinkProviders() {
        linkProviders.add(LanLinkProvider(this))
        //linkProviders.add(LoopbackLinkProvider(this))
        linkProviders.add(BluetoothLinkProvider(this))
    }

    fun onNetworkChange(network: Network?) {
        if (!initialized) {
            Log.d(LOG_TAG, "ignoring onNetworkChange called before the service is initialized")
            return
        }
        Log.d(LOG_TAG, "onNetworkChange")
        for (linkProvider in linkProviders) {
            linkProvider.onNetworkChange(network)
        }
    }

    fun addConnectionListener(connectionReceiver: ConnectionReceiver) {
        for (linkProvider in linkProviders) {
            linkProvider.addConnectionReceiver(connectionReceiver)
        }
    }

    fun removeConnectionListener(connectionReceiver: ConnectionReceiver) {
        for (linkProvider in linkProviders) {
            linkProvider.removeConnectionReceiver(connectionReceiver)
        }
    }

    /** This will called only once, even if we launch the service intent several times */
    @MainThread
    override fun onCreate() {
        super.onCreate()
        Log.d("KdeConnect/BgService", "onCreate")
        this.applicationInstance = KdeConnect.getInstance()
        instance = this
        // Promote before starting discovery: Android gives a newly started service only a
        // short deadline to enter foreground mode.
        if (!promoteToForeground()) {
            stopSelf()
            return
        }

        KdeConnect.getInstance().addDeviceListChangedCallback("BackgroundService", this::updateForegroundNotification)

        // Register screen on listener
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        filter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
        // See: https://developer.android.com/reference/android/net/ConnectivityManager.html#CONNECTIVITY_ACTION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        }
        registerReceiver(screenReceiver, filter)
        receiverRegistered = true

        registerLinkProviders()
        addConnectionListener(applicationInstance.connectionListener) // Link Providers need to be already registered
        for (linkProvider in linkProviders) {
            linkProvider.onStart()
        }
        initialized = true
        recovery.start()
        connectivityManager = getSystemService<ConnectivityManager>()
        connectivityManager?.registerNetworkCallback(createNonCellularNetworkRequestBuilder().build(), networkCallback)
    }

    fun changePersistentNotificationVisibility(visible: Boolean) {
        if (visible) {
            updateForegroundNotification()
        }
        else {
            stopForeground(true) // Pass STOP_FOREGROUND_REMOVE instead of true when we only support API 24+
            Start(this)
        }
    }

    private fun createForegroundNotification(): Notification {
        // Why is this needed: https://developer.android.com/guide/components/services#Foreground

        val connectedDevices = mutableListOf<String>()
        val connectedDeviceIds = mutableListOf<String>()
        for (device in applicationInstance.devices.values) {
            if (device.isReachable && device.isPaired) {
                connectedDeviceIds.add(device.deviceId)
                connectedDevices.add(device.name)
            }
        }

        val intent = Intent(this, MainActivity::class.java)
        // PendingIntent matching ignores extras, so add an action to avoid other notifications that also
        // open MainActivity replacing the device id extra below.
        intent.action = "org.kde.kdeconnect.FOREGROUND_NOTIFICATION"
        if (connectedDeviceIds.size == 1) {
            // Force open screen of the only connected device
            intent.putExtra(MainActivity.EXTRA_DEVICE_ID, connectedDeviceIds[0])
        }

        val pi = PendingIntent.getActivity(this, 0, intent, UPDATE_IMMUTABLE_FLAGS)
        val notification = NotificationCompat.Builder(this, NotificationHelper.Channels.PERSISTENT).apply {
            setSmallIcon(R.drawable.ic_notification)
            setOngoing(true)
            setContentIntent(pi)
            setPriority(NotificationCompat.PRIORITY_LOW)
            setSilent(true)
            setContentTitle(getString(R.string.kde_connect))
            setShowWhen(false)
            setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            setAutoCancel(false)
            setGroup("BackgroundService")
        }

        if (connectedDevices.isEmpty()) {
            notification.setContentText(getString(R.string.foreground_notification_no_devices))
        }
        else {
            notification.setContentText(getString(R.string.foreground_notification_devices, connectedDevices.joinToString(", ")))

            // Adding an action button to send clipboard manually in Android 10 and later.
            if (!ClipboardPlugin.canSyncAutomatically(this)) {
                val sendClipboard = ClipboardFloatingActivity.getIntent(this, true)
                sendClipboard.action = "org.kde.kdeconnect.FOREGROUND_NOTIFICATION_SEND_CLIPBOARD"
                val sendPendingClipboard = PendingIntent.getActivity(this, 0, sendClipboard, UPDATE_IMMUTABLE_FLAGS)
                notification.addAction(0, getString(R.string.foreground_notification_send_clipboard), sendPendingClipboard)
            }

        }
        return notification.build()
    }

    override fun onDestroy() {
        Log.d("KdeConnect/BgService", "onDestroy")
        initialized = false
        recovery.stop()
        synchronized(availableNetworks) { availableNetworks.clear() }
        if (receiverRegistered) unregisterReceiver(screenReceiver)
        receiverRegistered = false
        connectivityManager?.let { manager -> runCatching { manager.unregisterNetworkCallback(networkCallback) } }
        connectivityManager = null
        if (instance === this) instance = null
        for (linkProvider in linkProviders) {
            linkProvider.onStop()
        }
        KdeConnect.getInstance().removeDeviceListChangedCallback("BackgroundService")
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(LOG_TAG, "onStartCommand")
        if (!promoteToForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent != null && intent.getBooleanExtra("refresh", false)) {
            onNetworkChange(null)
            recovery.requestImmediate()
        }
        return START_STICKY
    }

    private fun promoteToForeground(): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification())
        }
        true
    } catch (e: RuntimeException) {
        Log.w(LOG_TAG, "Foreground service could not start", e)
        false
    }

    companion object {
        const val LOG_TAG = "KDE/BackgroundService"

        const val UPDATE_IMMUTABLE_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        private const val FOREGROUND_NOTIFICATION_ID = 1

        var instance: BackgroundService? = null
            private set

        private var initialized = false

        private fun createNonCellularNetworkRequestBuilder(): NetworkRequest.Builder {
            return NetworkRequest.Builder().apply {
                // NetworkRequest.Builder requires NET_CAPABILITY_NOT_VPN by default.
                // Remove it so this request can match VPN networks as well.
                removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                addTransportType(NetworkCapabilities.TRANSPORT_BLUETOOTH)
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
                    addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
                }
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S) {
                    addTransportType(NetworkCapabilities.TRANSPORT_USB)
                    addTransportType(NetworkCapabilities.TRANSPORT_LOWPAN)
                }
            }
        }

        fun Start(context: Context) {
            Log.d(LOG_TAG, "Start")
            val intent = Intent(context, BackgroundService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: RuntimeException) {
                Log.w(LOG_TAG, "Android did not allow the connection service to start", e)
            }
        }

        fun ForceRefreshConnections(context: Context) {
            Log.d(LOG_TAG, "ForceRefreshConnections")
            val intent = Intent(context, BackgroundService::class.java)
            intent.putExtra("refresh", true)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: RuntimeException) {
                Log.w(LOG_TAG, "Android did not allow the connection service to start", e)
            }
        }
    }
}
