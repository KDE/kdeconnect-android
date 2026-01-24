/*
 * SPDX-FileCopyrightText: 2024 TPJ Schikhof <kde@schikhof.eu>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.helpers

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.Assert
import org.junit.Test

class VideoUrlsHelperTest {

    @Test
    fun checkYoutubeTvLinksConversion() {
        fun check(isTv: Boolean, input: String, expected: String) {
            mockkObject(DeviceHelper)
            every { DeviceHelper.isTv } returns isTv
            val formatted = VideoUrlsHelper.convertToAndFromYoutubeTvLinks(input)
            Assert.assertEquals(expected, formatted)
            unmockkObject(DeviceHelper)
        }
        val complexTvLink = "https://www.youtube.com/tv?is_account_switch=1&hrld=2&fltor=1#/watch?v=ZN471HiQD3o&t=13"
        val tvLink = "https://www.youtube.com/tv#/watch?v=ZN471HiQD3o&t=13"
        val pcLink = "https://www.youtube.com/watch?v=ZN471HiQD3o&t=13"
        val unrelatedLink = "https://www.youtube.com/healthz"
        check(isTv = true, input = pcLink, expected = tvLink)
        check(isTv = true, input = tvLink, expected = tvLink)
        check(isTv = true, input = complexTvLink, expected = complexTvLink)
        check(isTv = true, input = unrelatedLink, expected = unrelatedLink)
        check(isTv = false, input = pcLink, expected = pcLink)
        check(isTv = false, input = tvLink, expected = pcLink)
        check(isTv = false, input = complexTvLink, expected = pcLink)
        check(isTv = false, input = unrelatedLink, expected = unrelatedLink)
    }

    @Test
    fun checkYoutubeURLWithoutTime() {
        val url = "https://www.youtube.com/watch?v=ovX5G0O5ZvA"
        val formatted = VideoUrlsHelper.formatUriWithSeek(url, 51_000L)
        val expected = "https://www.youtube.com/watch?v=ovX5G0O5ZvA&t=51"
        Assert.assertEquals(expected, formatted)
    }

    @Test
    fun checkYoutubeURLWithTime() {
        val url = "https://www.youtube.com/watch?v=ovX5G0O5ZvA&t=13"
        val formatted = VideoUrlsHelper.formatUriWithSeek(url, 51_000L)
        val expected = "https://www.youtube.com/watch?v=ovX5G0O5ZvA&t=51"
        Assert.assertEquals(expected, formatted)
    }

    @Test
    fun checkVimeoURLWithOtherArgsWithoutTime() {
        val url = "https://vimeo.com/347119375?foo=bar"
        val formatted = VideoUrlsHelper.formatUriWithSeek(url, 51_000L)
        val expected = "https://vimeo.com/347119375?foo=bar&t=51s"
        Assert.assertEquals(expected, formatted)
    }

    @Test
    fun checkVimeoURLWithOtherArgsWithTime() {
        val url = "https://vimeo.com/347119375?foo=bar&t=13s"
        val formatted = VideoUrlsHelper.formatUriWithSeek(url, 51_000L)
        val expected = "https://vimeo.com/347119375?foo=bar&t=51s"
        Assert.assertEquals(expected, formatted)
    }

    @Test
    fun checkVimeoURLWithoutTime() {
        val url = "https://vimeo.com/347119375"
        val formatted = VideoUrlsHelper.formatUriWithSeek(url, 51_000L)
        val expected = "https://vimeo.com/347119375?t=51s"
        Assert.assertEquals(expected, formatted)
    }
    @Test
    fun checkVimeoURLWithTime() {
        val url = "https://vimeo.com/347119375?t=13s"
        val formatted = VideoUrlsHelper.formatUriWithSeek(url, 51_000L)
        val expected = "https://vimeo.com/347119375?t=51s"
        Assert.assertEquals(expected, formatted)
    }

    @Test
    fun checkDailymotionURL() {
        val url = "https://www.dailymotion.com/video/xnopyt?foo=bar&start=13"
        val formatted = VideoUrlsHelper.formatUriWithSeek(url, 51_000L)
        val expected = "https://www.dailymotion.com/video/xnopyt?foo=bar&start=51"
        Assert.assertEquals(expected, formatted)
    }

    @Test
    fun checkTwitchURL() {
        val url = "https://www.twitch.tv/videos/123?foo=bar&t=1h2m3s"
        val formatted = VideoUrlsHelper.formatUriWithSeek(url, 10_000_000)
        val expected = "https://www.twitch.tv/videos/123?foo=bar&t=02h46m40s"
        Assert.assertEquals(expected, formatted)
    }

    @Test
    fun checkUnknownURL() {
        val url = "https://example.org/cool_video.mp4"
        val formatted = VideoUrlsHelper.formatUriWithSeek(url, 51_000L)
        val expected = "https://example.org/cool_video.mp4"
        Assert.assertEquals(expected, formatted)
    }

    @Test
    fun checkPeerTubeURL() {
        val validUrls = mapOf(
            "https://video.blender.org/w/472h2s5srBFmAThiZVw96R" to "https://video.blender.org/w/472h2s5srBFmAThiZVw96R?start=01m30s",
            "https://video.blender.org/w/mDyZP2TrdjjjNRMoVUgPM2?start=01m27s" to "https://video.blender.org/w/mDyZP2TrdjjjNRMoVUgPM2?start=01m30s",
            "https://video.blender.org/w/evhMcVhvK6VeAKJwCSuHSe#potato" to "https://video.blender.org/w/evhMcVhvK6VeAKJwCSuHSe?start=01m30s#potato",
            "https://video.blender.org/w/54tzKpEguEEu26Hi8Lcpna?start=01m27s#potato" to "https://video.blender.org/w/54tzKpEguEEu26Hi8Lcpna?start=01m30s#potato",
            "https://video.blender.org/w/o5VtGNQaNpFNNHiJbLy4eM?start=01m27s" to "https://video.blender.org/w/o5VtGNQaNpFNNHiJbLy4eM?start=01m30s",
        )
        for ((from, to) in validUrls) {
            val formatted = VideoUrlsHelper.formatUriWithSeek(from, 90_000L)
            Assert.assertEquals(to, formatted)
        }
        val invalidUrls = listOf(
            "https://video.blender.org/w/472h2s5srBFmAOhiZVw96R?start=01m27s", // invalid character (O)
            "https://video.blender.org/w/mDyZP2TrdjjjNIMoVUgPM2?start=01m27s", // invalid character (I)
            "https://video.blender.org/w/evhMcVhvK6VeAlJwCSuHSe?start=01m27s", // invalid character (l)
            "https://video.blender.org/w/54tzKpEguEEu20Hi8Lcpna?start=01m27s", // invalid character (0)
            "https://video.blender.org/w/o5VtGNQaNpFNHiJbLy4eM?start=01m27s", // invalid length (21)
            "https://video.blender.org/w/hb43bRmBzNpHd4sW74Y4cyAB?start=01m27s", // invalid length (23)
        )
        for (url in invalidUrls) {
            val formatted = VideoUrlsHelper.formatUriWithSeek(url, 90_000L)
            Assert.assertEquals(url, formatted) // should not modify the URL
        }
    }
}
