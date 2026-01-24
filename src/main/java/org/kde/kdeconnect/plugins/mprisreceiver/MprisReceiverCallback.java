/*
 * SPDX-FileCopyrightText: 2018 Nicolas Fella <nicolas.fella@gmx.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.mprisreceiver;

import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.util.Pair;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Objects;

class MprisReceiverCallback extends MediaController.Callback {
    private final MprisReceiverPlayer player;
    private final MprisReceiverPlugin plugin;

    private Long artHash = null;
    private Bitmap displayArt = null;
    private String artUrl = null;
    private String album = null;
    private String artist = null;

    private static final String[] PREFERRED_BITMAP_ORDER = {
            MediaMetadata.METADATA_KEY_DISPLAY_ICON,
            MediaMetadata.METADATA_KEY_ART,
            MediaMetadata.METADATA_KEY_ALBUM_ART
    };

    private static final String[] PREFERRED_URI_ORDER = {
            MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
            MediaMetadata.METADATA_KEY_ART_URI,
            MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
            // Fall back to album name if none of the above is set
            MediaMetadata.METADATA_KEY_ALBUM,
            // Youtube doesn't normally provide album info
            MediaMetadata.METADATA_KEY_TITLE,
            // Last option, use artist
            MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
            MediaMetadata.METADATA_KEY_ARTIST,
    };

    static String encodeAsUri(String kind, String data) {
        // there's probably a better way to do this, but meh
        // TODO: do we want to include the player name?
        return new Uri.Builder()
                .scheme("kdeconnect")
                .path("/artUri")
                .appendQueryParameter(kind, data)
                .build().toString();
    }

    /**
     * Extract the art bitmap and corresponding uri from the media metadata.
     *
     * @return Pair of art,artUrl. May be null if either was not found.
     */
    static Pair<Bitmap, String> getArtAndUri(MediaMetadata metadata) {
        if (metadata == null) return null;
        String uri = null;
        Bitmap art = null;
        for (String s : PREFERRED_BITMAP_ORDER) {
            Bitmap next = metadata.getBitmap(s);
            if (next != null) {
                art = next;
                break;
            }
        }
        for (String s : PREFERRED_URI_ORDER) {
            String next = metadata.getString(s);
            if (next != null && !next.isEmpty()) {
                String kind;
                switch (s) {
                    case MediaMetadata.METADATA_KEY_ALBUM:
                        kind = "album";
                        break;
                    case MediaMetadata.METADATA_KEY_TITLE:
                        kind = "title";
                        break;
                    case MediaMetadata.METADATA_KEY_ARTIST:
                    case MediaMetadata.METADATA_KEY_ALBUM_ARTIST:
                        kind = "artist";
                        break;
                    default:
                        kind = "orig";
                        break;
                }
                uri = encodeAsUri(kind, next);
                break;
            }
        }

        if (art == null || uri == null) return null;
        return new Pair<>(art, uri);
    }

    private static long hashBitmap(Bitmap bitmap) {
        int[] buffer = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(buffer, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        return Arrays.hashCode(buffer);
    }

    private String makeArtUrl(long artHash, String artUrl) {
        // we include the hash in the URL to handle the case when the player changes the bitmap
        // without changing the url- the PC side won't know the art was modified if we don't do this
        // also useful when the input url contains only the artist name (eg: Youtube)
        return Uri.parse(artUrl)
                .buildUpon()
                .appendQueryParameter("kdeArtHash", String.valueOf(artHash))
                .build()
                .toString();
    }

    MprisReceiverCallback(MprisReceiverPlugin plugin, MprisReceiverPlayer player) {
        this.player = player;
        this.plugin = plugin;
        // fetch the initial art, when player is already running and we start kdeconnect
        Pair<Bitmap, String> artAndUri = getArtAndUri(player.getMetadata());
        if (artAndUri != null) {
            Bitmap bitmap = artAndUri.first;
            artHash = hashBitmap(bitmap);
            artUrl = makeArtUrl(artHash, artAndUri.second);
            displayArt = bitmap;
            album = player.getAlbum();
            artist = player.getArtist();
        }
    }

    @Override
    public void onPlaybackStateChanged(PlaybackState state) {
        plugin.sendMetadata(player);
    }

    @Override
    public void onMetadataChanged(@Nullable MediaMetadata metadata) {
        if (metadata == null) {
            artHash = null;
            displayArt = null;
            artUrl = null;
            artist = null;
            album = null;
        } else {
            // We could check hasRequestedAlbumArt to avoid hashing art for clients that don't support it
            //  But upon running the profiler, looks like hashBitmap is a minuscule (<1%) part so no
            //  need to optimize prematurely.
            Pair<Bitmap, String> artAndUri = getArtAndUri(metadata);
            String newAlbum = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM);
            String newArtist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
            if (artAndUri == null) {
                // check if the album+artist is still the same- some players don't send art every time
                if (!Objects.equals(newAlbum, album) || !Objects.equals(newArtist, artist)) {
                    // there really is no new art
                    artHash = null;
                    displayArt = null;
                    artUrl = null;
                    album = null;
                    artist = null;
                }
            } else {
                Long newHash = hashBitmap(artAndUri.first);
                // In case the hashes are equal, we do a full comparison to protect against collisions
                if ((!newHash.equals(artHash) || !artAndUri.first.sameAs(displayArt))) {
                    artHash = newHash;
                    displayArt = artAndUri.first;
                    artUrl = makeArtUrl(artHash, artAndUri.second);
                    artist = newArtist;
                    album = newAlbum;
                }
            }

        }

        plugin.sendMetadata(player);
    }

    @Override
    public void onAudioInfoChanged(MediaController.PlaybackInfo info) {
        //Note: not called by all media players
        plugin.sendMetadata(player);
    }

    public String getArtUrl() {
        return artUrl;
    }

    /**
     * Get the JPG art of the current track as a bytearray.
     *
     * @return null if no art is available, otherwise a PNG image serialized into a bytearray
     */
    public byte[] getArtAsArray() {
        Bitmap displayArt = this.displayArt;
        if (displayArt == null) {
            return null;
        }
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        displayArt.compress(Bitmap.CompressFormat.JPEG, 90, stream);
        return stream.toByteArray();
    }
}
