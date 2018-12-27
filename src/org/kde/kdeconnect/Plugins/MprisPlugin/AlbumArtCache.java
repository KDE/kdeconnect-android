/*
 * Copyright 2017 Matthijs Tijink <matthijstijink@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License or (at your option) version 3 or any later version
 * accepted by the membership of KDE e.V. (or its successor approved
 * by the membership of KDE e.V.), which shall act as a proxy
 * defined in Section 14 of version 3 of the license.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.kde.kdeconnect.Plugins.MprisPlugin;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import com.jakewharton.disklrucache.DiskLruCache;

import org.kde.kdeconnect.NetworkPacket;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import androidx.collection.LruCache;

/**
 * Handles the cache for album art
 */
final class AlbumArtCache {
    private static final class MemoryCacheItem {
        boolean failedFetch;
        Bitmap albumArt;
    }

    /**
     * An in-memory cache for album art bitmaps. Holds at most 10 entries (to prevent too much memory usage)
     * Also remembers failure to fetch urls.
     */
    private static final LruCache<String, MemoryCacheItem> memoryCache = new LruCache<>(10);
    /**
     * An on-disk cache for album art bitmaps.
     */
    private static DiskLruCache diskCache;
    /**
     * Used to check if the connection is metered
     */
    private static ConnectivityManager connectivityManager;

    /**
     * A list of urls yet to be fetched.
     */
    private static final ArrayList<URL> fetchUrlList = new ArrayList<>();
    /**
     * A list of urls currently being fetched
     */
    private static final ArrayList<URL> isFetchingList = new ArrayList<>();
    /**
     * A integer indicating how many fetches are in progress.
     */
    private static int numFetching = 0;

    /**
     * A list of plugins to notify on fetched album art
     */
    private static final ArrayList<MprisPlugin> registeredPlugins = new ArrayList<>();

    /**
     * Initializes the disk cache. Needs to be called at least once before trying to use the cache
     *
     * @param context The context
     */
    static void initializeDiskCache(Context context) {
        if (diskCache != null) return;

        File cacheDir = new File(context.getCacheDir(), "album_art");
        int versionCode;
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            versionCode = info.versionCode;
            //Initialize the disk cache with a limit of 5 MB storage (fits ~830 images, taking Spotify as reference)
            diskCache = DiskLruCache.open(cacheDir, versionCode, 1, 1000 * 1000 * 5);
        } catch (PackageManager.NameNotFoundException e) {
            throw new AssertionError(e);
        } catch (IOException e) {
            Log.e("KDE/Mpris/AlbumArtCache", "Could not open the album art disk cache!", e);
        }

        connectivityManager = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    /**
     * Registers a mpris plugin, such that it gets notified of fetched album art
     *
     * @param mpris The mpris plugin
     */
    static void registerPlugin(MprisPlugin mpris) {
        registeredPlugins.add(mpris);
    }

    /**
     * Deregister a mpris plugin
     *
     * @param mpris The mpris plugin
     */
    static void deregisterPlugin(MprisPlugin mpris) {
        registeredPlugins.remove(mpris);
    }

    /**
     * Get the album art for the given url. Currently only handles http(s) urls.
     * If it's not in the cache, will initiate a request to fetch it.
     *
     * @param albumUrl The album art url
     * @return A bitmap for the album art. Can be null if not (yet) found
     */
    static Bitmap getAlbumArt(String albumUrl, MprisPlugin plugin, String player) {
        //If the url is invalid, return "no album art"
        if (albumUrl == null || albumUrl.isEmpty()) {
            return null;
        }

        URL url;
        try {
            url = new URL(albumUrl);
        } catch (MalformedURLException e) {
            //Invalid url, so just return "no album art"
            //Shouldn't happen (checked on receival of the url), but just to be sure
            return null;
        }

        //We currently only support http(s) and file urls
        if (!url.getProtocol().equals("http") && !url.getProtocol().equals("https") && !url.getProtocol().equals("file")) {
            return null;
        }

        //First, check the in-memory cache
        if (memoryCache.get(albumUrl) != null) {
            MemoryCacheItem item = memoryCache.get(albumUrl);

            //Do not retry failed fetches
            if (item.failedFetch) {
                return null;
            } else {
                return item.albumArt;
            }
        }

        //If not found, check the disk cache
        if (diskCache == null) {
            Log.e("KDE/Mpris/AlbumArtCache", "The disk cache is not intialized!");
            return null;
        }
        try {
            DiskLruCache.Snapshot item = diskCache.get(urlToDiskCacheKey(albumUrl));
            if (item != null) {
                Bitmap result = BitmapFactory.decodeStream(item.getInputStream(0));
                item.close();
                MemoryCacheItem memItem = new MemoryCacheItem();
                if (result != null) {
                    memItem.failedFetch = false;
                    memItem.albumArt = result;
                } else {
                    //Invalid bitmap, so remember it as a "failed fetch" and remove it from the disk cache
                    memItem.failedFetch = true;
                    memItem.albumArt = null;
                    diskCache.remove(urlToDiskCacheKey(albumUrl));
                    Log.d("KDE/Mpris/AlbumArtCache", "Invalid image: " + albumUrl);
                }
                memoryCache.put(albumUrl, memItem);
                return result;
            }
        } catch (IOException e) {
            return null;
        }

        /* If not found, we have not tried fetching it (recently), or a fetch is in-progress.
           Either way, just add it to the fetch queue and starting fetching it if no fetch is running. */
        if ("file".equals(url.getProtocol())) {
            //Special-case file, since we need to fetch it from the remote
            if (isFetchingList.contains(url)) return null;

            if (!plugin.askTransferAlbumArt(albumUrl, player)) {
                //It doesn't support transferring the art, so mark it as failed in the memory cache
                MemoryCacheItem cacheItem = new MemoryCacheItem();
                cacheItem.failedFetch = true;
                cacheItem.albumArt = null;
                memoryCache.put(url.toString(), cacheItem);
            }
        } else {
            fetchUrl(url);
        }
        return null;
    }

