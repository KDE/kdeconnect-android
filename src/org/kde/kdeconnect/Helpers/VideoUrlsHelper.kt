/*
 * SPDX-FileCopyrightText: 2024 TPJ Schikhof <kde@schikhof.eu>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.Helpers

import java.net.MalformedURLException
import java.net.URL

object VideoUrlsHelper {
    private const val SECONDS_IN_MINUTE = 60
    private const val MINUTES_IN_HOUR = 60
    private const val SECONDS_IN_HOUR = SECONDS_IN_MINUTE * MINUTES_IN_HOUR

    /** PeerTube uses a Flickr Base58 encoded short UUID (alphanumeric, but 0, O, I, and l are excluded) with a length of 22 characters **/
    private val peerTubePathPattern = Regex("^/w/[1-9a-km-zA-HJ-NP-Z]{22}(\\?.+)?$")

    @Throws(MalformedURLException::class)
    fun formatUriWithSeek(address: String, position: Long): URL {
        val positionSeconds = position / 1000 // milliseconds to seconds
        val url = URL(address)
        if (positionSeconds <= 0) {
            return url // nothing to do
        }
        val host = url.host.lowercase()

        return when {
            listOf("youtube.com", "youtu.be", "pornhub.com").any { site -> site in host } -> {
                url.editParameter("t", Regex("\\d+")) { "$positionSeconds" }
            }
            host.contains("vimeo.com") -> {
                url.editParameter("t", Regex("\\d+s")) { "${positionSeconds}s" }
            }
            host.contains("dailymotion.com") -> {
                url.editParameter("start", Regex("\\d+")) { "$positionSeconds" }
            }
            host.contains("twitch.tv") -> {
                url.editParameter("t", Regex("(\\d+[hH])?(\\d+[mM])?\\d+[sS]")) { formatTimestampHMS(positionSeconds) }
            }
            url.path.matches(peerTubePathPattern) -> {
                url.editParameter("start", Regex("(\\d+[hH])?(\\d+[mM])?\\d+[sS]")) { formatTimestampHMS(positionSeconds) }
            }
            else -> url
        }
    }

    private fun URL.editParameter(parameter: CharSequence, valuePattern: Regex?, parameterValueModifier: (String) -> String): URL {
        // "https://www.youtube.com/watch?v=ovX5G0O5ZvA&t=13" -> ["https://www.youtube.com/watch", "v=ovX5G0O5ZvA&t=13"]
        val urlSplit = this.toString().split("?")
        if (urlSplit.size != 2) {
            return this
        }
        val (urlBase, urlQuery) = urlSplit
        val modifiedUrlQuery = urlQuery
            .split("&") // "v=ovX5G0O5ZvA&t=13" -> ["v=ovX5G0O5ZvA", "t=13"]
            .map { it.split("=", limit = 2) } // […, "t=13"] -> […, ["t", "13"]]
            .map { Pair(it.first(), it.lastOrNull() ?: return this) }
            .map { paramAndValue ->
                // Modify matching parameter and optionally matches the old value with the provided pattern
                if (paramAndValue.first == parameter && valuePattern?.matches(paramAndValue.second) != false) {
                    Pair(paramAndValue.first, parameterValueModifier(paramAndValue.second)) // ["t", "13"] -> ["t", result]
                } else {
                    paramAndValue
                }
            }
            .joinToString("&") { "${it.first}=${it.second}" } // [["v", "ovX5G0O5ZvA"], ["t", "14"]] -> "v=ovX5G0O5ZvA&t=14"
        return URL("${urlBase}?${modifiedUrlQuery}") // -> "https://www.youtube.com/watch?v=ovX5G0O5ZvA&t=14"
    }

    /**
     *  @param timestamp in seconds
     *  @return timestamp formatted as e.g. "01h02m34s"
     * */
    private fun formatTimestampHMS(timestamp: Long): String {
        if (timestamp == 0L) return "0s"

        val seconds: Long = timestamp % SECONDS_IN_MINUTE
        val minutes: Long = (timestamp / SECONDS_IN_MINUTE) % MINUTES_IN_HOUR
        val hours: Long = timestamp / SECONDS_IN_HOUR

        fun pad(s: String) = s.padStart(3, '0')
        val hoursText = if (hours > 0) pad("${hours}h") else ""
        val minutesText = if (minutes > 0 || hours > 0) pad("${minutes}m") else ""
        val secondsText = pad("${seconds}s")

        return "${hoursText}${minutesText}${secondsText}"
    }
}
