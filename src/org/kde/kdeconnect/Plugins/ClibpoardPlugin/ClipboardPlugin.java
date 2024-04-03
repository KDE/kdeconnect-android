/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Plugins.ClibpoardPlugin;


import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect_tp.R;

import java.util.Objects;

@PluginFactory.LoadablePlugin
public class ClipboardPlugin extends Plugin {

    /**
     * Packet containing just clipboard contents, sent when a device updates its clipboard.
     * <p>
     * The body should look like so:
     * {
     * "content": "password"
     * }
     */
    private final static String PACKET_TYPE_CLIPBOARD = "kdeconnect.clipboard";

    /**
     * Packet containing clipboard contents and a timestamp that the contents were last updated, sent
     * on first connection
     * <p>
     * The timestamp is milliseconds since epoch. It can be 0, which indicates that the clipboard
     * update time is currently unknown.
     * <p>
     * The body should look like so:
     * {
     * "timestamp": 542904563213,
     * "content": "password"
     * }
     */
    private final static String PACKET_TYPE_CLIPBOARD_CONNECT = "kdeconnect.clipboard.connect";

    @Override
    public @NonNull String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_clipboard);
    }

    @Override
    public @NonNull String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_clipboard_desc);
    }

    @Override
    public boolean onPacketReceived(@NonNull NetworkPacket np) {
        String content = np.getString("content");
        switch (np.getType()) {
            case (PACKET_TYPE_CLIPBOARD):
                ClipboardListener.instance(context).setText(content);
                return true;
            case(PACKET_TYPE_CLIPBOARD_CONNECT):
                long packetTime = np.getLong("timestamp");
                // If the packetTime is 0, it means the timestamp is unknown (so do nothing).
                if (packetTime == 0 || packetTime < ClipboardListener.instance(context).getUpdateTimestamp()) {
                    return false;
                }

                if (np.has("content")) { // change clipboard if content is in NetworkPacket
                    ClipboardListener.instance(context).setText(content);
                }
                return true;
        }
        throw new UnsupportedOperationException("Unknown packet type: " + np.getType());
    }

    private final ClipboardListener.ClipboardObserver observer = this::propagateClipboard;

    void propagateClipboard(String content) {
        NetworkPacket np = new NetworkPacket(ClipboardPlugin.PACKET_TYPE_CLIPBOARD);
        np.set("content", content);
        getDevice().sendPacket(np);
    }

    private void sendConnectPacket() {
        String content = ClipboardListener.instance(context).getCurrentContent();
        NetworkPacket np = new NetworkPacket(ClipboardPlugin.PACKET_TYPE_CLIPBOARD_CONNECT);
        long timestamp = ClipboardListener.instance(context).getUpdateTimestamp();
        np.set("timestamp", timestamp);
        np.set("content", content);
        getDevice().sendPacket(np);
    }


    @Override
    public boolean onCreate() {
        ClipboardListener.instance(context).registerObserver(observer);
        sendConnectPacket();
        return true;
    }

    @Override
    public void onDestroy() {
        ClipboardListener.instance(context).removeObserver(observer);
    }

    @Override
    public @NonNull String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_CLIPBOARD, PACKET_TYPE_CLIPBOARD_CONNECT};
    }

    @Override
    public @NonNull String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_CLIPBOARD, PACKET_TYPE_CLIPBOARD_CONNECT};
    }

    @Override
    public @NonNull String getActionName() {
        return context.getString(R.string.send_clipboard);
    }

    @Override
    public boolean displayAsButton(Context context) {
        return Build.VERSION.SDK_INT > Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_DENIED;
    }

    @Override
    public @DrawableRes int getIcon() {
        return R.drawable.ic_baseline_content_paste_24;
    }

    @Override
    public void startMainActivity(Activity activity) {
        if (isDeviceInitialized()) {
            ClipboardManager clipboardManager = ContextCompat.getSystemService(this.context,
                    ClipboardManager.class);
            ClipData.Item item;
            if (clipboardManager.hasPrimaryClip()) {
                item = clipboardManager.getPrimaryClip().getItemAt(0);
                String content = item.coerceToText(this.context).toString();
                this.propagateClipboard(content);
                Toast.makeText(this.context, R.string.pref_plugin_clipboard_sent, Toast.LENGTH_SHORT).show();
            }
        }
    }

}
