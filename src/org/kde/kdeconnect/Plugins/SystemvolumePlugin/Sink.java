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

    private final List<UpdateListener> listeners;

    Sink(JSONObject obj) throws JSONException {
        listeners = new ArrayList<>();
        name = obj.getString("name");
        volume = obj.getInt("volume");
        mute = obj.getBoolean("muted");
        description = obj.getString("description");
        maxVolume = obj.getInt("maxVolume");
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
