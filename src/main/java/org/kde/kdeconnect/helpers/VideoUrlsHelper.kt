/*
 * SPDX-FileCopyrightText: 2024 TPJ Schikhof <kde@schikhof.eu>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.helpers

import java.net.MalformedURLException
import java.net.URL

object VideoUrlsHelper {
    private const val SECONDS_IN_MINUTE = 60
    private const val MINUTES_IN_HOUR = 60
    private const val SECONDS_IN_HOUR = SECONDS_IN_MINUTE * MINUTES_IN_HOUR

    /** PeerTube uses a Flickr Base58 encoded short UUID (alphanumeric, but 0, O, I, and l are excluded) with a length of 22 characters **/
    private val peerTubePathPattern = Regex("^/w/[1-9a-km-zA-HJ-NP-Z]{22}(\\?.+)?$")

    @Throws(MalformedURLException::class)
    fun formatUriWithSeek(address: String, positionMillis: Long): String {
        val positionSeconds = positionMillis / 1000
        if (positionSeconds <= 0) {
            return address // do not change the url if time is zero
        }
        val (host, path) = URL(address).let { Pair(it.host.lowercase(), it.path) }
        return when {
            listOf("youtube.com", "youtu.be", "pornhub.com").any { site -> site in host } -> {
                editOrAddParameter(address, "t", Regex("\\d+"), "$positionSeconds")
            }
            host.contains("vimeo.com") -> {
                editOrAddParameter(address, "t", Regex("\\d+s"), "${positionSeconds}s")
            }
            host.contains("dailymotion.com") -> {
                editOrAddParameter(address, "start", Regex("\\d+"), "$positionSeconds")
            }
            host.contains("twitch.tv") -> {
                editOrAddParameter(address, "t", Regex("(\\d+[hH])?(\\d+[mM])?\\d+[sS]"), formatTimestampHMS(positionSeconds))
            }
            path.matches(peerTubePathPattern) -> {
                editOrAddParameter(address, "start", Regex("(\\d+[hH])?(\\d+[mM])?\\d+[sS]"), formatTimestampHMS(positionSeconds))
            }
            else -> address
        }
    }

    fun editOrAddParameter(
        url: String,
        parameter: String,
        valuePattern: Regex,
        newValue: String
    ): String {
        val (urlWithoutFragment, fragment) = url.split("#", limit = 2).let { Pair(it[0], it.getOrNull(1)) }
        val (baseUrl, query) = urlWithoutFragment.split("?", limit = 2).let { Pair(it[0], it.getOrElse(1) { "" }) }

        val params = query
            .split("&")
            .filter { it.isNotEmpty() }
            .associate {
                val (key, value) = it.split("=", limit = 2).let { parts ->
                    parts[0] to parts.getOrElse(1) { "" }
                }
                key to value
            }.toMutableMap()

        val currentValue = params[parameter]
        if (currentValue != null && !currentValue.matches(valuePattern)) {
            // The argument exists but it doesn't match the format we expect, did we match the wrong url?
            return url
        }
        params[parameter] = newValue

        val newQuery = params.entries.joinToString("&") { "${it.key}=${it.value}" }
        val newUrlWithoutFragment = if (newQuery.isNotEmpty()) "$baseUrl?$newQuery" else baseUrl
        return if (fragment != null) "$newUrlWithoutFragment#$fragment" else newUrlWithoutFragment
    }

    fun convertToAndFromYoutubeTvLinks(url : String): String {
        if (url.contains("youtube.com/watch") || url.contains("youtube.com/tv")) {
            val wantTvLinks = DeviceHelper.isTv
            val isTvLink = url.contains("youtube\\.com/tv.*#/watch".toRegex())
            if (wantTvLinks && !isTvLink) {
                return url.replace("youtube.com/watch", "youtube.com/tv#/watch")
            } else if (!wantTvLinks && isTvLink) {
                return url.replace("youtube\\.com/tv.*#/watch".toRegex(), "youtube.com/watch")
            }
        }
        return url
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
