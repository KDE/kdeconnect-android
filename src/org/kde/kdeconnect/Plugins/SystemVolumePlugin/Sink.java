/*
 * SPDX-FileCopyrightText: 2018 Nicolas Fella <nicolas.fella@gmx.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.Plugins.SystemVolumePlugin;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

class Sink {

    interface UpdateListener {
        void updateSink(Sink sink);
    }

    private int volume;
    private String description;
    private String name;
    private boolean mute;
    private int maxVolume;
    private boolean enabled;

    private final List<UpdateListener> listeners;

    Sink(JSONObject obj) throws JSONException {
        listeners = new ArrayList<>();
        name = obj.getString("name");
        volume = obj.getInt("volume");
        mute = obj.getBoolean("muted");
        description = obj.getString("description");
        maxVolume = obj.getInt("maxVolume");
        enabled = obj.optBoolean("enabled", false);
    }

    int getVolume() {
        return volume;
    }


    void setVolume(int volume) {
        this.volume = volume;
        for (UpdateListener l : listeners) {
            l.updateSink(this);
        }
    }

    String getDescription() {
        return description;
    }

    String getName() {
        return name;
    }

    boolean isMute() {
        return mute;
    }

    void setMute(boolean mute) {
        this.mute = mute;
        for (UpdateListener l : listeners) {
            l.updateSink(this);
        }
    }

    boolean isDefault() {
        return enabled;
    }

    void setDefault(boolean enable) {
        this.enabled = enable;
        for (UpdateListener l : listeners) {
            l.updateSink(this);
        }
    }

    void addListener(UpdateListener l) {

        if (!listeners.contains(l)) {
            listeners.add(l);
        }
    }

    int getMaxVolume() {
        return maxVolume;
    }

    void removeListener(UpdateListener l) {
        listeners.remove(l);
    }

}
