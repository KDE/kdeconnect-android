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
import android.os.Handler
import android.os.Looper
import android.util.Pair
import androidx.core.net.toUri
import org.apache.commons.lang3.StringUtils
import java.io.ByteArrayOutputStream

internal class MprisReceiverPlayer(
    private val controller: MediaController,
    val name: String,
    private val onChanged: (MprisReceiverPlayer) -> Unit,
) {
    private var artHash: Long? = null
    private var displayArt: Bitmap? = null
    var artUrl: String? = null
        private set
    private var artAlbum: String? = null
    private var artArtist: String? = null

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            onChanged(this@MprisReceiverPlayer)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateArt(metadata)
            onChanged(this@MprisReceiverPlayer)
        }

        override fun onAudioInfoChanged(info: MediaController.PlaybackInfo) {
            // Note: not called by all media players
            onChanged(this@MprisReceiverPlayer)
        }
    }

    init {
        val artAndUri: Pair<Bitmap, String>? = getArtAndUri(metadata)
        if (artAndUri != null) {
            val bitmap = artAndUri.first
            val hash = hashBitmap(bitmap)
            artHash = hash
            artUrl = makeArtUrl(hash, artAndUri.second)
            displayArt = bitmap
            artAlbum = album
            artArtist = artist
        }
        controller.registerCallback(callback, Handler(Looper.getMainLooper()))
    }

    fun release() {
        controller.unregisterCallback(callback)
    }

    private fun clearArt() {
        artHash = null
        displayArt = null
        artUrl = null
        artAlbum = null
        artArtist = null
    }

    private fun updateArt(metadata: MediaMetadata?) {
        if (metadata == null) {
            clearArt()
            return
        }
        // We could check hasRequestedAlbumArt to avoid hashing art for clients that don't support it
        //  But upon running the profiler, looks like hashBitmap is a minuscule (<1%) part so no
        //  need to optimize prematurely.
        val artAndUri: Pair<Bitmap, String>? = getArtAndUri(metadata)
        val newAlbum = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
        val newArtist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
        if (artAndUri == null) {
            // Check if the album+artist is still the same. some players don't send art every time
            if (newAlbum != artAlbum || newArtist != artArtist) {
                // there really is no new art
                clearArt()
            }
        } else {
            val newHash: Long = hashBitmap(artAndUri.first)
            // In case the hashes are equal, we do a full comparison to protect against collisions
            if ((newHash != artHash || !artAndUri.first.sameAs(displayArt))) {
                artHash = newHash
                displayArt = artAndUri.first
                artUrl = makeArtUrl(newHash, artAndUri.second)
                artArtist = newArtist
                artAlbum = newAlbum
            }
        }
    }

    val artAsArray: ByteArray?
        /**
         * Get the JPG art of the current track as a bytearray.
         *
         * @return null if no art is available, otherwise a JPG image serialized into a bytearray
         */
        get() {
            val displayArt = this.displayArt ?: return null
            val stream = ByteArrayOutputStream()
            displayArt.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            return stream.toByteArray()
        }

    fun isPlaying(): Boolean {
        val state = controller.playbackState ?: return false
        return state.state == PlaybackState.STATE_PLAYING
    }

    fun canPlay(): Boolean {
        val state = controller.playbackState ?: return false
        if (state.state == PlaybackState.STATE_PLAYING) return true
        return (state.actions and (PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PLAY_PAUSE)) != 0L
    }

    fun canPause(): Boolean {
        val state = controller.playbackState ?: return false
        if (state.state == PlaybackState.STATE_PAUSED) return true
        return (state.actions and (PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE)) != 0L
    }

    fun canGoPrevious(): Boolean {
        val state = controller.playbackState ?: return false
        return (state.actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L
    }

    fun canGoNext(): Boolean {
        val state = controller.playbackState ?: return false
        return (state.actions and PlaybackState.ACTION_SKIP_TO_NEXT) != 0L
    }

    fun canSeek(): Boolean {
        val state = controller.playbackState ?: return false
        return (state.actions and PlaybackState.ACTION_SEEK_TO) != 0L
    }

    fun playPause() {
        if (this.isPlaying()) {
            controller.transportControls.pause()
        } else {
            controller.transportControls.play()
        }
    }

    val album: String
        get() {
            val metadata = controller.metadata ?: return ""
            return metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        }

    val artist: String
        get() {
            val metadata = controller.metadata ?: return ""
            return StringUtils.firstNonEmpty<String?>(
                metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
                metadata.getString(MediaMetadata.METADATA_KEY_AUTHOR),
                metadata.getString(MediaMetadata.METADATA_KEY_WRITER)
            ) ?: ""
        }

    val title: String
        get() {
            val metadata = controller.metadata ?: return ""
            return StringUtils.firstNonEmpty<String?>(
                metadata.getString(MediaMetadata.METADATA_KEY_TITLE),
                metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ) ?: ""
        }

    fun previous() {
        controller.transportControls.skipToPrevious()
    }

    fun next() {
        controller.transportControls.skipToNext()
    }

    fun play() {
        controller.transportControls.play()
    }

    fun pause() {
        controller.transportControls.pause()
    }

    fun stop() {
        controller.transportControls.stop()
    }

    var volume: Int
        get() {
            val info = controller.playbackInfo
            if (info.maxVolume == 0) return 0
            return 100 * info.currentVolume / info.maxVolume
        }
        set(volume) {
            val info = controller.playbackInfo
            // Use rounding for the volume, since most devices don't have a very large range
            val unroundedVolume = info.maxVolume * volume / 100.0 + 0.5
            controller.setVolumeTo(unroundedVolume.toInt(), 0)
        }

    var position: Long
        get() {
            val state = controller.playbackState ?: return 0
            return state.position
        }
        set(position) {
            controller.transportControls.seekTo(position)
        }

    val length: Long
        get() {
            val metadata = controller.metadata ?: return 0
            return metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
        }

    val metadata: MediaMetadata?
        get() = controller.metadata

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

        private fun encodeAsUri(kind: String?, data: String?): String {
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
        private fun getArtAndUri(metadata: MediaMetadata?): Pair<Bitmap, String>? {
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
    }
}
