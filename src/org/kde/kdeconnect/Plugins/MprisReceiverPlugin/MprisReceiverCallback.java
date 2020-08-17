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