    /**
     * Fetches an album art url and puts it in the cache
     *
     * @param url The url
     */
    private static void fetchUrl(URL url) {
        //We need the disk cache for this
        if (diskCache == null) {
            Log.e("KDE/Mpris/AlbumArtCache", "The disk cache is not intialized!");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            //Only download art on unmetered networks (wifi etc.)
            if (connectivityManager.isActiveNetworkMetered()) {
                return;
            }
        }

        //Only fetch an URL if we're not fetching it already
        if (fetchUrlList.contains(url) || isFetchingList.contains(url)) {
            return;
        }

        fetchUrlList.add(url);
        initiateFetch();
    }

    private static final class FetchURLTask extends AsyncTask<Void, Void, Boolean> {
        private final URL url;
        private NetworkPacket.Payload payload;
        private final DiskLruCache.Editor cacheItem;
        private OutputStream output;

        /**
         * Initialize an url fetch
         *
         * @param url          The url being fetched
         * @param payload      A NetworkPacket Payload (if from the connected device). null if fetched from http(s)
         * @param cacheItem    The disk cache item to edit
         */
        FetchURLTask(URL url, NetworkPacket.Payload payload, DiskLruCache.Editor cacheItem) throws IOException {
            this.url = url;
            this.payload = payload;
            this.cacheItem = cacheItem;
            output = cacheItem.newOutputStream(0);
        }

        /**
         * Opens the http(s) connection
         *
         * @return True if succeeded
         */
        private InputStream openHttp() throws IOException {
            //Default android behaviour does not follow https -> http urls, so do this manually
            if (!url.getProtocol().equals("http") && !url.getProtocol().equals("https")) {
                throw new AssertionError("Invalid url: not http(s) in background album art fetch");
            }
            URL currentUrl = url;
            HttpURLConnection connection;
            for (int i = 0; i < 5; ++i) {
                connection = (HttpURLConnection) currentUrl.openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setInstanceFollowRedirects(false);

                switch (connection.getResponseCode()) {
                    case HttpURLConnection.HTTP_MOVED_PERM:
                    case HttpURLConnection.HTTP_MOVED_TEMP:
                        String location = connection.getHeaderField("Location");
                        location = URLDecoder.decode(location, "UTF-8");
                        currentUrl = new URL(currentUrl, location);  // Deal with relative URLs
                        //Again, only support http(s)
                        if (!currentUrl.getProtocol().equals("http") && !currentUrl.getProtocol().equals("https")) {
                            return null;
                        }
                        connection.disconnect();
                        continue;
                }

                //Found a non-redirecting connection, so do something with it
                return connection.getInputStream();
            }

            return null;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            //See if we need to open a http(s) connection here, or if we use a payload input stream
            try (InputStream input = payload == null ? openHttp() : payload.getInputStream()) {
                if (input == null) {
                    return false;
                }

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
                output.flush();
                output.close();
                return true;
            } catch (IOException e) {
                return false;
            } finally {
                if (payload != null) {
                    payload.close();
                }
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            try {
                if (success) {
                    cacheItem.commit();
                } else {
                    cacheItem.abort();
                }
            } catch (IOException e) {
                success = false;
                Log.e("KDE/Mpris/AlbumArtCache", "Problem with the disk cache", e);
            }
            if (success) {
                //Now it's in the disk cache, the getAlbumArt() function should be able to read it

                //So notify the mpris plugins of the fetched art
                for (MprisPlugin mpris : registeredPlugins) {
                    mpris.fetchedAlbumArt(url.toString());
                }
            } else {
                //Mark the fetch as failed in the memory cache
                MemoryCacheItem cacheItem = new MemoryCacheItem();
                cacheItem.failedFetch = true;
                cacheItem.albumArt = null;
                memoryCache.put(url.toString(), cacheItem);
            }

            //Remove the url from the fetching list
            isFetchingList.remove(url);
            //Fetch the next url (if any)
            --numFetching;
            initiateFetch();
        }
    }

