/*
 * SPDX-FileCopyrightText: 2024 TPJ Schikhof <kde@schikhof.eu>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.Helpers

import org.junit.Assert
import org.junit.Test

class VideoUrlsHelperTest {
    @Test
    fun checkYoutubeURL() {
        val url = "https://www.youtube.com/watch?v=ovX5G0O5ZvA&t=13"
        val formatted = VideoUrlsHelper.formatUriWithSeek(url, 51_000L)
        val expected = "https://www.youtube.com/watch?v=ovX5G0O5ZvA&t=51"
        Assert.assertEquals(expected, formatted.toString())
    }

    @Test
    fun checkYoutubeURLSubSecond() {
        val url = "https://www.youtube.com/watch?v=ovX5G0O5ZvA&t=13"
        val formatted = VideoUrlsHelper.formatUriWithSeek(url, 450L)
        val expected = "https://www.youtube.com/watch?v=ovX5G0O5ZvA&t=13"
        Assert.assertEquals(expected, formatted.toString())
    }

    @Test
    fun checkVimeoURL() {
        val url = "https://vimeo.com/347119375?foo=bar&t=13s"
        val formatted = VideoUrlsHelper.formatUriWithSeek(url, 51_000L)
        val expected = "https://vimeo.com/347119375?foo=bar&t=51s"
        Assert.assertEquals(expected, formatted.toString())
    }

    @Test
    fun checkVimeoURLSubSecond() {
        val url = "https://vimeo.com/347119375?foo=bar&t=13s"
        val formatted = VideoUrlsHelper.formatUriWithSeek(url, 450L)
        val expected = "https://vimeo.com/347119375?foo=bar&t=13s"
        Assert.assertEquals(expected, formatted.toString())
    }

    @Test
    fun checkVimeoURLParamOrderCrash() {
        val url = "https://vimeo.com/347119375?t=13s"
        val formatted = VideoUrlsHelper.formatUriWithSeek(url, 51_000L)
        val expected = "https://vimeo.com/347119375?t=51s"
        Assert.assertEquals(expected, formatted.toString())
    }

    @Test
    fun checkDailymotionURL() {
        val url = "https://www.dailymotion.com/video/xnopyt?foo=bar&start=13"
        val formatted = VideoUrlsHelper.formatUriWithSeek(url, 51_000L)
        val expected = "https://www.dailymotion.com/video/xnopyt?foo=bar&start=51"
        Assert.assertEquals(expected, formatted.toString())
    }

    @Test
    fun checkTwitchURL() {
        val url = "https://www.twitch.tv/videos/123?foo=bar&t=1h2m3s"
        val formatted = VideoUrlsHelper.formatUriWithSeek(url, 10_000_000)
        val expected = "https://www.twitch.tv/videos/123?foo=bar&t=02h46m40s"
        Assert.assertEquals(expected, formatted.toString())
    }

    @Test
    fun checkUnknownURL() {
        val url = "https://example.org/cool_video.mp4"
        val formatted = VideoUrlsHelper.formatUriWithSeek(url, 51_000L)
        val expected = "https://example.org/cool_video.mp4"
        Assert.assertEquals(expected, formatted.toString())
    }
}