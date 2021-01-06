/*
 * SPDX-FileCopyrightText: 2017 Matthijs Tijink <matthijstijink@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.Plugins.MprisPlugin

import android.content.Context
import android.content.pm.PackageManager.NameNotFoundException
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.util.Log
import androidx.collection.LruCache
import androidx.core.content.getSystemService
import androidx.core.net.ConnectivityManagerCompat
import com.jakewharton.disklrucache.DiskLruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kde.kdeconnect.NetworkPacket.Payload
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.*

/**
 * Handles the cache for album art
 */
internal object AlbumArtCache {
    /**
     * An in-memory cache for album art bitmaps. Holds at most 10 entries (to prevent too much memory usage)
     * Also remembers failure to fetch urls.
     */
    private val memoryCache = LruCache<String, MemoryCacheItem>(10)

    /**
     * An on-disk cache for album art bitmaps.
     */
    private lateinit var diskCache: DiskLruCache

    /**
     * Used to check if the connection is metered
     */
    private lateinit var connectivityManager: ConnectivityManager

    /**
     * A list of urls yet to be fetched.
     */
    private val fetchUrlList = ArrayList<URL>()

    /**
     * A list of urls currently being fetched
     */
    private val isFetchingList = ArrayList<URL>()

    /**
     * A integer indicating how many fetches are in progress.
     */
    private var numFetching = 0

    /**
     * A list of plugins to notify on fetched album art
     */
    private val registeredPlugins = ArrayList<MprisPlugin>()