    /**
     * Does the actual fetching and makes sure only not too many fetches are running at the same time
     */
    private static void initiateFetch() {
        if (numFetching >= 2) return;
        if (fetchUrlList.isEmpty()) return;

        //Fetch the last-requested url first, it will probably be needed first
        URL url = fetchUrlList.get(fetchUrlList.size() - 1);
        //Remove the url from the to-fetch list
        fetchUrlList.remove(url);

        if ("file".equals(url.getProtocol())) {
            throw new AssertionError("Not file urls should be possible here!");
        }

        //Download the album art ourselves
        ++numFetching;
        //Add the url to the currently-fetching list
        isFetchingList.add(url);
        try {
            DiskLruCache.Editor cacheItem = diskCache.edit(urlToDiskCacheKey(url.toString()));
            if (cacheItem == null) {
                Log.e("KDE/Mpris/AlbumArtCache",
                        "Two disk cache edits happened at the same time, should be impossible!");
                --numFetching;
                return;
            }

            //Do the actual fetch in the background
            new FetchURLTask(url, null, cacheItem).execute();
        } catch (IOException e) {
            Log.e("KDE/Mpris/AlbumArtCache", "Problems with the disk cache", e);
            --numFetching;
        }
    }

    /**
     * The disk cache requires mostly alphanumeric characters, and at most 64 characters.
     * So hash the url to get a valid key
     *
     * @param url The url
     * @return A valid disk cache key
     */
    private static String urlToDiskCacheKey(String url) {
        MessageDigest hasher;
        try {
            hasher = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            //Should always be available
            throw new AssertionError(e);
        }

        StringBuilder builder = new StringBuilder();
        for (byte singleByte : hasher.digest(url.getBytes())) {
            builder.append(String.format("%02x", singleByte));
        }
        return builder.toString();
    }

    /**
     * Transfer an asked-for album art payload to the disk cache.
     *
     * @param albumUrl The url of the album art (should be a file:// url)
     * @param payload  The payload input stream
     */
    static void payloadToDiskCache(String albumUrl, NetworkPacket.Payload payload) {
        //We need the disk cache for this
        if (payload == null) {
            return;
        }

        if (diskCache == null) {
            Log.e("KDE/Mpris/AlbumArtCache", "The disk cache is not intialized!");
            payload.close();
            return;
        }

        URL url;
        try {
            url = new URL(albumUrl);
        } catch (MalformedURLException e) {
            //Shouldn't happen (checked on receival of the url), but just to be sure
            payload.close();
            return;
        }

        if (!"file".equals(url.getProtocol())) {
            //Shouldn't happen (otherwise we wouldn't have asked for the payload), but just to be sure
            payload.close();
            return;
        }

        //Only fetch the URL if we're not fetching it already
        if (isFetchingList.contains(url)) {
            payload.close();
            return;
        }

        //Check if we already have this art
        try {
            if (memoryCache.get(albumUrl) != null || diskCache.get(urlToDiskCacheKey(albumUrl)) != null) {
                payload.close();
                return;
            }
        } catch (IOException e) {
            Log.e("KDE/Mpris/AlbumArtCache", "Disk cache problem!", e);
            payload.close();
            return;
        }

        //Add it to the currently-fetching list
        isFetchingList.add(url);
        ++numFetching;

        try {
            DiskLruCache.Editor cacheItem = diskCache.edit(urlToDiskCacheKey(url.toString()));
            if (cacheItem == null) {
                Log.e("KDE/Mpris/AlbumArtCache",
                        "Two disk cache edits happened at the same time, should be impossible!");
                --numFetching;
                payload.close();
                return;
            }

            //Do the actual fetch in the background
            new FetchURLTask(url, payload, cacheItem).execute();
        } catch (IOException e) {
            Log.e("KDE/Mpris/AlbumArtCache", "Problems with the disk cache", e);
            --numFetching;
        }
    }
}
