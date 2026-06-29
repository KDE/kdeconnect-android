package org.kde.kdeconnect.plugins.bluetoothhandoff

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginFactory.LoadablePlugin
import org.kde.kdeconnect_tp.R
import kotlin.coroutines.resume


@LoadablePlugin
class BluetoothHandoffPlugin : Plugin() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override val displayName: String
        get() = context.resources.getString(R.string.pref_plugin_bluetooth_handoff)

    override val description: String
        get() = context.resources.getString(R.string.pref_plugin_bluetooth_handoff_desc)

    override val supportedPacketTypes: Array<String> = arrayOf(
        PACKET_TYPE_DEVICE_LIST_REQUEST,
        PACKET_TYPE_DISCONNECT_REQUEST,
        PACKET_TYPE_CONNECT_REQUEST,
    )
    override val outgoingPacketTypes: Array<String> = arrayOf(
        PACKET_TYPE_DEVICE_LIST,
        PACKET_TYPE_DISCONNECT_RESPONSE,
        PACKET_TYPE_CONNECT_RESPONSE,
    )

    override val requiredPermissions: Array<String> = buildList {
        add(Manifest.permission.BLUETOOTH_ADMIN)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.BLUETOOTH_ADMIN)
        }
    }.toTypedArray()

    private fun bluetoothAdapter(): BluetoothAdapter {
        val bluetooth = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return bluetooth.adapter
    }

    private fun supportedProfiles(): Array<Int> = buildList {
        add(BluetoothProfile.A2DP)
        add(BluetoothProfile.HEADSET)
        add(BluetoothProfile.GATT)
        add(BluetoothProfile.GATT_SERVER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            add(BluetoothProfile.HID_DEVICE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(BluetoothProfile.HEARING_AID)
        }
    }.toTypedArray()


    @SuppressLint("MissingPermission")
    private fun getConnectedDevices(): List<BluetoothDevice> {
        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        val connectedDevices =
            supportedProfiles().flatMap { profile -> bluetoothManager.getConnectedDevices(profile) }
                .distinctBy { it.address }
        return connectedDevices
    }


    private suspend fun connectDevice(
        adapter: BluetoothAdapter,
        device: BluetoothDevice,
    ) {
        supportedProfiles().forEach { profile ->
            connectProfile(adapter, device, profile)
        }
    }

    private suspend fun connectProfile(
        adapter: BluetoothAdapter, device: BluetoothDevice, profile: Int
    ): Boolean = suspendCancellableCoroutine { continuation ->
        adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                val result = try {
                    val method =
                        proxy.javaClass.getDeclaredMethod("connect", BluetoothDevice::class.java)
                    method.isAccessible = true
                    val status = method.invoke(proxy, device) as Boolean
                    Log.i("BluetoothHandoff", "Connect using profile $profile result = $status")
                    status
                } catch (e: Exception) {
                    Log.w("BluetoothHandoff", "connect() not available for profile $profile", e)
                    false
                } finally {
                    adapter.closeProfileProxy(profile, proxy) // always release the proxy
                }
                if (continuation.isActive) continuation.resume(result)
            }

            override fun onServiceDisconnected(profile: Int) {
                Log.i("BluetoothHandoff", "profile [$profile] disconnected")
                if (continuation.isActive) continuation.resume(false)
            }
        }, profile)
    }

    private suspend fun disconnectDevice(
        adapter: BluetoothAdapter,
        device: BluetoothDevice,
    ) {
        supportedProfiles().forEach { profile ->
            disconnectProfile(adapter, device, profile)
        }
    }

    private suspend fun disconnectProfile(
        adapter: BluetoothAdapter, device: BluetoothDevice, profile: Int
    ): Boolean = suspendCancellableCoroutine { continuation ->
        adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                val result = try {
                    val method =
                        proxy.javaClass.getDeclaredMethod("disconnect", BluetoothDevice::class.java)
                    method.isAccessible = true
                    val status = method.invoke(proxy, device) as Boolean
                    Log.i("BluetoothHandoff", "Disconnect using profile $profile result = $status")
                    status
                } catch (e: Exception) {
                    Log.w("BluetoothHandoff", "disconnect() not available for profile $profile", e)
                    false
                } finally {
                    adapter.closeProfileProxy(profile, proxy) // always release the proxy
                }
                if (continuation.isActive) continuation.resume(result)
            }

            override fun onServiceDisconnected(profile: Int) {
                Log.i("BluetoothHandoff", "profile [$profile] disconnected")
                if (continuation.isActive) continuation.resume(false)
            }
        }, profile)
    }


    @SuppressLint("MissingPermission")
    private fun handleRequestDeviceList() {
        Log.i("BluetoothHandoff", "Device List Requested")

        val adapter = bluetoothAdapter()
        val devices = getConnectedDevices()
        Log.i("BluetoothHandoff", "Connected devices: $devices")
        val packet = NetworkPacket(PACKET_TYPE_DEVICE_LIST)
        packet["adapter_name"] = adapter.name

        try {
            val list = JSONArray()
            for (device in devices) {
                val obj = JSONObject()
                obj.put("address", device.address)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    obj.put("name", device.alias)
                } else {
                    obj.put("name", device.name)
                }

                list.put(obj)
            }
            packet["devices"] = list
            device.sendPacket(packet)
        } catch (e: JSONException) {
            Log.e("BluetoothHandoff", "Failed to serialize response: $e")
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleRequestDisconnect(address: String?) {
        Log.i("BluetoothHandoff", "Disconnect Requested")

        if (address == null) {
            // TODO: HANDLE INVALID ADDRESS
            return
        }

        val adapter = bluetoothAdapter()
        val device = adapter.getRemoteDevice(address)
        scope.launch {
            disconnectDevice(adapter, device)
            Log.i("BluetoothHandoff", "Disconnect Success: ${device.name}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleRequestConnect(address: String?) {
        Log.i("BluetoothHandoff", "Connect Requested")

        if (address == null) {
            // TODO: HANDLE INVALID ADDRESS
            return
        }

        val adapter = bluetoothAdapter()
        val device = adapter.getRemoteDevice(address)
        scope.launch {
            connectDevice(adapter, device)
            Log.i("BluetoothHandoff", "Connect Success: ${device.name}")
        }
    }

    override fun onPacketReceived(np: NetworkPacket): Boolean {
        return when (np.type) {
            PACKET_TYPE_DEVICE_LIST_REQUEST -> {
                handleRequestDeviceList()
                true
            }

            PACKET_TYPE_DISCONNECT_REQUEST -> {
                handleRequestDisconnect(np.getString("address"))
                true
            }

            PACKET_TYPE_CONNECT_REQUEST -> {
                handleRequestConnect(np.getString("address"))
                true
            }

            else -> false
        }
    }

    override fun onDestroy() {
        scope.cancel()
    }


    companion object {
        private const val PACKET_TYPE_DEVICE_LIST = "kdeconnect.bt_handoff.device_list"
        private const val PACKET_TYPE_DEVICE_LIST_REQUEST =
            "kdeconnect.bt_handoff.device_list.request"

        //        private const val PACKET_TYPE_PAIR_REQUEST = "kdeconnect.bt_handoff.pair.request"
//        private const val PACKET_TYPE_PAIR_RESPONSE = "kdeconnect.bt_handoff.pair.response"
        private const val PACKET_TYPE_CONNECT_REQUEST = "kdeconnect.bt_handoff.connect.request"
        private const val PACKET_TYPE_CONNECT_RESPONSE = "kdeconnect.bt_handoff.connect.response"
        private const val PACKET_TYPE_DISCONNECT_REQUEST =
            "kdeconnect.bt_handoff.disconnect.request"
        private const val PACKET_TYPE_DISCONNECT_RESPONSE =
            "kdeconnect.bt_handoff.disconnect.response"
    }


}