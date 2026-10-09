/*
 * SPDX-FileCopyrightText: 2017 Matthijs Tijink <matthijstijink@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins.mpris

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.util.Log
import androidx.collection.LruCache
import androidx.core.content.getSystemService
import androidx.core.net.ConnectivityManagerCompat
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath
import okio.source
import org.kde.kdeconnect.NetworkPacket.Payload
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Handles the cache for album art. Downloading, decoding, resizing and caching are delegated to Coil.
 */
internal object AlbumArtCache {
    private const val TAG = "KDE/Mpris/AlbumArtCache"

    /**
     * Urls we failed to fetch, so we don't retry them. Bounded, so they get retried eventually.
     */
    private val failedUrls = LruCache<String, Boolean>(10)

    /**
     * Urls currently being loaded or written to the disk cache.
     */
    private val pendingUrls: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Loads album art from http(s) or from the disk cache (for art transferred from the device).
     * Its memory cache holds at most 16 MB of uncompressed bitmaps, and its disk cache at most 12 MB of image files.
     */
    private lateinit var imageLoader: ImageLoader

    private lateinit var appContext: Context

    /**
     * Used to check if the connection is metered
     */
    private lateinit var connectivityManager: ConnectivityManager

    /**
     * A list of plugins to notify on fetched album art
     */
    private val registeredPlugins = CopyOnWriteArrayList<MprisPlugin>()

    val ALLOWED_SCHEMES = listOf("http", "https", "file", "kdeconnect")

    /**
     * A list of art url schemes that require a transfer from the connected device.
     */
    private val DEVICE_FETCH_SCHEMES = listOf("file", "kdeconnect")

