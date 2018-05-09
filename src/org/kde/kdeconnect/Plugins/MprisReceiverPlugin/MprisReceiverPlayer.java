/*
 * Copyright 2018 Nicolas Fella <nicolas.fella@gmx.de>
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

package org.kde.kdeconnect.Plugins.MprisReceiverPlugin;

import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Build;
import android.support.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class MprisReceiverPlayer {

    private MediaController controller;

    private String name;

    private boolean isPlaying;

    MprisReceiverPlayer(MediaController controller, String name) {

        this.controller = controller;
        this.name = name;

        if (controller.getPlaybackState() != null) {
            isPlaying = controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING;
        }
    }

    boolean isPlaying() {
        return isPlaying;
    }

    void setPlaying(boolean playing) {
        isPlaying = playing;
    }

    boolean isPaused() {
        return !isPlaying;
    }

    void setPaused(boolean paused) {
        isPlaying = !paused;
    }

    void playPause() {
        if (isPlaying) {
            controller.getTransportControls().pause();
        } else {
            controller.getTransportControls().play();
        }
    }

    String getName() {
        return name;
    }

    String getAlbum() {
        if (controller.getMetadata() == null)
            return "";
        String album = controller.getMetadata().getString(MediaMetadata.METADATA_KEY_ALBUM);
        return album != null ? album : "";
    }

    String getArtist() {
        if (controller.getMetadata() == null)
            return "";

        String artist = controller.getMetadata().getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        return artist != null ? artist : "";
    }

    String getTitle() {
        if (controller.getMetadata() == null)
            return "";
        String title = controller.getMetadata().getString(MediaMetadata.METADATA_KEY_TITLE);
        return title != null ? title : "";
    }

    void previous() {
        controller.getTransportControls().skipToPrevious();
    }

    void next() {
        controller.getTransportControls().skipToNext();
    }

    int getVolume() {
        if (controller.getPlaybackInfo() == null)
            return 0;
        return 100 * controller.getPlaybackInfo().getCurrentVolume() / controller.getPlaybackInfo().getMaxVolume();
    }

    long getPosition() {
        if (controller.getPlaybackState() == null)
            return 0;
        return controller.getPlaybackState().getPosition();
    }
}
