/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import org.kde.kdeconnect.Helpers.DeviceHelper
import org.kde.kdeconnect.Helpers.SecurityHelpers.SslHelper
import org.kde.kdeconnect_tp.R
import java.security.cert.Certificate
import java.security.cert.CertificateEncodingException
import java.security.cert.CertificateException

/**
 * DeviceInfo contains all the properties needed to instantiate a Device.
 */
class DeviceInfo(
    @JvmField val id: String,
    @JvmField val certificate: Certificate,
    @JvmField var name: String,
    @JvmField var type: DeviceType,
    @JvmField var protocolVersion: Int = 0,
    @JvmField var incomingCapabilities: Set<String>? = null,
    @JvmField var outgoingCapabilities: Set<String>? = null,
) {

    /**
     * Saves the info in settings so it can be restored later using loadFromSettings().
     * This is used to keep info from paired devices, even when they are not reachable.
     * The capabilities and protocol version are not persisted.
     */
    fun saveInSettings(settings: SharedPreferences) {
        try {
            val encodedCertificate = Base64.encodeToString(certificate.encoded, 0)

            with(settings.edit()) {
                putString("certificate", encodedCertificate)
                putString("deviceName", name)
                putString("deviceType", type.toString())
                apply()
            }
        } catch (e: CertificateEncodingException) {
            throw RuntimeException(e)
        }
    }

    /**
     * Serializes to a NetworkPacket, which LanLinkProvider uses to send this data over the network.
     * The serialization doesn't include the certificate, since LanLink can query that from the socket.
     * Can be deserialized using fromIdentityPacketAndCert(), given a certificate.
     */
    fun toIdentityPacket(): NetworkPacket =
        NetworkPacket(NetworkPacket.PACKET_TYPE_IDENTITY).also { np ->
            np.set("deviceId", id)
            np.set("deviceName", name)
            np.set("protocolVersion", protocolVersion)
            np.set("deviceType", type.toString())
            np.set("incomingCapabilities", incomingCapabilities!!)
            np.set("outgoingCapabilities", outgoingCapabilities!!)
        }

    companion object {

        /**
         * Recreates a DeviceInfo object that was persisted using saveInSettings()
         */
        @JvmStatic
        @Throws(CertificateException::class)
        fun loadFromSettings(context: Context, deviceId: String, settings: SharedPreferences) =
            with(settings) {
                DeviceInfo(
                    id = deviceId,
                    name = getString("deviceName", "unknown")!!,
                    type = DeviceType.fromString(getString("deviceType", "desktop")!!),
                    certificate = SslHelper.getDeviceCertificate(context, deviceId)
                )
            }

        /**
         * Recreates a DeviceInfo object that was serialized using toIdentityPacket().
         * Since toIdentityPacket() doesn't serialize the certificate, this needs to be passed separately.
         */
        @JvmStatic
        fun fromIdentityPacketAndCert(identityPacket: NetworkPacket, certificate: Certificate) =
            with(identityPacket) {
                DeviceInfo(
                    id = getString("deviceId"), // Redundant: We could read this from the certificate instead
                    name = DeviceHelper.filterName(getString("deviceName", "unknown")),
                    type = DeviceType.fromString(getString("deviceType", "desktop")),
                    certificate = certificate,
                    protocolVersion = getInt("protocolVersion"),
                    incomingCapabilities = getStringSet("incomingCapabilities"),
                    outgoingCapabilities = getStringSet("outgoingCapabilities")
                )
            }

        @JvmStatic
        fun isValidIdentityPacket(identityPacket: NetworkPacket): Boolean = with(identityPacket) {
            type == NetworkPacket.PACKET_TYPE_IDENTITY &&
                    DeviceHelper.filterName(getString("deviceName", "")).isNotBlank() &&
                    getString("deviceId", "").isNotBlank()
        }
    }
}

enum class DeviceType {
    PHONE, TABLET, DESKTOP, LAPTOP, TV;

    override fun toString() =
        when (this) {
            TABLET -> "tablet"
            PHONE -> "phone"
            TV -> "tv"
            LAPTOP -> "laptop"
            else -> "desktop"
        }

    fun getIcon(context: Context) =
        ContextCompat.getDrawable(context, toDrawableId())!!

    @DrawableRes
    fun toDrawableId() =
        when (this) {
            PHONE -> R.drawable.ic_device_phone_32dp
            TABLET -> R.drawable.ic_device_tablet_32dp
            TV -> R.drawable.ic_device_tv_32dp
            LAPTOP -> R.drawable.ic_device_laptop_32dp
            else -> R.drawable.ic_device_desktop_32dp
        }

    fun toShortcutDrawableId() =
        when (this) {
            PHONE -> R.drawable.ic_device_phone_shortcut
            TABLET -> R.drawable.ic_device_tablet_shortcut
            TV -> R.drawable.ic_device_tv_shortcut
            LAPTOP -> R.drawable.ic_device_laptop_shortcut
            else -> R.drawable.ic_device_desktop_shortcut
        }

    companion object {
        @JvmStatic
        fun fromString(s: String) =
            when (s) {
                "phone" -> PHONE
                "tablet" -> TABLET
                "tv" -> TV
                "laptop" -> LAPTOP
                else -> DESKTOP
            }
    }
}
