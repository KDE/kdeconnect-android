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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;


@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
class MprisReceiverCallback extends MediaController.Callback {

    private static final String TAG = "MprisReceiver";

    private final MprisReceiverPlayer player;
    private final MprisReceiverPlugin plugin;

    MprisReceiverCallback(MprisReceiverPlugin plugin, MprisReceiverPlayer player) {
        this.player = player;
        this.plugin = plugin;
    }

    @Override
    public void onPlaybackStateChanged(PlaybackState state) {
        plugin.sendMetadata(player);
    }

    @Override
    public void onMetadataChanged(@Nullable MediaMetadata metadata) {
        plugin.sendMetadata(player);
    }

    @Override
    public void onAudioInfoChanged(MediaController.PlaybackInfo info) {
        //Note: not called by all media players
        plugin.sendMetadata(player);
    }
}
