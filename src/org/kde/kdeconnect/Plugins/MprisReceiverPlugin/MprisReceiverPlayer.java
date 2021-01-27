/*
 * SPDX-FileCopyrightText: 2018 Nicolas Fella <nicolas.fella@gmx.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.MprisReceiverPlugin;

import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Build;

import androidx.annotation.RequiresApi;

import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.firstNonEmpty;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class MprisReceiverPlayer {

    private final MediaController controller;

    private final String name;

    MprisReceiverPlayer(MediaController controller, String name) {
        this.controller = controller;
        this.name = name;
    }

    boolean isPlaying() {
        PlaybackState state = controller.getPlaybackState();
        if (state == null) return false;

        return state.getState() == PlaybackState.STATE_PLAYING;
    }

    boolean canPlay() {
        PlaybackState state = controller.getPlaybackState();
        if (state == null) return false;

        if (state.getState() == PlaybackState.STATE_PLAYING) return true;

        return (state.getActions() & (PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PLAY_PAUSE)) != 0;
    }

    boolean canPause() {
        PlaybackState state = controller.getPlaybackState();
        if (state == null) return false;

        if (state.getState() == PlaybackState.STATE_PAUSED) return true;

        return (state.getActions() & (PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE)) != 0;
    }

    boolean canGoPrevious() {
        PlaybackState state = controller.getPlaybackState();
        if (state == null) return false;

        return (state.getActions() & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0;
    }

    boolean canGoNext() {
        PlaybackState state = controller.getPlaybackState();
        if (state == null) return false;

        return (state.getActions() & PlaybackState.ACTION_SKIP_TO_NEXT) != 0;
    }

    boolean canSeek() {
        PlaybackState state = controller.getPlaybackState();
        if (state == null) return false;

        return (state.getActions() & PlaybackState.ACTION_SEEK_TO) != 0;
    }

    void playPause() {
        if (isPlaying()) {
            controller.getTransportControls().pause();
        } else {
            controller.getTransportControls().play();
        }
    }

    String getName() {
        return name;
    }

    String getAlbum() {
        MediaMetadata metadata = controller.getMetadata();
        if (metadata == null) return "";

        return defaultString(metadata.getString(MediaMetadata.METADATA_KEY_ALBUM));
    }

    String getArtist() {
        MediaMetadata metadata = controller.getMetadata();
        if (metadata == null) return "";

        return defaultString(firstNonEmpty(metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
                metadata.getString(MediaMetadata.METADATA_KEY_AUTHOR),
                metadata.getString(MediaMetadata.METADATA_KEY_WRITER)));
    }

    String getTitle() {
        MediaMetadata metadata = controller.getMetadata();
        if (metadata == null) return "";

        return defaultString(firstNonEmpty(metadata.getString(MediaMetadata.METADATA_KEY_TITLE),
                metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)));
    }

    void previous() {
        controller.getTransportControls().skipToPrevious();
    }

    void next() {
        controller.getTransportControls().skipToNext();
    }

    void play() {
        controller.getTransportControls().play();
    }

    void pause() {
        controller.getTransportControls().pause();
    }

    void stop() {
        controller.getTransportControls().stop();
    }

    int getVolume() {
        MediaController.PlaybackInfo info = controller.getPlaybackInfo();
        if (info == null) return 0;
        return 100 * info.getCurrentVolume() / info.getMaxVolume();
    }

    void setVolume(int volume) {
        MediaController.PlaybackInfo info = controller.getPlaybackInfo();
        if (info == null) return;

        //Use rounding for the volume, since most devices don't have a very large range
        double unroundedVolume = info.getMaxVolume() * volume / 100.0 + 0.5;
        controller.setVolumeTo((int) unroundedVolume, 0);
    }

    long getPosition() {
        PlaybackState state = controller.getPlaybackState();
        if (state == null) return 0;

        return state.getPosition();
    }

    void setPosition(long position) {
        controller.getTransportControls().seekTo(position);
    }

    long getLength() {
        MediaMetadata metadata = controller.getMetadata();
        if (metadata == null) return 0;

        return metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
    }
}
