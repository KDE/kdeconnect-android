/*
 * SPDX-FileCopyrightText: 2016 Thomas Posch <kdeconnect@online.posch.name>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.runcommand

import android.view.LayoutInflater
import android.view.View
import kotlinx.serialization.Serializable
import org.kde.kdeconnect.ui.list.ListAdapter
import org.kde.kdeconnect_tp.databinding.ListItemEntryBinding

@Serializable
data class CommandInfo(val name: String, val command: String, val key: String)

open class CommandEntry(private val info: CommandInfo) : ListAdapter.Item {

    val key: String
        get() = info.key

    val name: String
     get() = info.name

    val command: String
        get() = info.command

    override fun inflateView(layoutInflater: LayoutInflater): View {
        val binding = ListItemEntryBinding.inflate(layoutInflater)

        binding.listItemEntryTitle.text = info.name

        binding.listItemEntrySummary.visibility = View.VISIBLE
        binding.listItemEntrySummary.text = info.command

        return binding.root
    }
}
