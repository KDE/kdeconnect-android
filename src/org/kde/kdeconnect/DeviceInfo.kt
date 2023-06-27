/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.util.Base64
import androidx.core.content.ContextCompat
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper
import org.kde.kdeconnect_tp.R
import java.security.cert.Certificate
import java.security.cert.CertificateEncodingException

/**
 * DeviceInfo contains all the properties needed to instantiate a Device.
 */
class DeviceInfo(
    @JvmField val id : String,
    @JvmField val certificate : Certificate,
    @JvmField var name : String,
    @JvmField var type : DeviceType,
    @JvmField var protocolVersion : Int = 0,
    @JvmField var incomingCapabilities : Set<String>? = null,
    @JvmField var outgoingCapabilities : Set<String>? = null,
) {

    /**
     * Saves the info in settings so it can be restored later using loadFromSettings().
     * This is used to keep info from paired devices, even when they are not reachable.
     * The capabilities and protocol version are not persisted.
     */
    fun saveInSettings(settings: SharedPreferences) {
        val editor = settings.edit()
        try {
            val encodedCertificate = Base64.encodeToString(certificate.encoded, 0)
            editor.putString("certificate", encodedCertificate)
        } catch (e: CertificateEncodingException) {
            throw RuntimeException(e)
        }
        editor.putString("deviceName", name)
        editor.putString("deviceType", type.toString())
        editor.apply()
    }


    /**
     * Serializes to a NetworkPacket, which LanLinkProvider uses to send this data over the network.
     * The serialization doesn't include the certificate, since LanLink can query that from the socket.
     * Can be deserialized using fromIdentityPacketAndCert(), given a certificate.
     */
    fun toIdentityPacket(): NetworkPacket {
        val np = NetworkPacket(NetworkPacket.PACKET_TYPE_IDENTITY)
        np.set("deviceId", id)
        np.set("deviceName", name)
        np.set("protocolVersion", protocolVersion)
        np.set("deviceType", type.toString())
        np.set("incomingCapabilities", incomingCapabilities)
        np.set("outgoingCapabilities", outgoingCapabilities)
        return np
    }

    companion object {

        /**
         * Recreates a DeviceInfo object that was persisted using saveInSettings()
         */
        @JvmStatic
        fun loadFromSettings(context : Context, deviceId: String, settings: SharedPreferences): DeviceInfo {
            val deviceName = settings.getString("deviceName", "unknown")!!
            val deviceType = DeviceType.fromString(settings.getString("deviceType", "desktop")!!)
            val certificate = SslHelper.getDeviceCertificate(context, deviceId)
            return DeviceInfo(id = deviceId, name = deviceName, type = deviceType, certificate = certificate)
        }

        /**
         * Recreates a DeviceInfo object that was serialized using toIdentityPacket().
         * Since toIdentityPacket() doesn't serialize the certificate, this needs to be passed separately.
         */
        @JvmStatic
        fun fromIdentityPacketAndCert(identityPacket : NetworkPacket, certificate : Certificate): DeviceInfo {
            val deviceId = identityPacket.getString("deviceId")
            val deviceName = identityPacket.getString("deviceName", "unknown")
            val protocolVersion = identityPacket.getInt("protocolVersion")
            val deviceType = DeviceType.fromString(identityPacket.getString("deviceType", "desktop"))
            val incomingCapabilities = identityPacket.getStringSet("incomingCapabilities")
            val outgoingCapabilities = identityPacket.getStringSet("outgoingCapabilities")
            return DeviceInfo(id = deviceId, name = deviceName, type = deviceType, certificate = certificate,
                protocolVersion = protocolVersion, incomingCapabilities = incomingCapabilities, outgoingCapabilities = outgoingCapabilities)
        }
    }

}


enum class DeviceType {
    Phone, Tablet, Computer, Tv;

    override fun toString(): String {
        return when (this) {
            Tablet -> "tablet"
            Phone -> "phone"
            Tv -> "tv"
            else -> "desktop"
        }
    }

    fun getIcon(context: Context): Drawable? {
        val drawableId: Int = when (this) {
            Phone -> R.drawable.ic_device_phone_32dp
            Tablet -> R.drawable.ic_device_tablet_32dp
            Tv -> R.drawable.ic_device_tv_32dp
            else -> R.drawable.ic_device_laptop_32dp
        }
        return ContextCompat.getDrawable(context, drawableId)
    }

    companion object {
        @JvmStatic
        fun fromString(s: String): DeviceType {
            return when (s) {
                "phone" -> Phone
                "tablet" -> Tablet
                "tv" -> Tv
                else -> Computer
            }
        }
    }
}