    /**
     * Initializes the disk cache. Needs to be called at least once before trying to use the cache
     *
     * @param context The context
     */
    @JvmStatic
    fun initializeDiskCache(context: Context) {
        if (this::diskCache.isInitialized) return
        val cacheDir = File(context.cacheDir, "album_art")
        val versionCode: Int
        try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            versionCode = info.versionCode
            //Initialize the disk cache with a limit of 5 MB storage (fits ~830 images, taking Spotify as reference)
            diskCache = DiskLruCache.open(cacheDir, versionCode, 1, 1000 * 1000 * 5.toLong())
        } catch (e: NameNotFoundException) {
            throw AssertionError(e)
        } catch (e: IOException) {
            Log.e("KDE/Mpris/AlbumArtCache", "Could not open the album art disk cache!", e)
        }
        connectivityManager = context.applicationContext.getSystemService()!!
    }

    /**
     * Registers a mpris plugin, such that it gets notified of fetched album art
     *
     * @param mpris The mpris plugin
     */
    @JvmStatic
    fun registerPlugin(mpris: MprisPlugin) {
        registeredPlugins.add(mpris)
    }

    /**
     * Deregister a mpris plugin
     *
     * @param mpris The mpris plugin
     */
    @JvmStatic
    fun deregisterPlugin(mpris: MprisPlugin?) {
        registeredPlugins.remove(mpris)
    }

    /**
     * Get the album art for the given url. Currently only handles http(s) urls.
     * If it's not in the cache, will initiate a request to fetch it.
     *
     * @param albumUrl The album art url
     * @return A bitmap for the album art. Can be null if not (yet) found
     */
    @JvmStatic
    fun getAlbumArt(albumUrl: String?, plugin: MprisPlugin, player: String?): Bitmap? {
        //If the url is invalid, return "no album art"
        if (albumUrl.isNullOrEmpty()) {
            return null
        }
        val url = try {
            URL(albumUrl)
        } catch (e: MalformedURLException) {
            //Invalid url, so just return "no album art"
            //Shouldn't happen (checked on receival of the url), but just to be sure
            return null
        }

        //We currently only support http(s) and file urls
        if (url.protocol !in arrayOf("http", "https", "file")) {
            return null
        }

        //First, check the in-memory cache
        val albumItem = memoryCache[albumUrl]
        if (albumItem != null) {
            //Do not retry failed fetches
            return if (albumItem.failedFetch) {
                null
            } else {
                albumItem.albumArt
            }
        }

        //If not found, check the disk cache
        if (!this::diskCache.isInitialized) {
            Log.e("KDE/Mpris/AlbumArtCache", "The disk cache is not intialized!")
            return null
        }
        try {
            val item = diskCache[urlToDiskCacheKey(albumUrl)]
            if (item != null) {
                val result = BitmapFactory.decodeStream(item.getInputStream(0))
                item.close()
                val memItem = MemoryCacheItem()
                if (result != null) {
                    memItem.failedFetch = false
                    memItem.albumArt = result
                } else {
                    //Invalid bitmap, so remember it as a "failed fetch" and remove it from the disk cache
                    memItem.failedFetch = true
                    memItem.albumArt = null
                    diskCache.remove(urlToDiskCacheKey(albumUrl))
                    Log.d("KDE/Mpris/AlbumArtCache", "Invalid image: $albumUrl")
                }
                memoryCache.put(albumUrl, memItem)
                return result
            }
        } catch (e: IOException) {
            return null
        }

        /* If not found, we have not tried fetching it (recently), or a fetch is in-progress.
           Either way, just add it to the fetch queue and starting fetching it if no fetch is running. */
        if ("file" == url.protocol) {
            //Special-case file, since we need to fetch it from the remote
            if (url in isFetchingList) return null
            if (!plugin.askTransferAlbumArt(albumUrl, player)) {
                //It doesn't support transferring the art, so mark it as failed in the memory cache
                memoryCache.put(url.toString(), MemoryCacheItem(true))
            }
        } else {
            fetchUrl(url)
        }
        return null
    }

    /**
     * Fetches an album art url and puts it in the cache
     *
     * @param url The url
     */
    private fun fetchUrl(url: URL) {
        //We need the disk cache for this
        if (!this::diskCache.isInitialized) {
            Log.e("KDE/Mpris/AlbumArtCache", "The disk cache is not intialized!")
            return
        }
        if (ConnectivityManagerCompat.isActiveNetworkMetered(connectivityManager)) {
            //Only download art on unmetered networks (wifi etc.)
            return
        }

        //Only fetch an URL if we're not fetching it already
        if (url in fetchUrlList || url in isFetchingList) {
            return
        }
        fetchUrlList.add(url)
        initiateFetch()
    }

    /**
     * Does the actual fetching and makes sure only not too many fetches are running at the same time
     */
    private fun initiateFetch() {
        if (numFetching >= 2 || fetchUrlList.isEmpty()) return

        //Fetch the last-requested url first, it will probably be needed first
        val url = fetchUrlList.last()
        //Remove the url from the to-fetch list
        fetchUrlList.remove(url)
        if ("file" == url.protocol) {
            throw AssertionError("Not file urls should be possible here!")
        }

        //Download the album art ourselves
        ++numFetching
        //Add the url to the currently-fetching list
        isFetchingList.add(url)
        try {
            val cacheItem = diskCache.edit(urlToDiskCacheKey(url.toString()))
            if (cacheItem == null) {
                Log.e("KDE/Mpris/AlbumArtCache",
                        "Two disk cache edits happened at the same time, should be impossible!")
                --numFetching
                return
            }

            //Do the actual fetch in the background
            GlobalScope.launch { fetchURL(url, null, cacheItem) }
        } catch (e: IOException) {
            Log.e("KDE/Mpris/AlbumArtCache", "Problems with the disk cache", e)
            --numFetching
        }
    }

    /**
     * The disk cache requires mostly alphanumeric characters, and at most 64 characters.
     * So hash the url to get a valid key
     *
     * @param url The url
     * @return A valid disk cache key
     */
    private fun urlToDiskCacheKey(url: String): String {
        return MessageDigest.getInstance("MD5").digest(url.toByteArray())
                .joinToString(separator = "") { String.format("%02x", it) }
    }

    /**
     * Transfer an asked-for album art payload to the disk cache.
     *
     * @param albumUrl The url of the album art (should be a file:// url)
     * @param payload  The payload input stream
     */
    @JvmStatic
    fun payloadToDiskCache(albumUrl: String, payload: Payload?) {
        //We need the disk cache for this
        if (payload == null) {
            return
        }
        if (!this::diskCache.isInitialized) {
            Log.e("KDE/Mpris/AlbumArtCache", "The disk cache is not intialized!")
            payload.close()
            return
        }
        val url = try {
            URL(albumUrl)
        } catch (e: MalformedURLException) {
            //Shouldn't happen (checked on receival of the url), but just to be sure
            payload.close()
            return
        }
        if ("file" != url.protocol) {
            //Shouldn't happen (otherwise we wouldn't have asked for the payload), but just to be sure
            payload.close()
            return
        }

        //Only fetch the URL if we're not fetching it already
        if (url in isFetchingList) {
            payload.close()
            return
        }

        //Check if we already have this art
        try {
            if (memoryCache[albumUrl] != null || diskCache[urlToDiskCacheKey(albumUrl)] != null) {
                payload.close()
                return
            }
        } catch (e: IOException) {
            Log.e("KDE/Mpris/AlbumArtCache", "Disk cache problem!", e)
            payload.close()
            return
        }

        //Add it to the currently-fetching list
        isFetchingList.add(url)
        ++numFetching
        try {
            val cacheItem = diskCache.edit(urlToDiskCacheKey(url.toString()))
            if (cacheItem == null) {
                Log.e("KDE/Mpris/AlbumArtCache",
                        "Two disk cache edits happened at the same time, should be impossible!")
                --numFetching
                payload.close()
                return
            }

            //Do the actual fetch in the background
            GlobalScope.launch { fetchURL(url, payload, cacheItem) }
        } catch (e: IOException) {
            Log.e("KDE/Mpris/AlbumArtCache", "Problems with the disk cache", e)
            --numFetching
        }
    }

    private class MemoryCacheItem(var failedFetch: Boolean = false, var albumArt: Bitmap? = null)

    /**
     * Initialize an url fetch
     *
     * @param url          The url being fetched
     * @param payload      A NetworkPacket Payload (if from the connected device). null if fetched from http(s)
     * @param cacheItem    The disk cache item to edit
     */
    private suspend fun fetchURL(url: URL, payload: Payload?, cacheItem: DiskLruCache.Editor) {
        var success = withContext(Dispatchers.IO) {
            //See if we need to open a http(s) connection here, or if we use a payload input stream
            val output = cacheItem.newOutputStream(0)
            try {
                val inputStream = payload?.inputStream ?: openHttp(url)
                val buffer = ByteArray(4096)
                var bytesRead: Int
                if (inputStream != null) {
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
                output.flush()
                output.close()
                return@withContext true
            } catch (e: IOException) {
                return@withContext false
            } catch (e: AssertionError) {
                return@withContext false
            } finally {
                payload?.close()
            }
        }

        try {
            // Since commit() and abort() are blocking calls, they have to be executed in the IO
            // dispatcher.
            withContext(Dispatchers.IO) {
                if (success) {
                    cacheItem.commit()
                } else {
                    cacheItem.abort()
                }
            }
        } catch (e: IOException) {
            success = false
            Log.e("KDE/Mpris/AlbumArtCache", "Problem with the disk cache", e)
        }
        if (success) {
            //Now it's in the disk cache, the getAlbumArt() function should be able to read it

            //So notify the mpris plugins of the fetched art
            for (mpris in registeredPlugins) {
                mpris.fetchedAlbumArt(url.toString())
            }
        } else {
            //Mark the fetch as failed in the memory cache
            memoryCache.put(url.toString(), MemoryCacheItem(true))
        }

        //Remove the url from the fetching list
        isFetchingList.remove(url)
        //Fetch the next url (if any)
        --numFetching
        initiateFetch()
    }

    /**
     * Opens the http(s) connection
     *
     * @return True if succeeded
     */
    private fun openHttp(url: URL): InputStream? {
        //Default android behaviour does not follow https -> http urls, so do this manually
        if (url.protocol !in arrayOf("http", "https")) {
            throw AssertionError("Invalid url: not http(s) in background album art fetch")
        }
        var currentUrl = url
        var connection: HttpURLConnection
        loop@ for (i in 0..4) {
            connection = currentUrl.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.instanceFollowRedirects = false
            when (connection.responseCode) {
                HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP -> {
                    var location = connection.getHeaderField("Location")
                    location = URLDecoder.decode(location, "UTF-8")
                    currentUrl = URL(currentUrl, location) // Deal with relative URLs
                    //Again, only support http(s)
                    if (currentUrl.protocol !in arrayOf("http", "https")) {
                        return null
                    }
                    connection.disconnect()
                    continue@loop
                }
            }

            //Found a non-redirecting connection, so do something with it
            return connection.inputStream
        }
        return null
    }
}
