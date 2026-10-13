/*
 * SPDX-FileCopyrightText: 2018 Nicolas Fella <nicolas.fella@gmx.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.systemvolume

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import org.kde.kdeconnect.helpers.JsonHelper.json
import kotlin.math.roundToInt

class Sink {
    @Serializable
    private class SinkInfo(
        val name: String,
        val volume: Double,
        val muted: Boolean,
        val description: String,
        val maxVolume: Double,
        val enabled: Boolean = false,
    )

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

    @Throws(SerializationException::class)
    constructor(obj: JsonElement) {
        val info = json.decodeFromJsonElement<SinkInfo>(obj)
        name = info.name
        volume = info.volume.roundToInt()
        mute = info.muted
        description = info.description
        maxVolume = info.maxVolume.roundToInt()
        enabled = info.enabled
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
