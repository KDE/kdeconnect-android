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

package org.kde.kdeconnect.Plugins.SystemvolumePlugin;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

@PluginFactory.LoadablePlugin
public class SystemVolumePlugin extends Plugin {

    private final static String PACKET_TYPE_SYSTEMVOLUME = "kdeconnect.systemvolume";
    private final static String PACKET_TYPE_SYSTEMVOLUME_REQUEST = "kdeconnect.systemvolume.request";

    public interface SinkListener {
        void sinksChanged();
    }

    private final ConcurrentHashMap<String, Sink> sinks;
    private final ArrayList<SinkListener> listeners;

    public SystemVolumePlugin() {
        sinks = new ConcurrentHashMap<>();
        listeners = new ArrayList<>();
    }

    @Override
    public String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_systemvolume);
    }

    @Override
    public String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_systemvolume_desc);
    }

    @Override
    public boolean onPacketReceived(NetworkPacket np) {

        if (np.has("sinkList")) {
            sinks.clear();

            try {
                JSONArray sinkArray = np.getJSONArray("sinkList");

                for (int i = 0; i < sinkArray.length(); i++) {
                    JSONObject sinkObj = sinkArray.getJSONObject(i);
                    Sink sink = new Sink(sinkObj);
                    sinks.put(sink.getName(), sink);
                }
            } catch (JSONException e) {
                Log.e("KDEConnect", "Exception", e);
            }

            for (SinkListener l : listeners) {
                l.sinksChanged();
            }

        } else {
            String name = np.getString("name");
            if (sinks.containsKey(name)) {
                if (np.has("volume")) {
                    sinks.get(name).setVolume(np.getInt("volume"));
                }
                if (np.has("muted")) {
                    sinks.get(name).setMute(np.getBoolean("muted"));
                }
            }
        }
        return true;
    }

    void sendVolume(String name, int volume) {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_SYSTEMVOLUME_REQUEST);
        np.set("volume", volume);
        np.set("name", name);
        device.sendPacket(np);
    }

    void sendMute(String name, boolean mute) {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_SYSTEMVOLUME_REQUEST);
        np.set("muted", mute);
        np.set("name", name);
        device.sendPacket(np);
    }

    void requestSinkList() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_SYSTEMVOLUME_REQUEST);
        np.set("requestSinks", true);
        device.sendPacket(np);
    }

    @Override
    public boolean hasMainActivity() {
        return false;
    }

    @Override
    public boolean displayInContextMenu() {
        return false;
    }

    @Override
    public String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_SYSTEMVOLUME};
    }

    @Override
    public String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_SYSTEMVOLUME_REQUEST};
    }

    Collection<Sink> getSinks() {
        return sinks.values();
    }

    void addSinkListener(SinkListener listener) {
        listeners.add(listener);
    }

    void removeSinkListener(SinkListener listener) {
        listeners.remove(listener);
    }

}
