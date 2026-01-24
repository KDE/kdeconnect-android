/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins

import android.content.Context
import android.util.Log
import org.kde.kdeconnect.Device

object PluginFactory {
    annotation class LoadablePlugin  //Annotate plugins with this so PluginFactory finds them

    private var pluginInfo: Map<String, PluginInfo> = mapOf()

    fun initPluginInfo(context: Context) {
        try {
            pluginInfo = com.albertvaka.classindexksp.LoadablePlugin
                .asSequence()
                .map { it.java.getDeclaredConstructor().newInstance() as Plugin }
                .onEach { it.setContext(context, null) }
                .associate { Pair(it.pluginKey, PluginInfo(it)) }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
        Log.i("PluginFactory", "Loaded " + pluginInfo.size + " plugins")
    }

    val availablePlugins: Set<String>
        get() = pluginInfo.keys
    val incomingCapabilities: Set<String>
        get() = pluginInfo.values.flatMap { plugin -> plugin.supportedPacketTypes }.toSet()
    val outgoingCapabilities: Set<String>
        get() = pluginInfo.values.flatMap { plugin -> plugin.outgoingPacketTypes }.toSet()

    @JvmStatic
    fun getPluginInfo(pluginKey: String): PluginInfo = pluginInfo[pluginKey]!!

    @JvmStatic
    fun sortPluginList(plugins: List<String>): List<String> {
        return plugins.sortedBy { pluginInfo[it]?.displayName }
    }

    fun instantiatePluginForDevice(context: Context, pluginKey: String, device: Device): Plugin? {
        try {
            val plugin = pluginInfo[pluginKey]?.instantiableClass?.getDeclaredConstructor()?.newInstance()?.apply { setContext(context, device) }
            return plugin
        } catch (e: Exception) {
            Log.e("PluginFactory", "Could not instantiate plugin: $pluginKey", e)
            return null
        }
    }

    fun pluginsForCapabilities(incoming: Set<String>, outgoing: Set<String>): Set<String> {
        fun hasCommonCapabilities(info: PluginInfo): Boolean =
            outgoing.any { it in info.supportedPacketTypes } ||
            incoming.any { it in info.outgoingPacketTypes }

        val (used, unused) = pluginInfo.entries.partition { hasCommonCapabilities(it.value) }

        for (pluginId in unused.map { it.key }) {
            Log.d("PluginFactory", "Won't load $pluginId because of unmatched capabilities")
        }

        return used.map { it.key }.toSet()
    }

    class PluginInfo private constructor(
        val displayName: String,
        val description: String,
        val isEnabledByDefault: Boolean,
        val hasSettings: Boolean,
        val listenToUnpaired: Boolean,
        supportedPacketTypes: Array<String>,
        outgoingPacketTypes: Array<String>,
        val instantiableClass: Class<out Plugin>,
    ) {
        internal constructor(p: Plugin) : this(p.displayName, p.description,
            p.isEnabledByDefault, p.hasSettings(), p.listensToUnpairedDevices(),
            p.supportedPacketTypes, p.outgoingPacketTypes, p.javaClass)

        val supportedPacketTypes: Set<String> = supportedPacketTypes.toSet()
        val outgoingPacketTypes: Set<String> = outgoingPacketTypes.toSet()
    }
}
