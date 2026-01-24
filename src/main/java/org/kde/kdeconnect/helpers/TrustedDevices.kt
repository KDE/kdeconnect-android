package org.kde.kdeconnect.helpers

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import org.kde.kdeconnect.helpers.security.SslHelper.parseCertificate
import java.security.cert.Certificate

object TrustedDevices {

    @JvmStatic
    fun isTrustedDevice(context: Context, deviceId: String): Boolean {
        val preferences: SharedPreferences = context.getSharedPreferences("trusted_devices", MODE_PRIVATE)
        return preferences.getBoolean(deviceId, false)
    }

    fun addTrustedDevice(context: Context, deviceId: String) {
        val preferences = context.getSharedPreferences("trusted_devices", MODE_PRIVATE)
        preferences.edit { putBoolean(deviceId, true) }
    }

    fun removeTrustedDevice(context: Context, deviceId: String) {
        val preferences = context.getSharedPreferences("trusted_devices", MODE_PRIVATE)
        preferences.edit { remove(deviceId) }
        val deviceSettings = context.getSharedPreferences(deviceId, Context.MODE_PRIVATE)
        deviceSettings.edit { clear() }

    }

    fun getAllTrustedDevices(context: Context): List<String> {
        val preferences = context.getSharedPreferences("trusted_devices", MODE_PRIVATE)
        return preferences.all.keys.filter { preferences.getBoolean(it, false) }
    }

    fun removeAllTrustedDevices(context: Context) {
        val preferences = context.getSharedPreferences("trusted_devices", MODE_PRIVATE)
        preferences.all.keys
            .forEach {
                Log.d("KdeConnect", "Removing devices: $it")
                preferences.edit { remove(it) }
            }
    }

    /**
     * Returns the stored certificate for a trusted device
     *
     * @throws java.security.cert.CertificateException if there is no certificate stored (ie: the device isn't trusted)
     */
    fun getDeviceCertificate(context: Context, deviceId: String): Certificate {
        val devicePreferences = context.getSharedPreferences(deviceId, MODE_PRIVATE)
        val certificateBytes = Base64.decode(devicePreferences.getString("certificate", ""), 0)
        return parseCertificate(certificateBytes)
    }

    @JvmStatic
    fun isCertificateStored(context: Context, deviceId: String): Boolean {
        val devicePreferences = context.getSharedPreferences(deviceId, MODE_PRIVATE)
        val cert: String = devicePreferences.getString("certificate", "")!!
        return cert.isNotEmpty()
    }

    fun getDeviceSettings(context: Context, deviceId: String): SharedPreferences {
        return context.getSharedPreferences(deviceId, MODE_PRIVATE)
    }

}
