/*
 * Copyright 2014 Albert Vaca Cintora <albertvaka@gmail.com>
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

package org.kde.kdeconnect.Plugins.ClibpoardPlugin;



import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect_tp.R;

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
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_clipboard);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_clipboard_desc);
    }

    @Override
    public boolean onPacketReceived(NetworkPacket np) {
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

                ClipboardListener.instance(context).setText(content);
                return true;
        }
        throw new UnsupportedOperationException("Unknown packet type: " + np.getType());
    }

    private final ClipboardListener.ClipboardObserver observer = this::propagateClipboard;

    void propagateClipboard(String content) {
        NetworkPacket np = new NetworkPacket(ClipboardPlugin.PACKET_TYPE_CLIPBOARD);
        np.set("content", content);
        device.sendPacket(np);
    }

    private void sendConnectPacket() {
        String content = ClipboardListener.instance(context).getCurrentContent();
        NetworkPacket np = new NetworkPacket(ClipboardPlugin.PACKET_TYPE_CLIPBOARD_CONNECT);
        long timestamp = ClipboardListener.instance(context).getUpdateTimestamp();
        np.set("timestamp", timestamp);
        np.set("content", content);
        device.sendPacket(np);
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
    public String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_CLIPBOARD, PACKET_TYPE_CLIPBOARD_CONNECT};
    }

    @Override
    public String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_CLIPBOARD, PACKET_TYPE_CLIPBOARD_CONNECT};
    }


}
