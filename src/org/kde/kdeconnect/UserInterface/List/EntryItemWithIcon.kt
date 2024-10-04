/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.UserInterface.List

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import org.kde.kdeconnect_tp.databinding.ListCardEntryBinding

open class EntryItemWithIcon : ListAdapter.Item {
    protected val title: String
    protected val icon: Drawable

    constructor(title: String, icon: Drawable) {
        this.title = title
        this.icon = icon
    }

    override fun inflateView(layoutInflater: LayoutInflater): View {
        val binding = ListCardEntryBinding.inflate(layoutInflater)

        binding.listItemEntryTitle.text = title
        binding.listItemEntryIcon.setImageDrawable(icon)

        return binding.root
    }
}
