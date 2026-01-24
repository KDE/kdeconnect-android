package org.kde.kdeconnect

import org.kde.kdeconnect.helpers.ThreadHelper
import java.net.InetAddress

class DeviceHost private constructor(private val host: String) {
    // Wrapper because Kotlin doesn't allow nested nullability
    data class PingResult(val latency: Long?)

    /** The amount of milliseconds the ping request took or null it's in progress */
    var ping: PingResult? = null
        private set

    /**
     * Checks if the host can be reached over the network.
     * @param callback Callback for updating UI elements
     */
    fun checkReachable(callback: () -> Unit) {
        ThreadHelper.execute {
            try {
                val address = InetAddress.getByName(this.host)
                val startTime = System.currentTimeMillis()
                val pingable = address.isReachable(PING_TIMEOUT)
                val delayMillis = System.currentTimeMillis() - startTime
                val pingResult = PingResult(if (pingable) delayMillis else null)
                ping = pingResult
            }
            catch (_: Exception) {
                ping = PingResult(null)
            }
            callback()
        }
    }

    init {
        require(isValidDeviceHost(host)) { "Invalid host" }
    }

    override fun toString(): String {
        return this.host
    }

    companion object {
        /** Ping timeout */
        private const val PING_TIMEOUT = 3_000
        private val hostnameValidityPattern = Regex("^[0-9A-Za-z.:%_-]+$")

        @JvmStatic
        fun isValidDeviceHost(host: String): Boolean {
            return hostnameValidityPattern.matches(host)
        }

        @JvmStatic
        fun toDeviceHostOrNull(host: String): DeviceHost? {
            return if (isValidDeviceHost(host)) {
                DeviceHost(host)
            } else {
                null
            }
        }

        @JvmField
        val BROADCAST: DeviceHost = DeviceHost("255.255.255.255")
    }
}