    private val cacheScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Initializes the disk cache. Needs to be called at least once before trying to use the cache
     *
     * @param context The context
     */
    fun initializeDiskCache(context: Context) {
        if (this::imageLoader.isInitialized) return
        appContext = context.applicationContext
        connectivityManager = appContext.getSystemService()!!
        imageLoader = ImageLoader.Builder(appContext)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizeBytes(16L * 1024L * 1024L)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(appContext.cacheDir, "album_art").toOkioPath())
                    .maxSizeBytes(1000L * 1000L * 12L)
                    .build()
            }
            .components {
                add(OkHttpNetworkFetcherFactory())
                add(DeviceAlbumArtFetcher.Factory())
            }
            .build()
    }

    /**
     * Registers a mpris plugin, such that it gets notified of fetched album art
     *
     * @param mpris The mpris plugin
     */
    fun registerPlugin(mpris: MprisPlugin) {
        registeredPlugins.add(mpris)
    }

    /**
     * Deregister a mpris plugin
     *
     * @param mpris The mpris plugin
     */
    fun deregisterPlugin(mpris: MprisPlugin?) {
        registeredPlugins.remove(mpris)
    }

    /**
     * Get the album art for the given url.
     * If it's not in the in-memory cache, will initiate a request to load it, and
     * [MprisPlugin.fetchedAlbumArt] will be called with it once loaded.
     *
     * @param albumUrl The album art url
     * @return A bitmap for the album art. Can be null if not (yet) found
     */
    fun getAlbumArt(albumUrl: String?, plugin: MprisPlugin, player: String?): Bitmap? {
        // If the url is invalid, return "no album art"
        if (albumUrl.isNullOrEmpty()) {
            return null
        }
        val url = albumUrl.toUri()

        // We currently only support http(s), file, and kdeconnect urls
        if (url.scheme !in ALLOWED_SCHEMES) {
            return null
        }

        if (!this::imageLoader.isInitialized) {
            Log.e(TAG, "The cache is not initialized!")
            return null
        }

        // First, check the in-memory cache
        getFromMemoryCache(albumUrl)?.let { return it }

        // Do not retry failed fetches
        if (failedUrls[albumUrl] != null) {
            return null
        }

        if (albumUrl in pendingUrls) {
            return null
        }

        val fromDevice = url.scheme in DEVICE_FETCH_SCHEMES
        if (fromDevice && !isInDiskCache(albumUrl)) {
            // Special-case file or kdeconnect, since we need to fetch it from the connected device
            if (!plugin.askTransferAlbumArt(albumUrl, player)) {
                // It doesn't support transferring the art, so mark it as failed
                failedUrls.put(albumUrl, true)
            }
            return null
        }

        load(albumUrl, fromDevice)
        return null
    }

    private fun getFromMemoryCache(albumUrl: String): Bitmap? {
        return imageLoader.memoryCache?.get(MemoryCache.Key(albumUrl))?.image?.toBitmap()
    }

    private fun isInDiskCache(albumUrl: String): Boolean {
        return try {
            imageLoader.diskCache?.openSnapshot(albumUrl)?.use { true } ?: false
        } catch (e: IOException) {
            Log.e(TAG, "Disk cache problem!", e)
            false
        }
    }

    /**
     * Loads the album art (from the disk cache or the network) and hands it to the plugins
     *
     * @param albumUrl   The url
     * @param fromDevice Whether the art was transferred from the connected device, so it can only come from the disk cache
     */
    private fun load(albumUrl: String, fromDevice: Boolean) {
        // Only download art on unmetered networks (wifi etc.), but still use what's in the disk cache
        val metered = ConnectivityManagerCompat.isActiveNetworkMetered(connectivityManager)
        pendingUrls.add(albumUrl)
        val request = ImageRequest.Builder(appContext)
            .data(if (fromDevice) DeviceAlbumArt(albumUrl) else albumUrl)
            .memoryCacheKey(albumUrl)
            .diskCacheKey(albumUrl)
            .networkCachePolicy(if (metered) CachePolicy.DISABLED else CachePolicy.ENABLED)
            // The bitmaps get passed to the media session and notifications
            .allowHardware(false)
            // Downscale big images (we don't need more for the notification or the now playing screen), never upscale
            .size(1024)
            .precision(Precision.INEXACT)
            .listener(
                onCancel = { pendingUrls.remove(albumUrl) },
                onSuccess = { _, result ->
                    pendingUrls.remove(albumUrl)
                    val albumArt = result.image.toBitmap()
                    for (mpris in registeredPlugins) {
                        mpris.fetchedAlbumArt(albumUrl, albumArt)
                    }
                },
                onError = { _, result ->
                    pendingUrls.remove(albumUrl)
                    // Not having it cached while on a metered connection is not a failure, retry later
                    if (fromDevice || !metered) {
                        Log.d(TAG, "Failed to load album art: $albumUrl", result.throwable)
                        failedUrls.put(albumUrl, true)
                        // It might be an invalid image, don't keep it
                        try {
                            imageLoader.diskCache?.remove(albumUrl)
                        } catch (e: IOException) {
                            Log.e(TAG, "Disk cache problem!", e)
                        }
                    }
                },
            )
            .build()
        imageLoader.enqueue(request)
    }

    /**
     * Transfer an asked-for album art payload to the disk cache.
     *
     * @param albumUrl The url of the album art (must be one of the [DEVICE_FETCH_SCHEMES])
     * @param payload  The payload input stream
     */
    fun payloadToDiskCache(albumUrl: String, payload: Payload?) {
        if (payload == null) {
            return
        }
        // We need the disk cache for this
        val diskCache = if (this::imageLoader.isInitialized) imageLoader.diskCache else null
        if (diskCache == null) {
            Log.e(TAG, "The disk cache is not initialized!")
            payload.close()
            return
        }
        val url = albumUrl.toUri()
        if (url.scheme !in DEVICE_FETCH_SCHEMES) {
            // Shouldn't happen (checked on receival of the url), but just to be sure
            Log.e(TAG, "Got invalid art url with payload: $albumUrl")
            payload.close()
            return
        }

        // Check if we already have this art, or are already fetching it
        if (getFromMemoryCache(albumUrl) != null || isInDiskCache(albumUrl) || !pendingUrls.add(albumUrl)) {
            payload.close()
            return
        }

        cacheScope.launch {
            var editor: DiskCache.Editor? = null
            var stored = false
            try {
                editor = diskCache.openEditor(albumUrl)
                if (editor == null) {
                    Log.e(TAG, "Two disk cache edits happened at the same time, should be impossible!")
                    return@launch
                }
                val inputStream = payload.inputStream ?: throw IOException("Payload without input stream")
                diskCache.fileSystem.write(editor.data) {
                    inputStream.source().use { writeAll(it) }
                }
                editor.commit()
                stored = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to receive album art: $albumUrl", e)
                try {
                    editor?.abort()
                } catch (e: Exception) {
                    Log.e(TAG, "Problem with the disk cache", e)
                }
                failedUrls.put(albumUrl, true)
            } finally {
                payload.close()
                pendingUrls.remove(albumUrl)
            }
            // Now it's in the disk cache, so it can be loaded
            if (stored) {
                load(albumUrl, fromDevice = true)
            }
        }
    }

    /**
     * Album art transferred from the connected device, which can only be found in the disk cache
     */
    private class DeviceAlbumArt(val url: String)

    /**
     * Reads [DeviceAlbumArt] from the disk cache. Prevents Coil from interpreting file:// urls as local files.
     */
    private class DeviceAlbumArtFetcher(private val url: String, private val diskCache: DiskCache) : Fetcher {
        override suspend fun fetch(): FetchResult {
            val snapshot = diskCache.openSnapshot(url) ?: throw IOException("Album art not in the disk cache: $url")
            return SourceFetchResult(
                source = ImageSource(snapshot.data, diskCache.fileSystem, url, snapshot),
                mimeType = null,
                dataSource = DataSource.DISK,
            )
        }

        class Factory : Fetcher.Factory<DeviceAlbumArt> {
            override fun create(data: DeviceAlbumArt, options: Options, imageLoader: ImageLoader): Fetcher? {
                return imageLoader.diskCache?.let { DeviceAlbumArtFetcher(data.url, it) }
            }
        }
    }
}
