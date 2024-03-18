/*
 * SPDX-FileCopyrightText: 2021 Art Pinch <leonardo90690@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.SystemVolumePlugin

import android.media.AudioManager
import androidx.media.VolumeProviderCompat
import org.kde.kdeconnect.Helpers.DEFAULT_MAX_VOLUME
import org.kde.kdeconnect.Helpers.DEFAULT_VOLUME_STEP
import org.kde.kdeconnect.Helpers.calculateNewVolume
import org.kde.kdeconnect.Plugins.SystemVolumePlugin.Sink.UpdateListener
import org.kde.kdeconnect.Plugins.SystemVolumePlugin.SystemVolumePlugin.SinkListener
import kotlin.math.ceil
import kotlin.math.floor

class SystemVolumeProvider private constructor(plugin: SystemVolumePlugin) :
        VolumeProviderCompat(VOLUME_CONTROL_ABSOLUTE, DEFAULT_MAX_VOLUME, 0),
        SinkListener,
        UpdateListener {

    interface ProviderStateListener {
        fun onProviderStateChanged(systemVolumeProvider: SystemVolumeProvider, isActive: Boolean)
    }

    companion object {
        @JvmStatic
        var currentProvider: SystemVolumeProvider? = null
            private set

        @JvmStatic
        fun fromPlugin(systemVolumePlugin: SystemVolumePlugin): SystemVolumeProvider {
            val currentProvider = currentProvider ?: SystemVolumeProvider(systemVolumePlugin)

            currentProvider.update(systemVolumePlugin)

            return currentProvider
        }

        private fun scale(value: Int, maxValue: Int, maxScaled: Int): Int {
            val floatingResult = value * maxScaled / maxValue.toDouble()
            return if (maxScaled > maxValue) {
                ceil(floatingResult).toInt()
            } else {
                floor(floatingResult).toInt()
            }
        }
    }

    private val stateListeners: MutableList<ProviderStateListener>

    private var defaultSink: Sink? = null

    private var systemVolumePlugin: SystemVolumePlugin

    init {
        systemVolumePlugin = plugin
        stateListeners = mutableListOf()
    }

    private fun update(plugin: SystemVolumePlugin) {
        if (plugin === systemVolumePlugin) return

        propagateState(false)
        defaultSink = null
        stopListeningForSinks()
        systemVolumePlugin = plugin
        startListeningForSinks()
    }

    override fun sinksChanged() {
        for (sink in systemVolumePlugin.sinks) {
            sink.addListener(this)
        }

        val newDefaultSink = getDefaultSink(systemVolumePlugin)

        newDefaultSink?.also {
            updateLocalVolume(it)
        }

        if ((newDefaultSink == null) xor (defaultSink == null)) {
            val volumeAdjustSupported = isVolumeAdjustSupported(newDefaultSink)
            propagateState(volumeAdjustSupported)
        }
        defaultSink = newDefaultSink
    }

    override fun updateSink(sink: Sink) {
        if (!sink.isDefault) return
        defaultSink = sink
        updateLocalVolume(sink)
    }

    override fun onAdjustVolume(direction: Int) {
        val step = when (direction) {
            AudioManager.ADJUST_RAISE -> DEFAULT_VOLUME_STEP
            AudioManager.ADJUST_LOWER -> -DEFAULT_VOLUME_STEP
            else -> return
        }
        val newVolume = calculateNewVolume(currentVolume, maxVolume, step)
        onSetVolumeTo(newVolume)
    }

    override fun onSetVolumeTo(volume: Int) {
        updateLocalAndRemoteVolume(defaultSink, volume)
    }

    private fun updateLocalAndRemoteVolume(sink: Sink?, volume: Int) {
        val shouldUpdateRemote = updateLocalVolume(volume)
        if (!shouldUpdateRemote || sink == null) return
        val remoteVolume = scaleFromLocal(volume, sink.maxVolume)
        systemVolumePlugin.sendVolume(sink.name, remoteVolume)
    }

    private fun updateLocalVolume(volume: Int): Boolean {
        if (currentVolume == volume) return false
        currentVolume = volume
        return true
    }

    private fun updateLocalVolume(sink: Sink) {
        val localVolume = scaleToLocal(sink.volume, sink.maxVolume)
        updateLocalVolume(localVolume)
    }

    private fun scaleToLocal(value: Int, maxValue: Int): Int {
        return scale(value, maxValue, maxVolume)
    }

    private fun scaleFromLocal(value: Int, maxScaled: Int): Int {
        return scale(value, maxVolume, maxScaled)
    }

    fun addStateListener(l: ProviderStateListener) {
        if (!stateListeners.contains(l)) {
            stateListeners.add(l)
            l.onProviderStateChanged(this, isVolumeAdjustSupported(defaultSink))
        }
    }

    fun removeStateListener(l: ProviderStateListener) {
        stateListeners.remove(l)
    }

    private fun propagateState(state: Boolean) {
        for (listener in stateListeners) {
            listener.onProviderStateChanged(this, state)
        }
    }

    private fun isVolumeAdjustSupported(sink: Sink?): Boolean {
        return sink != null
    }

    fun startListeningForSinks() {
        systemVolumePlugin.addSinkListener(this)
        systemVolumePlugin.requestSinkList()
    }

    fun release() {
        stopListeningForSinks()
        stateListeners.clear()
        currentProvider = null
    }

    private fun stopListeningForSinks() {
        for (sink in systemVolumePlugin.sinks) {
            sink.removeListener(this)
        }
        systemVolumePlugin.removeSinkListener(this)
    }
}