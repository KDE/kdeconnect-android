/*
 * SPDX-FileCopyrightText: 2018 Nicolas Fella <nicolas.fella@gmx.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.mprisreceiver

import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.net.Uri
import android.util.Pair
import java.io.ByteArrayOutputStream
import androidx.core.net.toUri

internal class MprisReceiverCallback : MediaController.Callback {
    private val plugin: MprisReceiverPlugin
    private val player: MprisReceiverPlayer

    constructor(plugin: MprisReceiverPlugin, player: MprisReceiverPlayer) : super() {
        this.plugin = plugin
        this.player = player
        val artAndUri: Pair<Bitmap, String>? = getArtAndUri(player.metadata)
        if (artAndUri != null) {
            val bitmap = artAndUri.first
            val hash = hashBitmap(bitmap)
            artHash = hash
            artUrl = makeArtUrl(hash, artAndUri.second)
            displayArt = bitmap
            album = player.album
            artist = player.artist
        }
    }

    private var artHash: Long? = null
    private var displayArt: Bitmap? = null
    var artUrl: String? = null
        private set
    private var album: String? = null
    private var artist: String? = null

    fun encodeAsUri(kind: String?, data: String?): String {
        // there's probably a better way to do this, but meh
        // TODO: do we want to include the player name?
        return Uri.Builder()
            .scheme("kdeconnect")
            .path("/artUri")
            .appendQueryParameter(kind, data)
            .build().toString()
    }

    /**
     * Extract the art bitmap and corresponding uri from the media metadata.
     *
     * @return Pair of art,artUrl. May be null if either was not found.
     */
    fun getArtAndUri(metadata: MediaMetadata?): Pair<Bitmap, String>? {
        if (metadata == null) return null
        var art: Bitmap? = null
        for (s in PREFERRED_BITMAP_ORDER) {
            val next = metadata.getBitmap(s)
            if (next != null) {
                art = next
                break
            }
        }
        var uri: String? = null
        for (s in PREFERRED_URI_ORDER) {
            val next = metadata.getString(s)
            if (!next.isNullOrEmpty()) {
                val kind = when (s) {
                    MediaMetadata.METADATA_KEY_ALBUM -> "album"
                    MediaMetadata.METADATA_KEY_TITLE -> "title"
                    MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST -> "artist"
                    else -> "orig"
                }
                uri = encodeAsUri(kind, next)
                break
            }
        }

        if (art == null || uri == null) return null
        return Pair(art, uri)
    }

    private fun hashBitmap(bitmap: Bitmap): Long {
        val buffer = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(buffer, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return buffer.contentHashCode().toLong()
    }

    private fun makeArtUrl(artHash: Long, artUrl: String): String {
        // we include the hash in the URL to handle the case when the player changes the bitmap
        // without changing the url- the PC side won't know the art was modified if we don't do this
        // also useful when the input url contains only the artist name (eg: YouTube)
        return artUrl.toUri()
            .buildUpon()
            .appendQueryParameter("kdeArtHash", artHash.toString())
            .build()
            .toString()
    }

    override fun onPlaybackStateChanged(state: PlaybackState?) {
        plugin.sendMetadata(player)
    }

    override fun onMetadataChanged(metadata: MediaMetadata?) {
        if (metadata == null) {
            artHash = null
            displayArt = null
            artUrl = null
            artist = null
            album = null
        } else {
            // We could check hasRequestedAlbumArt to avoid hashing art for clients that don't support it
            //  But upon running the profiler, looks like hashBitmap is a minuscule (<1%) part so no
            //  need to optimize prematurely.
            val artAndUri: Pair<Bitmap, String>? = getArtAndUri(metadata)
            val newAlbum = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
            val newArtist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            if (artAndUri == null) {
                // Check if the album+artist is still the same. some players don't send art every time
                if (newAlbum != album || newArtist != artist) {
                    // there really is no new art
                    artHash = null
                    displayArt = null
                    artUrl = null
                    album = null
                    artist = null
                }
            } else {
                val newHash: Long = hashBitmap(artAndUri.first)
                // In case the hashes are equal, we do a full comparison to protect against collisions
                if ((newHash != artHash || !artAndUri.first.sameAs(displayArt))) {
                    artHash = newHash
                    displayArt = artAndUri.first
                    artUrl = makeArtUrl(artHash!!, artAndUri.second)
                    artist = newArtist
                    album = newAlbum
                }
            }
        }

        plugin.sendMetadata(player)
    }

    override fun onAudioInfoChanged(info: MediaController.PlaybackInfo) {
        // Note: not called by all media players
        plugin.sendMetadata(player)
    }

    val artAsArray: ByteArray?
        /**
         * Get the JPG art of the current track as a bytearray.
         * 
         * @return null if no art is available, otherwise a PNG image serialized into a bytearray
         */
        get() {
            val displayArt = this.displayArt ?: return null
            val stream = ByteArrayOutputStream()
            displayArt.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            return stream.toByteArray()
        }

    companion object {
        private val PREFERRED_BITMAP_ORDER = arrayOf(MediaMetadata.METADATA_KEY_DISPLAY_ICON, MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_ALBUM_ART)

        private val PREFERRED_URI_ORDER = arrayOf(
            MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
            MediaMetadata.METADATA_KEY_ART_URI,
            MediaMetadata.METADATA_KEY_ALBUM_ART_URI,  // Fall back to album name if none of the above is set
            MediaMetadata.METADATA_KEY_ALBUM,  // YouTube doesn't normally provide album info
            MediaMetadata.METADATA_KEY_TITLE,  // Last option, use artist
            MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
            MediaMetadata.METADATA_KEY_ARTIST,
        )
    }
}
