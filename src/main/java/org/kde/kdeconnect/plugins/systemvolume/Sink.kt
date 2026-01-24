/*
 * SPDX-FileCopyrightText: 2018 Nicolas Fella <nicolas.fella@gmx.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins.systemvolume

import org.json.JSONObject

class Sink {
    interface UpdateListener {
        fun updateSink(sink: Sink)
    }

    var volume: Int
        private set
    val description: String
    val name: String
    var mute: Boolean
        private set
    val maxVolume: Int
    private var enabled: Boolean
    private val listeners: MutableList<UpdateListener> = mutableListOf()

    constructor(obj: JSONObject) {
        name = obj.getString("name")
        volume = obj.getInt("volume")
        mute = obj.getBoolean("muted")
        description = obj.getString("description")
        maxVolume = obj.getInt("maxVolume")
        enabled = obj.optBoolean("enabled", false)
    }

    fun setVolume(volume: Int) {
        this.volume = volume
        updateListeners()
    }

    fun isMute(): Boolean {
        return mute
    }

    fun setMute(mute: Boolean) {
        this.mute = mute
        updateListeners()
    }

    var isDefault: Boolean
        get() = enabled
        set(enable) {
            this.enabled = enable
            updateListeners()
        }

    fun addListener(l: UpdateListener) {
        if (!listeners.contains(l)) {
            listeners.add(l)
        }
    }

    fun removeListener(l: UpdateListener) {
        listeners.remove(l)
    }

    private fun updateListeners() {
        for (l in listeners) {
            l.updateSink(this)
        }
    }
}
