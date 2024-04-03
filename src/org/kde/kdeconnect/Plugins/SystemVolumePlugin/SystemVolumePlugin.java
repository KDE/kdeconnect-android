/*
 * SPDX-FileCopyrightText: 2018 Nicolas Fella <nicolas.fella@gmx.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Plugins.SystemVolumePlugin;

import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
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
    public @NonNull String getDisplayName() {
        return context.getResources().getString(R.string.pref_plugin_systemvolume);
    }

    @Override
    public @NonNull String getDescription() {
        return context.getResources().getString(R.string.pref_plugin_systemvolume_desc);
    }

    @Override
    public boolean onPacketReceived(@NonNull NetworkPacket np) {

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

            synchronized(listeners) {
                for (SinkListener l : listeners) {
                    l.sinksChanged();
                }
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
                if (np.has("enabled")) {
                    sinks.get(name).setDefault(np.getBoolean("enabled"));
                }
            }
        }
        return true;
    }

    void sendVolume(String name, int volume) {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_SYSTEMVOLUME_REQUEST);
        np.set("volume", volume);
        np.set("name", name);
        getDevice().sendPacket(np);
    }

    void sendMute(String name, boolean mute) {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_SYSTEMVOLUME_REQUEST);
        np.set("muted", mute);
        np.set("name", name);
        getDevice().sendPacket(np);
    }

    void sendEnable(String name) {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_SYSTEMVOLUME_REQUEST);
        np.set("enabled", true);
        np.set("name", name);
        getDevice().sendPacket(np);
    }

    void requestSinkList() {
        NetworkPacket np = new NetworkPacket(PACKET_TYPE_SYSTEMVOLUME_REQUEST);
        np.set("requestSinks", true);
        getDevice().sendPacket(np);
    }

    @Override
    public @NonNull String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_SYSTEMVOLUME};
    }

    @Override
    public @NonNull String[] getOutgoingPacketTypes() {
        return new String[]{PACKET_TYPE_SYSTEMVOLUME_REQUEST};
    }

    Collection<Sink> getSinks() {
        return sinks.values();
    }

    void addSinkListener(SinkListener listener) {
        synchronized(listeners) {
            listeners.add(listener);
        }
    }

    void removeSinkListener(SinkListener listener) {
        synchronized(listeners) {
            listeners.remove(listener);
        }
    }

}
