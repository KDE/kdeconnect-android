/*
 * SPDX-FileCopyrightText: 2018 Nicolas Fella <nicolas.fella@gmx.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins.systemvolume

import android.util.Log
import androidx.annotation.VisibleForTesting
import org.json.JSONException
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginFactory.LoadablePlugin
import org.kde.kdeconnect_tp.R
import java.util.concurrent.ConcurrentHashMap

@LoadablePlugin
class SystemVolumePlugin : Plugin() {
    interface SinkListener {
        fun sinksChanged()
    }

    private val sinkMap: ConcurrentHashMap<String, Sink> = ConcurrentHashMap()
    private val listeners: MutableList<SinkListener> = mutableListOf()

    override val displayName: String
        get() = context.resources.getString(R.string.pref_plugin_systemvolume)

    override val description: String
        get() = context.resources.getString(R.string.pref_plugin_systemvolume_desc)

    override fun onPacketReceived(np: NetworkPacket): Boolean {
        if ("sinkList" in np) {
            sinkMap.clear()

            try {
                val sinkArray = checkNotNull(np.getJSONArray("sinkList"))
                for (i in 0..< sinkArray.length()) {
                    val sinkObj = sinkArray.getJSONObject(i)
                    val sink = Sink(sinkObj)
                    sinkMap[sink.name] = sink
                }
            } catch (e: JSONException) {
                Log.e("KDEConnect", "Exception", e)
            }

            synchronized(listeners) {
                for (l in listeners) {
                    l.sinksChanged()
                }
            }
        } else {
            val name = np.getString("name")
            val sink = sinkMap[name]
            if (sink != null) {
                if ("volume" in np) {
                    sink.setVolume(np.getInt("volume"))
                }
                if ("muted" in np) {
                    sink.setMute(np.getBoolean("muted"))
                }
                if ("enabled" in np) {
                    sink.isDefault = np.getBoolean("enabled")
                }
            }
        }
        return true
    }

    internal fun sendVolume(name: String, volume: Int) {
        val np = NetworkPacket(PACKET_TYPE_SYSTEMVOLUME_REQUEST)
        np["volume"] = volume
        np["name"] = name
        device.sendPacket(np)
    }

    internal fun sendMute(name: String, mute: Boolean) {
        val np = NetworkPacket(PACKET_TYPE_SYSTEMVOLUME_REQUEST)
        np["muted"] = mute
        np["name"] = name
        device.sendPacket(np)
    }

    internal fun sendEnable(name: String) {
        val np = NetworkPacket(PACKET_TYPE_SYSTEMVOLUME_REQUEST)
        np["enabled"] = true
        np["name"] = name
        device.sendPacket(np)
    }

    override val supportedPacketTypes: Array<String> = arrayOf(PACKET_TYPE_SYSTEMVOLUME)
    override val outgoingPacketTypes: Array<String> = arrayOf(PACKET_TYPE_SYSTEMVOLUME_REQUEST)

    val sinks: MutableCollection<Sink>
        get() = sinkMap.values

    internal fun addSinkListener(listener: SinkListener) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    internal fun removeSinkListener(listener: SinkListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    companion object {
        private const val PACKET_TYPE_SYSTEMVOLUME = "kdeconnect.systemvolume"
        private const val PACKET_TYPE_SYSTEMVOLUME_REQUEST = "kdeconnect.systemvolume.request"
    }
}
